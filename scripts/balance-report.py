#!/usr/bin/env python3
"""Summarize the application-engine CSVs and preserve failing seeds."""
import csv
import math
import sys
from collections import defaultdict
from pathlib import Path
from statistics import median

HOUR = 3_600_000
DAY = 24 * HOUR


def load(path):
    with path.open(newline="") as source:
        return list(csv.DictReader(source))


def q(values, probability):
    ordered = sorted(values)
    return ordered[math.ceil(len(ordered) * probability) - 1] if ordered else None


def stamp(value):
    if value is None:
        return "—"
    return f"{value / 1000:.1f}초" if value < HOUR else f"{value / HOUR:.2f}시간"


def number(row, field):
    return int(row[field]) if row[field] else None


def cohort(rows, **fields):
    return [row for row in rows if all(row[key] == str(value) for key, value in fields.items())]


def save(path, header, rows):
    with path.open("w", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(header)
        writer.writerows(rows)


def main(folder):
    raw = folder / "raw"
    growth = load(raw / "growth.csv")
    apps = load(raw / "apps.csv")
    assert growth and apps
    commerce = folder / "commerce"
    if (commerce / "growth.csv").is_file():
        growth = [r for r in growth if r["mode"] != "calendar"] + load(commerce / "growth.csv")
        apps = [r for r in apps if r["mode"] != "calendar"] + load(commerce / "apps.csv")
    comparison = folder / "comparison"
    if (comparison / "growth.csv").is_file():
        growth += load(comparison / "growth.csv")
        apps += load(comparison / "apps.csv")
    failures = []
    gates = []

    def gate(name, rows, passes, target):
        failing = [row for row in rows if not passes(row)]
        actual = len(rows) - len(failing)
        ok = bool(rows) and 100 * actual >= target * len(rows)
        gates.append((name, actual, len(rows), target, ok))
        if not ok:
            failures.extend((name, r["mode"], r["policy"], r["seed"], r["arm"]) for r in failing)

    gate("기본 P 원장", growth, lambda r: int(r["p_balance"]) == int(r["p_swipes"]) +
         int(r["p_attendance"]) - int(r["p_spent"]) - int(r["p_reset"]), 100)
    gate("개인 점수 +8% 상한", growth, lambda r: 100 * int(r["personal_best"]) <= 108 * int(r["base_best"]), 100)
    gate("유료재화 원장", growth, lambda r: 0 <= int(r["credits_left"]) <= int(r["credits_granted"])
         and (int(r["credits_granted"]) - int(r["credits_left"])) % 100 == 0, 100)
    gate("앱 최초 등장 · 관측된 일반 보충 3슬롯", apps, lambda r: int(r["late"]) == 0, 100)
    if "energy_charged" in growth[0]:
        gate("에너지 원장", growth, lambda r: 12 + int(r["energy_charged"]) +
             int(r["energy_refilled"]) - int(r["swipes"]) == int(r["energy_balance"]), 100)
        gate("기본 P·토큰·모형 비교 보너스 분해", growth,
             lambda r: int(r["p_base"]) + int(r["p_tokens"]) + int(r.get("p_model_bm", "0")) == int(r["p_swipes"]), 100)

    lines = ["# 알림 청소부 앱 엔진 밸런스 보고서", "",
             f"앱과 동일한 Kotlin 판정으로 성장 체크포인트 {len(growth):,}행, 앱별 관측 {len(apps):,}행을 생성했다.",
             "", "## 성장과 환생", "",
             "| 일정 / 정책 | 첫 지출 p50 | 첫 환생 p50 | 최고점 복구 p50 | 300h 또는 59일의 활성 강화 공백 p90 |",
             "|---|---:|---:|---:|---:|"]
    for mode, horizon in (("regular", 300 * HOUR), ("calendar", 59 * DAY)):
        for policy in ("auto", "random"):
            rows = cohort(growth, mode=mode, elapsed_ms=horizon, arm="free", policy=policy)
            fields = [stamp(q([int(r[k]) for r in rows if r[k]], .5))
                      for k in ("first_spend_ms", "first_prestige_ms", "first_recovery_ms")]
            gap = stamp(q([int(r["max_upgrade_gap_ms"]) for r in rows], .9))
            lines.append(f"| {mode}/{policy} | " + " | ".join(fields) + f" | {gap} |")
            gate(f"{mode}/{policy} 첫 지출 40~90초", rows,
                 lambda r: bool(r["first_spend_ms"]) and 40000 <= int(r["first_spend_ms"]) <= 90000, 80)
            gate(f"{mode}/{policy} 첫 5분 과부하 3회", rows, lambda r: int(r["first_5min_overloads"]) >= 3, 90)
            gate(f"{mode}/{policy} 첫 환생 복구", rows, lambda r: bool(r["first_recovery_ms"] and r["first_prestige_ms"])
                 and int(r["first_recovery_ms"]) <= 2 * int(r["first_prestige_ms"]), 80)

    lines += ["", "## 앱별·티어 도달", "",
              "`app-tiers.csv`는 정책·시나리오·경과 시점별 앱 8종을 포함한다. p50/p90은 전체 배정 실행에서 해당 비율만큼 도달했을 때만 채운다.",
              "1,000 P당 점수는 실제 강화 직전 보드와 입력을 재생해 해당 앱 기여점수 변화량을 관측한 강화만 사용한다. 비발동 증분은 자료 없음으로 둔다."]
    grouped = defaultdict(list)
    for row in apps:
        grouped[tuple(row[key] for key in ("mode", "policy", "arm", "elapsed_ms", "app"))].append(row)
    tier_rows = []
    for key, rows in sorted(grouped.items()):
        for field in ("unlock_ms", "affordable_ms", "buy_ms", "seen_ms", "tier1_ms", "tier3_ms", "tier5_ms", "tier8_ms", "tier12_ms"):
            observed = sorted(int(r[field]) for r in rows if r[field])
            n = len(rows)
            i50, i90 = math.ceil(n * .5) - 1, math.ceil(n * .9) - 1
            measured = sum(int(r["measured_p"]) for r in rows)
            delta = sum(int(r["score_delta"]) for r in rows)
            tier_rows.append((*key, field, len(observed), n, observed[i50] if len(observed) > i50 else "",
                              observed[i90] if len(observed) > i90 else "", measured,
                              sum(int(r["upgrade_p"]) for r in rows),
                              delta * 1000 // measured if measured else ""))
    save(folder / "app-tiers.csv", ("mode", "policy", "arm", "elapsed_ms", "app", "milestone", "reached", "total",
         "p50_ms", "p90_ms", "matched_upgrade_p", "all_upgrade_p", "score_per_1000p_matched"), tier_rows)

    lines += ["", "## 상품별 검증용 효율", "",
              "같은 시드의 무과금 개인 최고점을 분모로 비교했다. 표는 59일 결과이며 CSV에 첫 달도 들어 있다.",
              "", "| 정책 / 상품 | 지출 | 개인 최고점 변화 p50 | 1,000원당 변화 | 광고 완료 p50 | 유료재화 잔액 p50 |",
              "|---|---:|---:|---:|---:|---:|"]
    skus = []
    for horizon in (31 * DAY, 59 * DAY):
        for policy in ("auto", "random"):
            group = cohort(growth, mode="calendar", policy=policy, elapsed_ms=horizon)
            anchors = {r["seed"]: r for r in cohort(group, arm="free")}
            for arm in sorted(set(r["arm"] for r in group) - {"free"}):
                rows = cohort(group, arm=arm)
                assert {r["seed"] for r in rows} == set(anchors)
                uplifts = []
                per_minute = []
                for row in rows:
                    anchor = anchors[row["seed"]]
                    base = int(anchor["personal_best"])
                    uplift = 10000 * (int(row["personal_best"]) - base) // base
                    uplifts.append(uplift)
                    views = int(row["ad_views"])
                    if views:
                        per_minute.append(2 * uplift / views)
                won = int(rows[0]["won"])
                if horizon == 59 * DAY and not arm.startswith("compare-"):
                    if won:
                        fields = ("swipes", "level", "xp", "p_balance", "p_swipes", "p_attendance",
                                  "p_spent", "p_reset", "base_best", "first_prestige_ms", "first_recovery_ms")
                        gate(f"{policy}/{arm} 상품 보너스와 기본 경제 분리", rows,
                             lambda r: all(r[k] == anchors[r["seed"]][k] for k in fields), 100)
                    else:
                        for metric in ("level_30_ms", "level_60_ms"):
                            def bounds(r):
                                free = anchors[r["seed"]][metric]
                                value = r[metric]
                                return bool(free and value) and 5 * int(value) >= 4 * int(free) and 4 * int(value) <= 5 * int(free)
                            gate(f"{policy}/{arm} {metric} 시간 변화 -20%~+25%", rows, bounds, 90)
                score = median(uplifts) / 100
                per_won = score * 1000 / won if won else None
                avg_views = median(int(r["ad_views"]) for r in rows)
                balance = median(int(r["credits_left"]) for r in rows)
                speed = []
                for metric in ("level_30_ms", "level_60_ms"):
                    values = [(int(r[metric]) - int(anchors[r["seed"]][metric])) * 10000 //
                              int(anchors[r["seed"]][metric]) for r in rows
                              if r[metric] and anchors[r["seed"]][metric] and int(anchors[r["seed"]][metric])]
                    speed.append(median(values) / 100 if values else "")
                skus.append((horizon, policy, arm, won, score, per_won, avg_views,
                             median(per_minute) / 100 if per_minute else "", balance,
                             median(int(r["boosts_used"]) for r in rows), q(uplifts, .9) / 100, *speed,
                             *(median(int(r.get(field, "0")) for r in rows)
                               for field in ("mastery_hits", "boost_hits", "special_hits", "decor_hits"))))
                if horizon == 59 * DAY:
                    money = f"{per_won:+.3f}%p" if per_won is not None else "—"
                    lines.append(f"| {policy}/{arm} | {won:,}원 | {score:+.2f}% | {money} | {avg_views:g} | {balance:g} |")
    save(folder / "sku-efficiency.csv", ("elapsed_ms", "policy", "arm", "test_won", "median_best_pct",
         "best_pct_per_1000won", "ad_views_median", "best_pct_per_ad_minute", "credits_left_median", "boosts_used_median",
         "best_pct_p90", "level30_time_change_pct", "level60_time_change_pct",
         "mastery_hits_median", "boost_hits_median", "special_hits_median", "decor_hits_median"), skus)

    mastery_audit = folder / "mastery-audit" / "mastery.csv"
    if mastery_audit.is_file():
        audit = load(mastery_audit)
        by_app = defaultdict(list)
        for r in audit:
            by_app[r["elapsed_ms"], r["policy"], r["app"]].append(r)
        table = []
        for (time, policy, app), rows in sorted(by_app.items()):
            values = [(int(r["mastery_best"]) - int(r["free_best"])) * 10000 // int(r["free_best"]) for r in rows]
            worth = int(rows[0]["test_won"])
            u = median(values) / 100
            table.append((time, policy, app, worth, u, u * 1000 / worth, q(values, .9) / 100,
                          median(int(r["hits"]) for r in rows), sum(n > 0 for n in values), len(values)))
        save(folder / "mastery-apps.csv", ("elapsed_ms", "policy", "app", "won", "best_pct_p50", "best_pct_per_1000won",
             "best_pct_p90", "activation_p50", "improved", "total"), table)
        lines += ["", "## 앱 8종 마스터리 감사", "",
                  "`mastery-apps.csv`에 모든 앱의 가산 발동 수와 개인 기록 변화가 들어 있다. 동일한 스와이프를 유지하고,"
                  " 첫 보유 뒤 무료 마스터리 가산을 해당 앱의 영구 마스터리 가산으로 교체했다. 무료 재고의 대체 소비나 선택 변화는 반영하지 않았다."]
    lines += ["", "## 상품 채택 판단", "",
              "- 최초 보유한 소모품을 적격 기회에 바로 쓰는 정책에서 증폭기 충전과 체험권의 장기 최고점 증가 중앙값이 0%였다. 발동 가산은 지급됐지만 해당 기록이 59일 최고점으로 남지 않았다. 59일 기록 개선을 이 SKU의 판매 근거로 채택하지 않는다.",
              "- 원화 직접 구매의 점수 비교와 패스·이벤트·편의에 대한 선호는 별개다. 테마 가격은 기록 가산의 검증용 기준이며 패스의 실제 유료 수요는 사용자 실험에서 확인한다.",
              "- 기존 P형 BM 두 행은 판매 가격이 없는 비교 모델이다. 표의 0원은 미판매라는 뜻이며 과금 효율을 계산하지 않는다."]
    if "energy_charged" in growth[0]:
        lines += ["", "## 에너지와 환생 시점 대안", "",
                  "| 정책 / 전략 | 300시간 에너지 충전 p50 | 입력 대기 p50 | 환생 횟수 p50 | 토큰 p50 |",
                  "|---|---:|---:|---:|---:|"]
        reference = cohort(growth, mode="regular", elapsed_ms=300 * HOUR)
        for policy, arm in (("auto", "free"), ("auto", "late"), ("random", "free"), ("random", "late"), ("random", "fast")):
            rows = cohort(reference, arm=arm, policy=policy)
            if not rows:
                continue
            m = lambda f: median(int(r[f]) for r in rows)
            lines.append(f"| {policy}/{arm} | {m('energy_charged'):g} | {stamp(m('energy_wait_ms'))} | {m('prestiges'):g} | {m('tokens'):g} |")
    lines += ["", "## 통과 기준", "", "| 기준 | 통과 | 요구 | 결과 |", "|---|---:|---:|---|"]
    lines += [f"| {name} | {actual}/{total} | {target}% | {'통과' if ok else '실패'} |"
              for name, actual, total, target, ok in gates]
    rejected = sum(not gate[-1] for gate in gates)
    lines += ["", f"**{len(gates)-rejected}/{len(gates)} 기준 통과**", "",
              "구매율·광고 수락률·실매출은 측정 범위 밖이다. 광고 조건은 자격이 있을 때 수락하는 스트레스 정책이며, 상품 효율은 표기 가격을 사용한 수치 비교다.",
              "앱의 상품 흐름은 디버그용 거래로 검증한다. 시뮬레이터는 전략으로 환생을 실행하고, 앱에서는 플레이어가 환생을 확정한다."]
    save(folder / "failures.csv", ("gate", "mode", "policy", "seed", "arm"), failures)
    (folder / "REPORT.md").write_text("\n".join(lines) + "\n")
    return int(bool(rejected))


if __name__ == "__main__":
    raise SystemExit(main(Path(sys.argv[1])))
