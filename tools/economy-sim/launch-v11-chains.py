"""Summarize the visible chain color bands and the actual burst paths in launch-v1 output."""
import argparse
import csv
from collections import defaultdict
from pathlib import Path
from statistics import median

BANDS = ((2, 3, "2–3"), (4, 5, "4–5"), (6, 8, "6–8"), (9, float("inf"), "9+"))
PERIOD_DAYS = {"days1_14": (1, 14), "day15_on": (15, float("inf"))}


def read_chain_paths(path, profile, mode):
    with path.open(encoding="utf-8") as file:
        for row in csv.DictReader(file):
            if (row["profile"], row["input"]) != (profile, mode):
                continue
            yield row["seed"], row["period"], *(
                int(row[key]) for key in ("physical_bursts", "match_bonus", "waves", "scoring_chain", "swipes")
            )


def summarize(directory, profile, mode):
    with (directory / "seeds.csv").open(encoding="utf-8") as file:
        days_by_seed = {
            row["seed"]: int(row["days"])
            for row in csv.DictReader(file)
            if (row["profile"], row["input"]) == (profile, mode)
        }
    counts = defaultdict(lambda: defaultdict(int))
    for seed, period, bursts, match, waves, chain, swipes in read_chain_paths(directory / "chain-paths.csv", profile, mode):
        if min(bursts, match, waves) < 0 or chain != bursts + match + waves:
            raise ValueError(f"Unexplained visual chain at seed {seed}, {period}")
        totals = counts[period, seed]
        totals["swipes"] += swipes
        for threshold in (3, 4):
            totals[f"물리 {threshold}+"] += swipes * (bursts >= threshold)
        for start, end, label in BANDS:
            if start <= chain <= end:
                totals[label] += swipes
                route = "burst" if bursts >= start else "match" if bursts + match >= start else "wave"
                totals[label, route] += swipes

    print(f"| {profile}/{mode} | 일자 | 스와이프 비율 중앙값 (최소~최대) | 하루 출현 중앙값 | 폭발 / 합체 / 파동 횟수 합계 |")
    print("|---|---|---:|---:|---:|")
    for period, (start, end) in PERIOD_DAYS.items():
        cohort = [
            (row, min(days_by_seed[seed], end) - start + 1)
            for (stage, seed), row in counts.items() if stage == period
        ]
        if not cohort:
            continue
        dates = f"{start}~{int(max(span for _, span in cohort)) + start - 1}일"
        for label in tuple(label for _, _, label in BANDS) + ("물리 3+", "물리 4+"):
            shares = [100 * row[label] / row["swipes"] for row, _ in cohort]
            per_day = median(row[label] / span for row, span in cohort)
            routes = " / ".join(f"{sum(row[label, route] for row, _ in cohort):,}"
                                for route in ("burst", "match", "wave")) if "물리" not in label else "—"
            print(f"| {label} | {dates} | {median(shares):.2f}% ({min(shares):.2f}~{max(shares):.2f}%) | {per_day:.2f} | {routes} |")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("--profile", default="Free")
    parser.add_argument("--mode", default="Best")
    args = parser.parse_args()
    summarize(args.directory, args.profile, args.mode)
