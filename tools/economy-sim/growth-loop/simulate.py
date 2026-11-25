"""Fast log-domain design emulator. The shipping swipe engine is a separate validation layer."""
import argparse
import csv
import hashlib
import json
import math
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
import random
from statistics import median, quantiles

ZERO = -math.inf
DAY_COUNT = 168
APP_VALUE = math.log10(1.20)
APP_PRICE = math.log10(1.30)
FILE_PRICE = math.log10(1.48)
GB_PRICE = math.log10(1.58)
APP_START_COST = math.log10(0.22)
PROFILE = {
    # Active rate, timed subscription prestige factor, offline rate, returns' gift.
    "Free": (1.0, 1.0, 0.032, 1.0),
    "Ads": (1.25, 1.0, 0.052, 1.15),
    "Pass": (1.50, 1.0, 0.060, 1.22),
    "PassVisible": (1.50, 1.0, 0.060, 1.0),
    "Spend30": (3.5, 1.75, 0.078, 1.25),
    "Spend100": (8.0, 4.0, 0.122, 1.35),
}
STAGES = {"none": (0, 0), "one": (1, 0), "spread_three": (3, 0),
          "one_set": (3, 1), "two_sets": (6, 2), "full": (9, 3)}
CHECKPOINTS = (7, 14, 28, 56, 84, 112, 140, 168)


@dataclass(frozen=True)
class Offer:
    income_sku: float = 1
    income_set: float = 1
    income_all: float = 1
    prestige_sku: float = 1
    prestige_set: float = 1
    prestige_all: float = 1
    cost_sku: float = 1
    cost_set: float = 1
    cost_all: float = 1
    offline_minutes_sku: int = 0
    offline_minutes_set: int = 0


OFFERS = {
    "prestige": Offer(1.075, 1.16, 1.08, 1.02, 1.06, 1.10),
    "discount": Offer(1.07, 1.13, 1.15, cost_sku=.975, cost_set=.965, cost_all=.97,
                      offline_minutes_sku=8, offline_minutes_set=25),
}


def logadd(a, b):
    bigger, smaller = max(a, b), min(a, b)
    if smaller == ZERO or bigger - smaller > 16:
        return bigger
    return bigger + math.log1p(10 ** (smaller - bigger)) / math.log(10)


def logsub(a, b):
    if a <= b:
        return ZERO
    if b == ZERO or a - b > 16:
        return a
    return a + math.log10(-math.expm1((b - a) * math.log(10)))


def geometric_batch(wallet, first, step, maximum):
    """Return the number affordable and the total cost in log units."""
    if wallet < first:
        return 0, ZERO
    ratio_minus_one = math.expm1(step * math.log(10))
    if wallet - first > 20:
        count = maximum
    else:
        count = min(maximum, max(1, int(math.log1p(10 ** (wallet - first) * ratio_minus_one) / (step * math.log(10)) + 1e-10)))
    debit = first + math.log10(math.expm1(count * step * math.log(10)) / ratio_minus_one)
    if debit > wallet + 1e-10:
        count -= 1
        debit = (first + math.log10(math.expm1(count * step * math.log(10)) / ratio_minus_one)
                 if count else ZERO)
    return count, debit


def level(log_p):
    # Analytic P-level proxy. The core threshold curve is measured separately.
    return max(1, math.floor(1 + max(0, log_p) / math.log10(2.33)))


def report_log(value):
    return max(0.0, value)


def purchases(offer, sku, sets):
    all_owned = sku == 9
    return (
        math.log10(offer.income_sku ** sku * offer.income_set ** sets * offer.income_all ** all_owned),
        math.log10(offer.prestige_sku ** sku * offer.prestige_set ** sets * offer.prestige_all ** all_owned),
        math.log10(offer.cost_sku ** sku * offer.cost_set ** sets * offer.cost_all ** all_owned),
        sku * offer.offline_minutes_sku + sets * offer.offline_minutes_set,
    )


