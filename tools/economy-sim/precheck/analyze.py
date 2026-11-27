"""Report paired deltas, paired bootstrap intervals and EX condition realization."""
import argparse
from collections import defaultdict
import csv
from functools import lru_cache
from pathlib import Path
import random
from statistics import fmean, median, quantiles


PROFILES = {
    "Free": (66, 84), "Ads": (49, 63), "Pass": (35, 49),
    "Spend30": (24, 32), "Spend100": (11, 17), "PassVisible": (35, 49),
}
SCENARIOS = ("one", "spread_three", "one_set", "two_sets", "full", "staged", "offline_cap")
MONOTONIC = ("none", "one", "spread_three", "one_set", "two_sets", "full")
DAYS = (7, 14, 28, 56)
METRICS = {"level": int, "peak_level": int, "log10_run_p": float, "files_total": int, "gb_total": int}
REQUIRED_METRICS = ("level", "log10_run_p", "files_total", "gb_total")
SEEDS = range(1, 31)
HORIZON = 84
BOOTSTRAP = 4_000
PRICE = {
    "one": 3300, "spread_three": 9900, "one_set": 9900, "two_sets": 19800,
    "full": 29700, "staged": 29700, "offline_cap": 29700,
}
ASSETS = (
    "prism_trail", "prism_wave", "prism_theme", "reboot_frame", "reboot_theme",
    "reboot_sync", "studio_deck", "studio_star", "studio_pro",
)


def read(path):
    with path.open() as stream:
        return list(csv.DictReader(stream))


def write(path, columns, rows):
    with path.open("w") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(columns)
        writer.writerows(rows)


@lru_cache
def bootstrap_draws(size):
    rng = random.Random(20261125)
    return [tuple(rng.randrange(size) for _ in range(size)) for _ in range(BOOTSTRAP)]


def describe(values):
    means, medians = [], []
    for draw in bootstrap_draws(len(values)):
        sample = [values[i] for i in draw]
        means.append(fmean(sample))
        medians.append(median(sample))
    mean_interval = quantiles(means, n=40, method="inclusive")
    median_interval = quantiles(medians, n=40, method="inclusive")
    return (fmean(values), median(values), mean_interval[0], mean_interval[-1],
            median_interval[0], median_interval[-1], sum(x > 0 for x in values),
            sum(x == 0 for x in values), sum(x < 0 for x in values))


def index(rows, extra=None):
    result = {}
    for row in rows:
        key = (row["profile"], row["scenario"], int(row["seed"]))
        if extra:
            key += (int(row[extra]),)
        if key in result:
            raise RuntimeError(f"Duplicate key {key}")
        result[key] = row
    return result


def reach(row):
    return float(row["lv124_day"]) if row["lv124_day"] else HORIZON


def reach_state(row):
    return "reached" if row["lv124_day"] else "censored"


