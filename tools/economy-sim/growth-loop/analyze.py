"""Audit wait times, growth tails, purchase effects, and subscriptions from simulator CSVs."""
import argparse
from collections import defaultdict
import csv
import gzip
import math
from pathlib import Path
from statistics import fmean, median

DAYS = (7, 14, 28, 56)
STAGES = ("none", "one", "spread_three", "one_set", "two_sets", "full")
METRICS = ("digits_p", "digits_files", "digits_gb", "peak_level_diff")


def rows(folder, name):
    path = folder / (name + ".csv")
    with (path.open() if path.exists() else gzip.open(str(path) + ".gz", "rt")) as file:
        yield from csv.DictReader(file)


def table(out, name, columns, data):
    with (out / (name + ".csv")).open("w") as file:
        csv_writer = csv.writer(file, lineterminator="\n")
        csv_writer.writerow(columns)
        csv_writer.writerows(data)


def core_thresholds(out, probes):
    metrics = ("log_total_p", "log_total_files", "log_total_gb", "peak_lv")
    summary = []
    for path in probes:
        with path.open() as handle:
            records = list(csv.DictReader(handle))
        baseline = {(r["profile"],r["seed"],r["day"]):r for r in records if r["sku"] == "0"}
        grouped = defaultdict(list)
        for r in records:
            if r["sku"] == "0":
                continue
            base = baseline[r["profile"],r["seed"],r["day"]]
            grouped[r["profile"],r["policy"],r["model"],r["sku"],int(r["day"])].append(
                tuple(float(r[k]) - float(base[k]) for k in metrics) + (int(r["skipped_prestige"]),))
        for key, values in sorted(grouped.items()):
            goal = .5 if key[3] == "3" else 1.0
            means = [fmean(item[i] for item in values) for i in range(4)]
            summary.append((path.stem,*key,len(values),goal,*means,means[0]>=goal,
                            means[0]>=goal and means[1]>0 and means[2]>0, sum(item[4] for item in values)))
    table(out, "core-thresholds", ("probe", "profile", "policy", "model", "sku", "day", "seeds", "goal_digits_p",
           "mean_digits_p", "mean_digits_files", "mean_digits_gb", "mean_peak_lv", "p_pass", "p_files_gb_pass",
           "cumulative_skipped_schedules"), summary)


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("raw", type=Path)
    cli.add_argument("out", type=Path)
    cli.add_argument("--core", type=Path, nargs="*", default=[])
    args = cli.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    keys = set()
    stages = defaultdict(list)
    for row in rows(args.raw, "paired"):
        p, policy, model, stage = (row[k] for k in ("profile", "policy", "model", "stage"))
        day, seed = int(row["day"]), int(row["seed"])
        values = tuple(float(row[m]) for m in METRICS)
        stages[p, policy, model, stage, day].append((seed, values))
        if policy == "natural" and model != "none":
            keys.add((p, model, day))
    verdict = []
    monotone = []
    for p, model, day in sorted(keys):
        for mode in ("natural", "fixed"):
            for stage, goal in (("one_set", .5), ("full", 1.0)):
                values = [v for _, v in stages[p, mode, model, stage, day]]
                stats = [fmean(v[i] for v in values) for i in range(len(METRICS))]
                verdict.append((p, mode, model, stage, day, len(values), goal, *stats,
                                sum(v[0] >= goal for v in values), stats[0] >= goal and stats[1] > 0 and stats[2] > 0))
        for previous, stage in zip(STAGES, STAGES[1:]):
            before = sorted(stages[p, "natural", "none" if previous == "none" else model, previous, day])
            after = sorted(stages[p, "natural", model, stage, day])
            for i, metric in enumerate(METRICS):
                differences = []
                for (old_seed, u), (new_seed, v) in zip(before, after, strict=True):
                    if old_seed != new_seed:
                        raise ValueError("Unpaired seeds in the purchase comparison")
                    differences.append(v[i] - u[i])
                monotone.append((p, model, previous, stage, day, metric, fmean(differences), median(differences),
                                 sum(d < -1e-9 for d in differences)))
    table(args.out, "thresholds", ("profile", "policy", "model", "stage", "day", "seeds", "goal_digits_p",
          *(f"mean_{x}" for x in METRICS), "p_seeds_at_goal", "p_files_gb_pass"), verdict)
    table(args.out, "monotonicity", ("profile", "model", "from", "to", "day", "metric", "mean_gain", "median_gain",
          "negative_seeds"), monotone)

    steps = defaultdict(lambda: [0, 0, 0, []])
    for r in rows(args.raw, "waiting"):
        k = tuple(r[name] for name in ("profile", "policy", "model", "stage", "currency", "phase"))
        bucket = steps[k]
        for i, col in enumerate(("samples", "within_target", "over_10min")):
            bucket[i] += int(r[col])
        bucket[3].append(float(r["median_of_session_medians"]))
    table(args.out, "wait-summary", ("profile", "policy", "model", "stage", "currency", "phase", "samples",
          "within_target_pct", "over_10min_pct", "median_of_weekly_medians"),
          ((*k, n, 100*fit/n, 100*late/n, median(centers)) for k, (n,fit,late,centers) in sorted(steps.items())))

    cycles = defaultdict(lambda: [0, 0, 0, [], []])
    for r in rows(args.raw, "acceleration"):
        k = tuple(r[name] for name in ("profile", "policy", "model", "stage", "marker"))
        slot = cycles[k]
        rate = float(r["ratio"])
        slot[0] += 1
        slot[1] += rate < 1.0 - 1e-7
        slot[2] += abs(rate - 1.0) <= 1e-7
        slot[3].append(rate)
        if int(r["day"]) > 140:
            slot[4].append(rate)
    table(args.out, "acceleration-summary", ("profile", "policy", "model", "stage", "marker",
          "paired_runs", "faster_runs", "tied_runs", "median_time_ratio", "last_28d_median_time_ratio"),
          ((*k,n,faster,tied,median(rates),median(tail) if tail else "") for k,(n,faster,tied,rates,tail) in sorted(cycles.items())))

    records = {}
    daily = defaultdict(list)
    milestones = {}
    for r in rows(args.raw, "daily-seeds"):
        key = tuple(r[k] for k in ("profile", "policy", "model", "stage"))
        seed, day, peak = int(r["seed"]), int(r["day"]), int(r["peak_level_proxy"])
        records[*key, seed, day] = (float(r["log_total_p"]), float(r["log_total_files"]), float(r["log_total_gb"]), peak)
        daily[*key, seed].append((day, float(r["daily_digits_p"]), float(r["daily_digits_wallet"])))
        if peak >= 124:
            milestones.setdefault((*key, seed), day)
    tails = []
    ac = defaultdict(list)
    for key, series in daily.items():
        if key[1] != "natural":
            continue
        first = min(day for day, *_ in series)
        last = max(day for day, *_ in series)
        for start in range(first, last+1, 28):
            window = [(dp,dw) for day,dp,dw in series if start <= day < start+28]
            ac[*key[:-1],start].append((fmean(dp for dp,_ in window), fmean(dw for _,dw in window),
                                        sum(dp <= 1e-8 for dp,_ in window), len(window)))
    for key, data in sorted(ac.items()):
        tails.append((*key,len(data),median(r[0] for r in data),median(r[1] for r in data),
                      min(r[0] for r in data),sum(r[2] for r in data)/sum(r[3] for r in data)))
    table(args.out, "growth-tail", ("profile", "policy", "model", "stage", "window_start_day", "seeds",
          "median_daily_p_digits", "median_daily_wallet_digits", "lowest_seed_daily_p_digits", "flat_p_day_fraction"), tails)
    table(args.out, "level124-proxy", ("profile", "policy", "model", "stage", "seed", "day124_proxy"),
          ((*k,milestones.get(k,"")) for k in sorted(daily)))

    product = []
    ss = sorted({k[-2] for k in records})
    for day in (*DAYS,112,140,168):
        for model, stage, payer, monthly_won in (
            ("prestige","one_set","Free",9_900), ("discount","one_set","Free",9_900),
            ("prestige","full","Free",29_700), ("discount","full","Free",29_700),
            ("none","none","Spend30",33_000), ("none","none","Spend100",99_000)):
            trial = [(records[payer,"natural",model,stage,s,day],records["Free","natural","none","none",s,day],
                      records["Spend30","natural","none","none",s,day]) for s in ss
                     if (payer,"natural",model,stage,s,day) in records]
            if not trial:
                continue
            cost = monthly_won if payer == "Free" else monthly_won * math.ceil(day/30)
            delta = [fmean(x[i]-b[i] for x,b,_ in trial) for i in range(len(METRICS))]
            product.append((day,payer,model,stage,cost,len(trial),*delta,delta[0]*10_000/cost,
                            fmean(x[0]-t[0] for x,_,t in trial),sum(x[0]>t[0] for x,_,t in trial)))
    table(args.out, "paid-value", ("day", "profile", "model", "stage", "cumulative_won", "seeds",
          *(f"mean_{x}" for x in METRICS),"p_digits_per_10000_won","mean_p_digits_vs_turbo","seeds_above_turbo"),
          product)
    if args.core:
        core_thresholds(args.out, args.core)


if __name__ == "__main__":
    main()