def run(profile, seed, model, stage, days, schedule=None, price_policy="indexed"):
    active, speed_prestige, offline, returns = PROFILE[profile]
    rng = random.Random(seed)
    pos, p_total, run_p, file_wallet, gb_wallet, files, gb = (ZERO,) * 7
    best_file_record = best_gb_record = layer_files = ZERO
    rank = file_rank = gb_rank = peak = 0
    resets = syncs = run_minutes = 0
    run_id = 1
    last_prestige_session = -3
    last_sync_session = -20
    last_file_min = last_gb_min = 0
    layer_resets = 0
    route = {}
    rows, buys, events, reaches = [], [], [], []
    hit = {}
    previous_p = previous_wallet = 0.0
    effect = purchases(OFFERS.get(model, Offer()), *STAGES[stage if stage != "staged" else "none"])

    def add_p(amount):
        nonlocal pos, p_total, run_p
        pos = logadd(pos, amount)
        p_total = logadd(p_total, amount)
        run_p = logadd(run_p, amount)

    for day in range(1, days + 1):
        if stage == "staged" and day in (3, 14):
            effect = purchases(OFFERS[model], *STAGES["one_set" if day == 3 else "full"])
        income_up, reward_up, cost_down, offline_extra = effect
        for segment in range(4):
            t = (day - 1) * 4 + segment
            # The exogenous noise is paired across purchases and policies.
            noise = rng.gauss(0, .105)
            # Permanent deployment increases the real gain at equal rank along the next run.
            record_acceleration = .045 * resets + .11 * syncs
            permanent = (0.20 * report_log(files) + 0.24 * report_log(gb)
                         + .06 * file_rank + .07 * gb_rank + record_acceleration)
            base = math.log10(active) + permanent + income_up + noise
            away = 705 if segment == 0 else 225
            if t:
                hours = (8 if level(run_p) < 7 else 10 if level(run_p) < 35 else 12) + file_rank + gb_rank
                eligible = min(away, 60 * hours + offline_extra)
                add_p(base + rank * APP_VALUE + math.log10(eligible * offline * returns))
            sample = []
            for minute in range(1, 16):
                run_minutes += 1
                rate = base + rank * APP_VALUE
                if price_policy == "indexed":
                    # The saved run's neutral production funds the new band. The EX factor is separate.
                    frontier_repricing = max(0.0, permanent - .48)
                    quote_step, batch = APP_PRICE, 20
                else:
                    frontier_repricing = .80 * record_acceleration * min(1.0, rank / 25)
                    gap = 25 - rank
                    quote_step = APP_PRICE + (.80 * record_acceleration / 25 if gap > 0 else 0)
                    batch = min(20, gap) if gap > 0 else 20
                cost = APP_START_COST + rank * APP_PRICE + frontier_repricing + cost_down
                phase = "opening" if run_minutes <= 10 else "late"
                sample.append((phase, 10 ** min(10, cost - rate)))
                add_p(rate)
                count, debit = geometric_batch(pos, cost, quote_step, batch)
                if count:
                    pos = logsub(pos, debit)
                    rank += count
                current = level(run_p)
                peak = max(peak, current)
                for marker in (12, 25, 60, 124, 250, 500, 1000):
                    if current >= marker and marker not in hit:
                        hit[marker] = run_minutes
            for phase in ("opening", "late"):
                observations = [wait for group, wait in sample if group == phase]
                if observations:
                    buys.append((day, run_id, "P", phase, len(observations), sum(3/60 <= v <= 1 if phase == "opening" else 1 <= v <= 10 for v in observations),
                                 sum(v > 10 for v in observations), median(observations), max(observations)))
            file_record = (.38 * (run_p - 3) if run_p < 11 else 3.04 + .30 * (run_p - 11)) if run_p >= 3 else ZERO
            file_offer = logsub(file_record, best_file_record) if file_record != ZERO else ZERO
            gb_record = (.48 * (layer_files - 2) if layer_files >= 2 else ZERO)
            gb_offer = logsub(gb_record, best_gb_record) if gb_record != ZERO else ZERO
            wait = sample[-1][1]
            if schedule is None:
                move = ("sync" if gb_offer != ZERO and layer_resets >= 4 and t - last_sync_session >= 10 and t - last_prestige_session >= 2
                        else "reset" if file_offer != ZERO and t - last_prestige_session >= 2 and (wait > 2 or t - last_prestige_session >= 4)
                        else "")
            else:
                move = schedule.get(t, "")
            if move == "sync" and gb_offer == ZERO or move == "reset" and file_offer == ZERO:
                # A replay never grants a currency from a zero entitlement.
                events.append((day, t, move + "_zero", run_id, run_minutes, peak, 0.0, 0.0))
                move = ""
            if not move:
                continue
            route[t] = move
            reaches.extend((run_id, day, marker, elapsed, move) for marker, elapsed in hit.items())
            active_minute = (t + 1) * 15
            gross_file = gross_gb = ZERO
            if move == "reset":
                gross_file = file_offer + math.log10(speed_prestige) + reward_up
                file_wallet, files = logadd(file_wallet, gross_file), logadd(files, gross_file)
                layer_files = logadd(layer_files, gross_file)
                previous_file_rate = gross_file - math.log10(active_minute - last_file_min)
                last_file_min = active_minute
                best_file_record = file_record
                count, debit = geometric_batch(file_wallet, .15 + file_rank * FILE_PRICE + cost_down, FILE_PRICE, 20)
                file_wallet, file_rank = logsub(file_wallet, debit), file_rank + count
                file_time = 10 ** min(10, .15 + file_rank * FILE_PRICE + cost_down - previous_file_rate)
                buys.append((day, run_id, "files", "prestige", 1, int(1 <= file_time <= 10), int(file_time > 10), file_time, file_time))
                layer_resets += 1
                resets += 1
            else:
                gross_gb = gb_offer + math.log10(speed_prestige) + reward_up
                gb_wallet, gb = logadd(gb_wallet, gross_gb), logadd(gb, gross_gb)
                previous_gb_rate = gross_gb - math.log10(active_minute - last_gb_min)
                last_gb_min = active_minute
                best_gb_record = gb_record
                count, debit = geometric_batch(gb_wallet, .15 + gb_rank * GB_PRICE + cost_down, GB_PRICE, 20)
                gb_wallet, gb_rank = logsub(gb_wallet, debit), gb_rank + count
                gb_time = 10 ** min(10, .15 + gb_rank * GB_PRICE + cost_down - previous_gb_rate)
                buys.append((day, run_id, "gb", "prestige", 1, int(1 <= gb_time <= 10), int(gb_time > 10), gb_time, gb_time))
                file_wallet = layer_files = best_file_record = ZERO
                layer_resets = file_rank = 0
                last_sync_session = t
                syncs += 1
            events.append((day, t, move, run_id, run_minutes, level(run_p), report_log(gross_file), report_log(gross_gb)))
            run_id += 1
            last_prestige_session = t
            pos = run_p = ZERO
            rank = run_minutes = 0
            hit.clear()
        lp, lw = report_log(p_total), report_log(pos)
        rows.append((day, lp, lw, lp - previous_p, lw - previous_wallet, run_id, resets, syncs,
                     report_log(files), report_log(gb), peak, level(run_p), rank, file_rank, gb_rank))
        previous_p, previous_wallet = lp, lw
    return rows, buys, events, reaches, route