def tables(raw, output):
    overview = index(read(raw / "summary.csv"))
    snapshots = index(read(raw / "checkpoints.csv"), "day")
    expected = {(profile, scenario, seed) for profile in PROFILES for scenario in ("none", *SCENARIOS)
                for seed in SEEDS}
    assert overview.keys() == expected
    assert snapshots.keys() == {(*key, day) for key in expected for day in DAYS}
    pairs, comparisons, gates, increases, value, expectations = [], [], [], [], [], []
    for profile, (low, high) in PROFILES.items():
        base_days = [reach(overview[profile, "none", seed]) for seed in SEEDS]
        base_censored = sum(reach_state(overview[profile, "none", seed]) == "censored" for seed in SEEDS)
        base_hits = sum(low <= reach(row) <= high and reach_state(row) == "reached"
                        for row in (overview[profile, "none", seed] for seed in SEEDS))
        base_center = median(float(row["lv124_day"] or "inf")
                             for row in (overview[profile, "none", seed] for seed in SEEDS))
        base_pass = low <= base_center <= high and base_hits >= 24
        target = fmean(base_days) * .05 if profile in ("Spend30", "Spend100") else 3.0
        for scenario in SCENARIOS:
            candidate_days = []
            complete_deltas = []
            candidate_censored = 0
            for seed in SEEDS:
                before = overview[profile, "none", seed]
                after = overview[profile, scenario, seed]
                candidate_days.append(reach(after))
                candidate_censored += reach_state(after) == "censored"
                if reach_state(before) == reach_state(after) == "reached":
                    complete_deltas.append(reach(before) - reach(after))
                pairs.append((profile, scenario, seed, before["lv124_day"], after["lv124_day"], reach(before) - reach(after),
                              reach_state(before), reach_state(after), before["first_reset_day"], after["first_reset_day"],
                              before["first_sync_day"], after["first_sync_day"]))
            faster = [b - a for b, a in zip(base_days, candidate_days)]
            comparisons.append((profile, "none", scenario, 124, "earlier_days_84d_restricted", *describe(faster)))
            temporal = []
            for day in DAYS:
                for name, typ in METRICS.items():
                    deltas = [typ(snapshots[profile, scenario, seed, day][name]) -
                              typ(snapshots[profile, "none", seed, day][name]) for seed in SEEDS]
                    comparisons.append((profile, "none", scenario, day, name, *describe(deltas)))
                    if name in REQUIRED_METRICS:
                        temporal.append(fmean(deltas))
            delay_over_3 = sum(x < -3 for x in faster)
            unknown_delay = sum(reach_state(overview[profile, scenario, seed]) == "censored"
                                and reach_state(overview[profile, "none", seed]) == "censored" for seed in SEEDS)
            gates.append((profile, scenario, base_center, base_hits, base_censored, base_pass,
                          fmean(base_days), fmean(faster), median(faster), target, fmean(faster) >= target,
                          max(0, -min(faster)), delay_over_3, unknown_delay, candidate_censored,
                          sum(x > 0 for x in temporal), sum(x == 0 for x in temporal), sum(x < 0 for x in temporal),
                          len(complete_deltas), fmean(complete_deltas) if complete_deltas else ""))
            if scenario == "full":
                proportional = profile in ("Spend30", "Spend100")
                buyer_low, buyer_high = (.05, .15) if proportional else (3, 10)
                buyers = [(b - a) / (b if proportional else 1) for b, a in zip(base_days, candidate_days)]
                cuts = quantiles(buyers, n=10, method="inclusive")
                compatible = sum(buyer_low <= x <= buyer_high for x in buyers)
                expectations.append((profile, "baseline_share" if proportional else "days", buyer_low, buyer_high,
                                     cuts[0], median(buyers), cuts[-1], compatible))
            gain_28 = fmean(int(snapshots[profile, scenario, seed, 28]["level"]) -
                           int(snapshots[profile, "none", seed, 28]["level"]) for seed in SEEDS)
            cost = PRICE[scenario]
            value.append((profile, scenario, cost, fmean(faster), median(faster), gain_28,
                          fmean(faster) * 10_000 / cost, gain_28 * 10_000 / cost))
        for left, right in zip(MONOTONIC, MONOTONIC[1:]):
            for day in DAYS:
                for name in REQUIRED_METRICS:
                    typ = METRICS[name]
                    deltas = [typ(snapshots[profile, right, seed, day][name]) -
                              typ(snapshots[profile, left, seed, day][name]) for seed in SEEDS]
                    stats = describe(deltas)
                    increases.append((profile, left, right, day, name, *stats,
                                      "increase" if stats[0] > 0 else "tie" if stats[0] == 0 else "decrease"))
    for base, cost in (("Free", 33000), ("Pass", 27500)):
        faster = [reach(overview[base, "none", seed]) - reach(overview["Spend30", "none", seed]) for seed in SEEDS]
        gain_28 = fmean(int(snapshots["Spend30", "none", seed, 28]["level"]) -
                        int(snapshots[base, "none", seed, 28]["level"]) for seed in SEEDS)
        value.append((base, "turbo_subscription", cost, fmean(faster), median(faster), gain_28,
                      fmean(faster) * 10_000 / cost, gain_28 * 10_000 / cost))

    delta_columns = ("mean", "median", "mean_ci95_low", "mean_ci95_high", "median_ci95_low",
                     "median_ci95_high", "positive_seeds", "tied_seeds", "negative_seeds")
    prefix = ("profile", "from", "to", "day", "metric")
    write(output / "paired.csv", ("profile", "scenario", "seed", "none_lv124_day", "ex_lv124_day",
          "earlier_days_84d_restricted", "none_status", "ex_status", "none_first_reset", "ex_first_reset",
          "none_first_sync", "ex_first_sync"), pairs)
    write(output / "effects.csv", (*prefix, *delta_columns), comparisons)
    write(output / "monotonicity.csv", (*prefix, *delta_columns, "mean_result"), increases)
    write(output / "gates.csv", ("profile", "scenario", "none_median", "none_in_band", "none_censored", "none_pass",
          "none_restricted_mean", "mean_earlier_days", "median_earlier_days", "required_mean_earlier_days", "mean_speed_pass",
          "worst_observed_delay_lower_bound", "seeds_over_3_days_late", "both_censored_seeds", "ex_censored",
          "positive_temporal_means", "tied_temporal_means", "negative_temporal_means", "complete_reach_pairs",
          "complete_pair_mean_earlier_days"), gates)
    write(output / "buyer-expectation.csv", ("profile", "unit", "proposed_gain_low", "proposed_gain_high", "gain_p10",
          "gain_median", "gain_p90", "seeds_in_proposed_range"), expectations)
    write(output / "value.csv", ("profile", "scenario", "won", "mean_earlier_days", "median_earlier_days",
          "mean_day28_level_gain", "days_per_10000won", "day28_levels_per_10000won"), value)


