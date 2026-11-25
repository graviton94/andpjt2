"""Compare integrated and reference baselines and summarize the extended playthroughs."""
import argparse
import csv
from pathlib import Path
from statistics import fmean, median


LIMITS = {
    "Free": (66, 84), "Ads": (49, 63), "Pass": (35, 49),
    "Spend30": (24, 32), "Spend100": (11, 17), "PassVisible": (35, 49),
}


def table(path):
    with path.open() as stream:
        return list(csv.DictReader(stream))


def write(path, fields, rows):
    with path.open("w") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(fields)
        writer.writerows(rows)


def baseline(path):
    result = {}
    for row in table(path):
        if row["scenario"] == "none":
            key = (row["profile"], int(row["seed"]))
            if key in result:
                raise ValueError(f"Duplicate baseline {key} in {path}")
            result[key] = float(row["lv124_day"]) if row["lv124_day"] else None
    return result


def counts(remaining, low, high):
    center = median(x if x is not None else float("inf") for x in remaining)
    early = sum(x is not None and x < low for x in remaining)
    inside = sum(x is not None and low <= x <= high for x in remaining)
    late = sum(x is not None and x > high for x in remaining)
    reached = early + inside + late
    return center, early, inside, late, len(remaining) - reached


def drifts(output, track, original, integrated, guarded=None):
    compared = integrated if guarded is None else guarded
    if not compared.keys() <= original.keys() or not compared.keys() <= integrated.keys():
        raise ValueError("Comparison lacks matching reference seeds")
    names = [("reference", original), ("integrated", integrated)]
    if guarded is not None:
        names.append(("switch_at_lv124", guarded))
    rows = []
    for p, (low, high) in LIMITS.items():
        seeds = sorted(seed for profile, seed in compared if profile == p)
        if not seeds:
            continue
        orig = [original[p, seed] for seed in seeds]
        for model, dataset in names:
            new = [dataset[p, seed] for seed in seeds]
            gains = [(84 if a is None else a) - (84 if b is None else b) for a, b in zip(orig, new)]
            rows.append((p, model, len(seeds), low, high, *counts(new, low, high), fmean(gains)))
    write(output / f"{track}.csv", ("profile", "model", "seeds", "low", "high", "median_days", "early", "in_band",
                                    "late", "not_reached_by_day84", "mean_earlier_than_reference_days"), rows)


def long_summary(output, data):
    fields = (
        "peak_level", "files_paid_total", "gb_paid_total", "backup_value", "backup_offline", "paid_syncs",
        "sync_value", "compression", "cloud_offline", "memo_level_first_20_swipes", "run_level_first_20_swipes",
    )
    groups = {}
    for row in table(data):
        key = row["profile"], int(row["day"])
        groups.setdefault(key, []).append(row)
    write(output / "growth-median.csv", ("profile", "day", "seeds", *fields),
          ((p, day, len(rows), *(median(int(r[name]) for r in rows) for name in fields))
           for (p, day), rows in sorted(groups.items())))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path, help="Original summary.csv")
    parser.add_argument("integrated", type=Path, help="Integrated summary.csv")
    parser.add_argument("output", type=Path)
    parser.add_argument("--opening", type=Path, help="Opening-control summary.csv")
    parser.add_argument("--long", type=Path, help="Long-run growth.csv")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    previous = baseline(args.reference)
    revised = baseline(args.integrated)
    drifts(args.output, "baseline-drift", previous, revised)
    if args.opening:
        drifts(args.output, "opening-control", previous, revised, baseline(args.opening))
    if args.long:
        long_summary(args.output, args.long)


if __name__ == "__main__":
    main()