DAILY = ("day", "log_total_p", "log_wallet_p", "daily_digits_p", "daily_digits_wallet", "run",
         "resets", "syncs", "log_total_files", "log_total_gb", "peak_level_proxy", "run_level_proxy",
         "app_rank", "file_rank", "gb_rank")


def write(out, stem, fields, rows):
    with (out / (stem + ".csv")).open("w") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(fields)
        writer.writerows(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("out", type=Path)
    parser.add_argument("--seeds", type=int, default=30)
    parser.add_argument("--days", type=int, default=DAY_COUNT)
    parser.add_argument("--price-policy", choices=("indexed", "record"), default="indexed")
    args = parser.parse_args()
    out = args.out
    out.mkdir(parents=True, exist_ok=True)
    profile_rows, pair_rows, wait_rows, speed_rows, route_rows = [], [], [], [], []
    waits = defaultdict(lambda: [0, 0, 0, []])
    days = defaultdict(list)
    compare = defaultdict(list)
    stages = tuple(k for k in STAGES if k != "none") + ("staged",)
    for profile in PROFILE:
        for seed in range(1, args.seeds + 1):
            before, waiting, restarts, reached, schedule = run(profile, seed, "none", "none", args.days, price_policy=args.price_policy)
            for policy, model, stage, results in [("natural", "none", "none", (before, waiting, restarts, reached, schedule))] + [
                (policy, model, stage, run(profile, seed, model, stage, args.days,
                                           schedule if policy == "fixed" else None, args.price_policy))
                for policy in ("natural", "fixed") for model in OFFERS
                for stage in (stages if policy == "natural" else ("one_set", "full"))
            ]:
                daily, wait, events, times, _ = results
                key = (profile, policy, model, stage)
                if stage in ("none", "one_set", "full"):
                    for row in daily:
                        if policy == "natural" or row[0] in CHECKPOINTS:
                            profile_rows.append((*key, seed, *row))
                    for event in events:
                        if event[2].endswith("_zero"):
                            route_rows.append((*key, seed, *event))
                for a, b in zip(before, daily):
                    if b[0] in CHECKPOINTS:
                        delta = (b[1] - a[1], b[8] - a[8], b[9] - a[9], b[10] - a[10])
                        pair_rows.append((*key, seed, b[0], *delta))
                        compare[*key, b[0]].append(delta)
                    if policy == "natural" and stage in ("none", "one_set", "full"):
                        days[*key, b[0]].append(b)
                for day, run_id, currency, phase, n, hits, stall, center, high in wait:
                    if policy == "natural" and stage in ("none", "one_set", "full"):
                        bucket = (day - 1) // 7 + 1
                        slot = waits[*key, currency, phase, bucket]
                        slot[0] += n
                        slot[1] += hits
                        slot[2] += stall
                        slot[3].append(center)
                        if high > 10 and (day in CHECKPOINTS or day <= 3):
                            wait_rows.append((*key, seed, day, run_id, currency, phase, n, hits, stall, center, high))
                previous = {}
                for run_id, day, marker, elapsed, kind in times:
                    if marker in previous and previous[marker][0] == run_id - 1:
                        pv = previous[marker]
                        speed_rows.append((*key, seed, run_id, day, marker, pv[1], elapsed, elapsed/pv[1], pv[2], kind))
                    previous[marker] = (run_id, elapsed, kind)
        print(profile, flush=True)
    prefix = ("profile", "policy", "model", "stage")
    write(out, "daily-seeds", (*prefix, "seed", *DAILY), profile_rows)
    write(out, "daily-growth", (*prefix, "day", *(f"median_{col}" for col in DAILY[1:])),
          ((*key, *(median(row[i] for row in values) for i in range(1, len(DAILY)))) for key, values in sorted(days.items())))
    write(out, "paired", (*prefix, "seed", "day", "digits_p", "digits_files", "digits_gb", "peak_level_diff"), pair_rows)
    write(out, "paired-summary", (*prefix, "day", *(f"mean_{k}" for k in ("digits_p", "digits_files", "digits_gb", "peak")),
                                 "p10_digits_p", "median_digits_p", "p90_digits_p", "positive_p_seeds"),
          ((*key, *(math.fsum(x[i] for x in values)/len(values) for i in range(4)),
            *([quantiles([x[0] for x in values], n=10, method="inclusive")[0], median(x[0] for x in values),
               quantiles([x[0] for x in values], n=10, method="inclusive")[-1]] if len(values)>1 else [values[0][0]]*3),
            sum(x[0] > 0 for x in values)) for key, values in sorted(compare.items())))
    write(out, "waiting", (*prefix, "currency", "phase", "week", "samples", "within_target", "over_10min", "median_of_session_medians"),
          ((*key, n, hits, stall, median(medians)) for key, (n,hits,stall,medians) in sorted(waits.items())))
    write(out, "stall-locations", (*prefix, "seed", "day", "run", "currency", "phase", "samples", "within_target", "over_10min",
                                    "median_min", "worst_min"), wait_rows)
    write(out, "acceleration", (*prefix, "seed", "run", "day", "marker", "previous_active_min", "active_min", "ratio",
                                  "previous_ended_by", "ended_by"), speed_rows)
    write(out, "skipped-prestige", (*prefix, "seed", "day", "session", "kind", "run", "active_min", "level_proxy",
                                      "log_files", "log_gb"), route_rows)
    files = sorted(out.glob("*.csv"))
    (out/"manifest.json").write_text(json.dumps(
        {"kind":"analytic design emulator", "seeds":list(range(1,args.seeds+1)), "days":args.days,
         "price_policy":args.price_policy,
         "source_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
         "csv_sha256":{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in files}}, indent=2)+"\n")


if __name__ == "__main__":
    main()