def activation(raw, output):
    condition_groups = defaultdict(list)
    for row in read(raw / "conditions.csv"):
        key = (row["actor"], int(row["day"]), ASSETS[int(row["asset_index"])])
        condition_groups[row["profile"], *key].append(row)
        condition_groups["All", *key].append(row)
    condition_rows = []
    for (profile, actor, day, sku), rows in sorted(condition_groups.items()):
        swipes = sum(int(row["swipes"]) for row in rows)
        fired = sum(int(row["activated"]) for row in rows)
        credited = sum(float(row["actual_marginal_bp_sum"]) for row in rows)
        condition_rows.append((profile, actor, day, sku, swipes, fired, fired / swipes if swipes else "",
                               25 * fired / swipes if swipes else "", credited / swipes if swipes else ""))
    write(output / "activation.csv", ("profile", "actor", "day", "sku", "swipes", "activated", "activation_share",
          "mean_nominal_condition_bp", "mean_actual_marginal_bp"), condition_rows)

    samples = defaultdict(list)
    for row in read(raw / "samples.csv"):
        key = (row["actor"], int(row["day"]))
        samples[row["profile"], *key].append(row)
        samples["All", *key].append(row)
    realization = []
    for (profile, actor, day), rows in sorted(samples.items()):
        swipes = sum(int(row["swipes"]) for row in rows)
        if not swipes:
            continue
        mean_nominal = sum(int(row["nominal_bp_sum"]) for row in rows) / swipes
        mean_actual = sum(float(row["actual_bp_sum"]) for row in rows) / swipes
        base = sum(int(row["same_state_base_p"]) for row in rows)
        extra = sum(int(row["ex_extra_p"]) for row in rows)
        no_pay_cap = sum(int(row["ex_blocked_by_cap"]) for row in rows)
        attempts = sum(int(row["auto_attempts"]) for row in rows)
        realization.append((profile, actor, day, swipes, mean_nominal, mean_actual, extra * 10_000 / base if base else "",
                            no_pay_cap, attempts, 1000 - mean_actual))
    write(output / "realization.csv", ("profile", "actor", "day", "swipes", "mean_nominal_bp", "mean_actual_bp",
          "p_weighted_actual_bp", "ex_blocked_by_cap", "auto_attempts", "gap_from_advertised_max_bp"), realization)


def delays(raw, output):
    summaries = index(read(raw / "summary.csv"))
    events = defaultdict(list)
    for row in read(raw / "prestige.csv"):
        if row["scenario"] in ("none", "full"):
            events[row["profile"], row["scenario"], int(row["seed"])].append(row)
    rows = []
    near_rows = []
    for profile in PROFILES:
        for seed in SEEDS:
            baseline = summaries[profile, "none", seed]
            full = summaries[profile, "full", seed]
            target = reach(baseline)
            delay = reach(full) - target
            if delay <= 0:
                continue
            before = [row for row in events[profile, "none", seed] if float(row["day"]) <= target]
            after = [row for row in events[profile, "full", seed] if float(row["day"]) <= target]
            base_reset = sum(row["kind"] == "reset" for row in before)
            full_reset = sum(row["kind"] == "reset" for row in after)
            distinct = next(((a, b) for a, b in zip(before, after)
                             if a["kind"] != b["kind"] or abs(float(a["day"]) - float(b["day"])) >= 1 / 6), None)
            if distinct is None:
                distinct = (before[len(after)] if len(before) > len(after) else {},
                            after[len(before)] if len(after) > len(before) else {})
            rows.append((profile, seed, delay, baseline["lv124_day"], full["lv124_day"], base_reset, full_reset,
                         len(before) - base_reset, len(after) - full_reset,
                         *(value.get(key, "") for value in distinct
                           for key in ("kind", "day", "level_before", "decision", "gain", "stall_sessions"))))
            for scenario in ("none", "full"):
                for event in events[profile, scenario, seed]:
                    if target - 3 <= float(event["day"]) <= reach(full):
                        near_rows.append((profile, seed, scenario, event["kind"], event["day"], event["level_before"],
                                          event["decision"], event["gain"], event["stall_sessions"]))
    event_fields = ("kind", "day", "level", "decision", "gain", "stalls")
    write(output / "delays.csv", ("profile", "seed", "delay_lower_bound_days", "none_lv124_day", "full_lv124_day",
          "none_resets_by_baseline_goal", "full_resets_by_baseline_goal", "none_syncs_by_baseline_goal",
          "full_syncs_by_baseline_goal", *(f"{case}_first_shift_{field}" for case in ("none", "full") for field in event_fields)), rows)
    write(output / "delayed_prestige_near_goal.csv", ("profile", "seed", "scenario", "kind", "day", "level_before",
          "decision", "gain", "stall_sessions"), near_rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("raw", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    tables(args.raw, args.output)
    activation(args.raw, args.output)
    delays(args.raw, args.output)


if __name__ == "__main__":
    main()
