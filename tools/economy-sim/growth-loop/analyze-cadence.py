"""Parse deterministic CadenceProbe shards into the B calibration report tables."""
import argparse
from collections import defaultdict
import csv
import gzip
from pathlib import Path
from statistics import fmean, median

PROFILES = ("Free", "Ads", "Pass", "PassVisible", "Spend30", "Spend100")
MODES = ("Loop", "Quarter", "Light45", "Light3")
DAYS = (7, 14, 28, 56, 168)
LEVELS = (12, 25, 60, 124)


def read(folder, name):
    file = folder / (name + ".csv")
    with (file.open() if file.exists() else gzip.open(str(file) + ".gz", "rt")) as stream:
        return list(csv.DictReader(stream))


def write(dest, name, head, data):
    with (dest / f"{name}.csv").open("w") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(head.split(","))
        writer.writerows(data)


def index(rows, fields):
    result = {tuple(r[k] for k in fields): r for r in rows}
    if len(result) != len(rows):
        raise ValueError(f"Duplicate key {fields}")
    return result


def mid(values):
    return median(values) if values else ""


def grouped(rows, fields):
    result = defaultdict(list)
    for row in rows:
        result[tuple(row[k] for k in fields)].append(row)
    return result


def core_play(rows):
    groups = grouped(rows, ("profile", "sku", "indexed", "day"))
    fields = ("log_total_p", "log_wallet", "log_files", "log_gb",
              "peak", "level", "run", "extra_offline_min", "log_extra_offline_p")
    return [
        (*key, len(items), *(median(float(row[m]) for row in items) for m in fields))
        for key, items in sorted(groups.items(), key=lambda entry: (
            PROFILES.index(entry[0][0]), int(entry[0][1]), entry[0][2], int(entry[0][3])))
    ]


def paired_play(dest, daily, mode):
    key = ("profile", "seed", "sku", "indexed", "day")
    view = index(daily, key)
    mode_prices = sorted({r["indexed"] for r in daily})
    profiles = sorted({r["profile"] for r in daily}, key=PROFILES.index)
    seed_set = sorted({r["seed"] for r in daily}, key=int)
    horizon = max(int(row["day"]) for row in daily)
    relevant = [str(day) for day in sorted(set(DAYS) | {horizon}) if day <= horizon]
    if mode_prices == ["false", "true"]:
        paired = []
        daily_losses = []
        for p in profiles:
            for seed in seed_set:
                differences = []
                for day in range(1, horizon + 1):
                    k = str(day)
                    base = view[p, seed, "0", "false", k]
                    next_state = view[p, seed, "0", "true", k]
                    loss = float(base["log_total_p"]) - float(next_state["log_total_p"])
                    differences.append(loss)
                    if k in relevant:
                        paired.append((p, int(seed), day, loss, float(next_state["log_total_p"]),
                                       float(base["log_total_p"])))
                worst = max(differences)
                daily_losses.append((p, seed, horizon, worst, differences.index(worst) + 1,
                                     sum(loss > 1.0 for loss in differences)))
        write(dest, "price-loss-pairs", "profile,seed,day,loss_digits,new_total_log_p,baseline_total_log_p", paired)
        write(dest, "price-loss-all-days", "profile,seed,days,worst_loss_digits,worst_day,days_over_one_loss", daily_losses)
        panel = defaultdict(list)
        for p, _, day, loss, _, _ in paired:
            panel[p, day].append(loss)
        write(dest, "price-loss", "profile,day,seeds,median_loss,mean_loss,worst_loss,seeds_over_one,all_pass",
              ((*k, len(l), median(l), fmean(l), max(l), sum(x > 1 for x in l), max(l) <= 1)
               for k, l in panel.items()))
    if {"0","3","9"} <= {r["sku"] for r in daily}:
        paid, full_vs_set, all_turbo = [], [], []
        for p in profiles:
            for day in relevant:
                upgrades = [float(view[p,seed,"9","true",day]["log_total_p"])
                            - float(view[p,seed,"3","true",day]["log_total_p"]) for seed in seed_set]
                full_vs_set.append((p, day, len(upgrades), fmean(upgrades), median(upgrades), min(upgrades),
                                    sum(gain < 0 for gain in upgrades)))
                for sku in ("3","9"):
                    if sku == "3" and mode in ("Light45","Light3"):
                        goal = None
                    else:
                        goal = (.25 if mode in ("Light45","Light3") else {"3":.5,"9":1.0}[sku])
                    deltas = []
                    for seed in seed_set:
                        before = view[p,seed,"0","true",day]
                        after = view[p,seed,sku,"true",day]
                        deltas.append(float(after["log_total_p"])-float(before["log_total_p"]))
                    paid.append((p,day,sku,len(deltas),goal, fmean(deltas),median(deltas),min(deltas),
                                 sum(x>=goal for x in deltas) if goal is not None else "",
                                 fmean(deltas)>=goal if goal is not None else ""))
        if "Free" in profiles and "Spend30" in profiles:
            for day in range(1,horizon+1):
                diffs = [float(view["Free",s,"9","true",str(day)]["log_total_p"])
                         -float(view["Spend30",s,"0","true",str(day)]["log_total_p"]) for s in seed_set]
                all_turbo.append((day,len(diffs),fmean(diffs),max(diffs),sum(x>0 for x in diffs)))
        write(dest, "ex-gate", "profile,day,sku,seeds,goal,mean_gain,median_gain,worst_gain,seeds_at_goal,mean_pass", paid)
        write(dest, "ex-full-vs-set", "profile,day,seeds,mean_gain,median_gain,worst_gain,seeds_behind_one_set", full_vs_set)
        write(dest, "turbo", "day,seeds,mean_full_free_minus_turbo,max_full_free_minus_turbo,seeds_above_turbo", all_turbo)


