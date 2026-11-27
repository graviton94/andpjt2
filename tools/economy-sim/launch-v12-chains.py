"""Measure falling chains with and without wave steps using launch-v1 audit schema 2."""
import argparse
import csv
from collections import Counter, defaultdict
from pathlib import Path
from statistics import median

BANDS = ((0, 0, "0"), (1, 1, "1"), (2, 3, "2–3"), (4, 5, "4–5"), (6, 8, "6–8"), (9, 10_000, "9+"))
REASONS = ("natural", "spam", "wave", "cap")


def table(directory, profile, policy):
    with (directory / "seeds.csv").open(encoding="utf-8") as file:
        horizon = {row["seed"]: int(row["days"]) for row in csv.DictReader(file)
                   if (row["profile"], row["input"]) == (profile, policy)}
    counts = defaultdict(Counter)
    with (directory / "chain-paths.csv").open(encoding="utf-8") as file:
        for row in csv.DictReader(file):
            if (row["profile"], row["input"]) != (profile, policy):
                continue
            seed, period, actor, reason = (row[key] for key in ("seed", "period", "actor", "end_reason"))
            physical, bonus, waves, displayed, swipes, steps = (
                int(row[key]) for key in ("physical_bursts", "match_bonus", "waves", "scoring_chain", "swipes", "processing_steps")
            )
            if displayed != physical + bonus + waves:
                raise ValueError(f"Unexplained scoring chain in seed {seed}")
            if physical and (reason not in REASONS or (reason == "wave") != bool(waves) or steps < physical):
                raise ValueError(f"Unexplained chain end in seed {seed}")
            c = counts[period, seed]
            c["swipes"] += swipes
            c["automatic"] += swipes * (actor == "Auto")
            for label, length in (("without_wave", physical + bonus), ("with_wave", displayed)):
                band = next(name for lo, hi, name in BANDS if lo <= length <= hi)
                c[label, band] += swipes
            c["direct3"] += swipes * (physical >= 3)
            c["prewave5"] += swipes * (physical + bonus >= 5)
            c["prewave9"] += swipes * (physical + bonus >= 9)
            c["wave9"] += swipes * (displayed >= 9 > physical + bonus)
            c["steps_total"] += swipes * steps
            c["steps_max"] = max(c["steps_max"], steps)
            c["steps_with_wave_total"] += swipes * (steps + waves)
            c["steps_with_wave_max"] = max(c["steps_with_wave_max"], steps + waves)
            if reason:
                c["chains"] += swipes
                c[reason] += swipes
    print("| 시점·연쇄 길이 | 파동 제외 비율 중앙 | 파동 포함 비율 중앙 |")
    print("|---|---:|---:|")
    for period in ("days1_14", "day15_on"):
        group = [c for (stage, _), c in counts.items() if stage == period]
        if not group:
            continue
        for _, _, band in BANDS:
            percentages = [median(100 * c[key, band] / c["swipes"] for c in group)
                           for key in ("without_wave", "with_wave")]
            print(f"| {period} · {band} | {percentages[0]:.2f}% | {percentages[1]:.2f}% |")
    print()
    print("| 시점 | 물리 3+ 중앙 (최소~최대) | 파동 제외 5+ /세션 · /일 | 파동 제외 9+ /세션 · /일 | 파동이 올린 9+ /일 |")
    print("|---|---:|---:|---:|---:|")
    for period in ("days1_14", "day15_on"):
        group = [(c, min(14, horizon[seed]) if period == "days1_14" else horizon[seed] - 14)
                 for (stage, seed), c in counts.items() if stage == period]
        if not group:
            continue
        direct = [100 * c["direct3"] / c["swipes"] for c, _ in group]
        rates = {key: median(c[key] / days for c, days in group) for key in ("prewave5", "prewave9", "wave9")}
        print(f"| {period} | {median(direct):.2f}% ({min(direct):.2f}~{max(direct):.2f}%) | "
              f"{rates['prewave5']/4:.2f} · {rates['prewave5']:.2f} | "
              f"{rates['prewave9']/4:.2f} · {rates['prewave9']:.2f} | {rates['wave9']:.2f} |")
    print()
    total = sum(counts.values(), start=Counter())
    print("종료 사유 " + " · ".join(
        f"{reason} {total[reason]:,}회 ({100 * total[reason] / total['chains']:.3f}%)" for reason in REASONS
    ))
    print(f"처리 단계 평균 {total['steps_total']/total['swipes']:.2f} (연쇄 시작 스와이프 {total['steps_total']/total['chains']:.2f}), "
          f"최대 {max(c['steps_max'] for c in counts.values())}. "
          f"파동 포함 평균 {total['steps_with_wave_total']/total['swipes']:.2f}, "
          f"최대 {max(c['steps_with_wave_max'] for c in counts.values())}. "
          f"자동 {total['automatic']:,}/{total['swipes']:,}회 ({100 * total['automatic']/total['swipes']:.2f}%).")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("--profile", default="Free")
    parser.add_argument("--policy", default="Best")
    args = parser.parse_args()
    table(args.directory, args.profile, args.policy)
