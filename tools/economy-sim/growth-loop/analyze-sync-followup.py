"""Compare archived SoftReplay and follow-up trials from the real core runner."""
import argparse
from collections import defaultdict
import csv
import gzip
from pathlib import Path
from statistics import fmean, median

CHECKPOINTS = {7, 14, 28, 56}
PATTERNS = ('Loop', 'Quarter', 'Light45', 'Light3')


def read(folder, name):
    source = folder / f'{name}.csv'
    if not source.exists():
        with gzip.open(str(source) + '.gz', 'rt') as stream:
            yield from csv.DictReader(stream)
    else:
        with source.open() as stream:
            yield from csv.DictReader(stream)


def write(dest, name, fields, rows):
    with (dest / f'{name}.csv').open('w', newline='') as result:
        writer = csv.writer(result, lineterminator='\n')
        writer.writerow(fields.split(','))
        writer.writerows(rows)


def percentages(hits, total):
    return hits * 100 / total if total else ''


def acceleration(label, folder, output):
    grouped = defaultdict(list)
    for r in read(folder, 'reaches'):
        if r['indexed'] == 'true' and r['sku'] == '0':
            grouped[r['profile'],r['seed'],int(r['level'])].append((int(r['run']),r['end_kind'],int(r['elapsed_ms'])))
    ratios = defaultdict(list)
    previous_zero = defaultdict(int)
    for (profile, _, level), runs in grouped.items():
        runs.sort()
        for (before, prestige, t0), (after, _, t1) in zip(runs, runs[1:]):
            if before + 1 != after or prestige not in ('sync','reset'):
                continue
            key = (profile,level,prestige)
            if t0 <= 0:
                previous_zero[key] += 1
            else:
                ratios[key].append((t0,t1))
    for profile in sorted({k[0] for k in grouped}):
        for level in (12,25,60,124):
            for prestige in ('sync','reset'):
                key = (profile,level,prestige)
                pairs = ratios[key]
                before, after = zip(*pairs) if pairs else ((),())
                observation = [b / a for a,b in pairs]
                n = len(pairs)
                h80 = sum(5 * b <= 4 * a for a,b in pairs)
                h100 = sum(b < a for a,b in pairs)
                ties = sum(a == b for a,b in pairs)
                fit = h80 / n >= .7 if n else 'no_sample'
                output.append((label,*key,n,previous_zero[key],h80,percentages(h80,n),h100,percentages(h100,n),ties,
                    median(observation) if n else '',median(before) if n else '',median(after) if n else '',fit))


def progression(label, folder, wait_output, pay_output, tail_output, loss_output, baseline):
    grouped = defaultdict(list)
    for r in read(folder, 'daily'):
        if r['indexed'] == 'true' and r['sku'] == '0':
            grouped[r['profile'],r['seed']].append(r)
    by_profile = defaultdict(list)
    for (profile, seed), rows in grouped.items():
        days = sorted(rows, key=lambda row: int(row['day']))
        if [int(row['day']) for row in days] != list(range(1,len(days) + 1)):
            raise ValueError((label, profile, seed, 'Nonconsecutive days'))
        tail = [float(row['log_total_p']) for row in days[-29:]]
        gains = [b - a for a,b in zip(tail,tail[1:])]
        new_avg = fmean(gains) if gains else 0
        flat = sum(x <= 1e-8 for x in gains)
        peak = max(int(row['peak']) for row in rows)
        by_profile[profile].append((rows,new_avg,flat,peak))
        if baseline and label != 'SoftReplay':
            paired = [(baseline[profile,seed,r['day']] - float(r['log_total_p']),int(r['day']))
                      for r in rows if (profile,seed,r['day']) in baseline]
            if paired:
                worst, day = max(paired)
                loss_output.append((label,profile,seed,len(paired),worst,day,sum(value > 1 for value,_ in paired)))
    for profile, samples in sorted(by_profile.items()):
        buy_cols = ('open_bought','open_bought_in_band','late_bought','late_bought_in_band')
        ps = [r for rows,_,_,_ in samples for r in rows]
        values = [sum(int(r[k]) for r in ps) if k in ps[0] else '' for k in
            ('open_obs','open_hits','late_obs','late_hits','over10',*buy_cols)]
        a,b,c,d,o,e,f,g,h = values
        wait_output.append((label,profile,a,b,percentages(b,a),c,d,percentages(d,c),o,e,f,
            percentages(f,e) if e != '' else '',g,h,percentages(h,g) if g != '' else ''))
        tail_output.append((label,profile,len(samples),median(x[1] for x in samples),min(x[1] for x in samples),
            sum(x[2] for x in samples),sum(x[1] >= .13 and x[2] == 0 for x in samples),sum(x[3] >= 124 for x in samples)))
    group_pays = defaultdict(list)
    for r in read(folder,'prestige'):
        if r['indexed'] == 'true' and r['sku'] == '0':
            group_pays[r['profile'],r['kind']].append(r)
    for (profile,kind), samples in sorted(group_pays.items()):
        low, high = (3,8) if kind == 'reset' else (2,5)
        affordable = sum(low <= int(x['affordable_until12']) <= high for x in samples)
        field = 'gap_ms' if kind == 'reset' else 'gap_sessions'
        intervals = [int(x[field]) / (60_000 if kind == 'reset' else 1) for x in samples if x[field]]
        a,b = (15,45) if kind == 'reset' else (2,4)
        eligible = sum(a <= x <= b for x in intervals)
        pay_output.append((label,profile,kind,len(samples),affordable,percentages(affordable,len(samples)),
            len(intervals),eligible,percentages(eligible,len(intervals)),median(intervals) if intervals else ''))