def waits_table(dest, daily):
    data = []
    for k, rows in grouped(daily, ("profile", "sku", "indexed")).items():
        a,b,c,d,e,f = (sum(int(r[key]) for r in rows) for key in
                       ("open_obs","open_hits","late_obs","late_hits","over10","index_exposed"))
        data.append((*k, a, b, b/a*100 if a else "", c, d, d/c*100 if c else "", e,
                     e/(a+c)*100 if a+c else "", f))
    write(dest, "waits", "profile,sku,indexed,open_n,open_hit,open_pct,late_n,late_hit,late_pct,over10_n,over10_pct,exposed", data)


def tail_table(dest, daily):
    result = defaultdict(list)
    for k, rows in grouped(daily, ("profile", "sku", "indexed", "seed")).items():
        ordered = sorted(rows, key=lambda row: int(row["day"]))
        p = [0.] + [float(r["log_total_p"]) for r in ordered]
        last = min(28,len(ordered))
        differences = [b-a for a,b in zip(p, p[1:])][-last:]
        mean = fmean(differences)
        flat = sum(x <= 1e-8 for x in differences)
        result[k[:-1]].append((mean,flat))
    write(dest, "tail", "profile,sku,indexed,seeds,median_digits_per_day,min_digits_per_day,flat_days,passing_seeds",
          ((*k,len(v),median(a for a,_ in v),min(a for a,_ in v),sum(b for _,b in v),
            sum(a>=.13 and b==0 for a,b in v)) for k,v in result.items()))