def ex_gates(label, pattern, folder, stats, daily_turbo):
    values = defaultdict(dict)
    for r in read(folder,'daily'):
        if r['indexed'] == 'true':
            key = (r['profile'],int(r['day']))
            pair = (r['seed'],int(r['sku']))
            if pair in values[key]:
                raise ValueError((label,pattern,key,pair))
            values[key][pair] = float(r['log_total_p'])
    for (profile, day), scores in sorted(values.items()):
        seeds = sorted({seed for seed,_ in scores}, key=int)
        full = [scores[s,9] - scores[s,0] for s in seeds]
        one = [scores[s,3] - scores[s,0] for s in seeds]
        delta = [scores[s,9] - scores[s,3] for s in seeds]
        full_target = 1.0 if pattern == 'Quarter' else .25
        stats.append((label,pattern,profile,day,day in CHECKPOINTS,len(seeds),fmean(one),fmean(full),fmean(delta),
            sum(v < 0 for v in delta),fmean(delta) >= 0,fmean(one) >= .5 if pattern == 'Quarter' else '',fmean(full) >= full_target))
    for day in sorted({day for _,day in values}):
        if ('Free',day) not in values or ('Spend30',day) not in values:
            continue
        free = values['Free',day]
        turbo = values['Spend30',day]
        diffs = [value - turbo[s,0] for (s,sku),value in free.items() if sku == 9]
        daily_turbo.append((label,pattern,day,day in CHECKPOINTS,len(diffs),max(diffs),sum(v > 0 for v in diffs)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input',type=Path)
    parser.add_argument('baseline',type=Path)
    parser.add_argument('output',type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    baseline = {(r['profile'],r['seed'],r['day']):float(r['log_total_p']) for r in read(args.baseline/'Loop','daily')
                if r['sku'] == '0' and r['indexed'] == 'true'}
    acc, waits, pays, tails, loss, ex, turbo = [], [], [], [], [], [], []
    for trial in (args.baseline, *sorted(args.input.iterdir())):
        for pattern in PATTERNS:
            folder = trial/pattern
            if not (folder/'daily.csv').is_file() and not (folder/'daily.csv.gz').is_file():
                continue
            if pattern == 'Loop':
                acceleration(trial.name,folder,acc)
                progression(trial.name,folder,waits,pays,tails,loss,baseline)
            else:
                ex_gates(trial.name,pattern,folder,ex,turbo)
    for name, header, rows in (
        ('acceleration','trial,profile,level,previous_end,pairs,previous_zero,ratio_le_080,ratio_le_080_pct,faster,faster_pct,ties,median_ratio,median_old_ms,median_new_ms,gate_70',acc),
        ('waits','trial,profile,open_quotes,open_quotes_in_band,open_quotes_pct,late_quotes,late_quotes_in_band,late_quotes_pct,quotes_over_10m,open_purchases,open_purchases_in_band,open_purchases_pct,late_purchases,late_purchases_in_band,late_purchases_pct',waits),
        ('payouts','trial,profile,kind,payments,in_band,pct,intervals,interval_in_band,interval_pct,median_interval',pays),
        ('tail','trial,profile,seeds,median_daily_growth,min_daily_growth,flat_days,seeds_at_goal,peak_ge_124',tails),
        ('p-loss','trial,profile,seed,matched_days,worst_loss_digits,worst_day,days_over_1',loss),
        ('ex','trial,pattern,profile,day,checkpoint,seeds,one_gain,full_gain,full_minus_one,seeds_behind_one,monotone,one_gate,full_gate',ex),
        ('turbo','trial,pattern,day,checkpoint,seeds,max_full_free_minus_turbo,free_above_turbo',turbo),
    ):
        write(args.output,name,header,rows)


if __name__ == '__main__':
    main()