def prestige_table(dest, rows, daily, mode):
    out = []
    groups = grouped(rows, ("profile", "sku", "indexed", "kind"))
    states = grouped(daily, ("profile","sku","indexed"))
    for k, days in states.items():
        members = {x["seed"] for x in days}
        for kind in ("reset","sync"):
            events = groups.get((*k,kind), [])
            clocks = ("gap_ms" if kind=="reset" else "gap_sessions")
            bounds = (15,45) if kind=="reset" else (2,4)
            unit = 60_000 if kind=="reset" else 1
            buys = (3,8) if kind=="reset" else (2,5)
            waits = [int(r[clocks])/unit for r in events if r[clocks]]
            receipt = [int(r["affordable_until12"]) for r in events]
            good = sum(buys[0] <= n <= buys[1] for n in receipt)
            valid_intervals = sum(bounds[0] <= d <= bounds[1] for d in waits)
            last_event = {r["seed"]:r for r in events}
            last_daily = {r["seed"]:r for r in days}
            tails = []
            for seed, event in last_event.items():
                last = last_daily[seed]
                tail = ((int(last["active_ms"]) - int(event["active_ms"])) / unit if kind == "reset"
                        else int(last["day"]) * (4 if mode == "Loop" else 1) - int(event["session"]))
                tails.append(tail)
            out.append((*k,kind,len(members),len({r["seed"] for r in events}),len(receipt),mid(receipt),
                        good,len(waits),mid(waits),valid_intervals,max(waits,default=""),receipt.count(12),
                        sum(d>bounds[1] for d in tails),max(tails,default="")))
    write(dest, "payouts", "profile,sku,indexed,kind,seeds,seeds_with_payout,payments,median_affordable,affordable_in_band,intervals,median_interval,intervals_in_band,max_interval,affordable_ge12,tail_over_limit,longest_tail", out)


def acceleration(dest, rows, daily):
    pairs, unknown, transitions = defaultdict(list), defaultdict(int), defaultdict(list)
    for key, values in grouped(rows, ("profile", "sku", "indexed", "seed", "level")).items():
        seq = sorted((int(r["run"]),int(r["elapsed_ms"]),r["end_kind"]) for r in values)
        agg = (*key[:3],int(key[4]))
        for (prev,t0,kind),(n,t1,_) in zip(seq,seq[1:]):
            if prev+1!=n:
                continue
            if t0<=0:
                unknown[agg]+=1
            else:
                ratio = t1/t0
                pairs[agg].append(ratio)
                transitions[(*agg,kind)].append(ratio)
    table=[]
    for k in grouped(daily,("profile","sku","indexed")):
        for l in LEVELS:
            g=(*k,l)
            xs=pairs[g]
            faster=sum(v<1 for v in xs)
            middle=mid(xs)
            table.append((*g,len(xs),unknown[g],sum(v==1 for v in xs),faster,
                          faster/len(xs)*100 if xs else "",middle,
                          middle<=.8 and faster/len(xs)>=.7 if xs else "no_sample"))
    write(dest,"acceleration","profile,sku,indexed,level,pairs,previous_zero,ties,faster,faster_pct,median_ratio,pass",table)
    write(dest,"acceleration-transitions","profile,sku,indexed,level,previous_end,pairs,median_ratio,faster_pct",
          ((*k,len(xs),median(xs),100*sum(x<1 for x in xs)/len(xs)) for k,xs in sorted(transitions.items())))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs",type=Path)
    parser.add_argument("out",type=Path)
    parser.add_argument("--economy",default="Merit")
    args=parser.parse_args()
    graph=[]
    for pattern in MODES:
        run=args.inputs/pattern
        if not run.exists():
            continue
        destination=args.out/pattern
        destination.mkdir(parents=True,exist_ok=True)
        daily=read(run,"daily")
        keys=index(daily,("profile","seed","sku","indexed","day"))
        schedules = {(p,s,sku,idx) for p,s,sku,idx,_ in keys}
        h=max(int(row[-1]) for row in keys)
        if set(keys) != {(*i,str(day)) for i in schedules for day in range(1,h+1)}:
            raise ValueError(f"Missing days in {pattern}")
        graph.extend((args.economy,pattern,*r) for r in core_play(daily))
        paired_play(destination,daily,pattern)
        waits_table(destination,daily)
        tail_table(destination,daily)
        prestige_table(destination,read(run,"prestige"),daily,pattern)
        acceleration(destination,read(run,"reaches"),daily)
    write(args.out,"daily-growth","economy,pattern,profile,sku,indexed,day,seeds,median_log_total_p,median_log_wallet,median_log_files,median_log_gb,median_peak,median_level,median_run,median_extra_offline_min,median_log_extra_offline_p",graph)


if __name__=="__main__":
    main()
