"""Join EX precheck shards in the same order as the single-machine run."""
import argparse
import csv
import hashlib
import json
from pathlib import Path


PROFILES = ("Free", "Ads", "Pass", "Spend30", "Spend100", "PassVisible")
SCENARIOS = ("none", "full", "one", "one_set", "spread_three", "two_sets", "staged", "offline_cap")
TABLES = ("summary", "checkpoints", "prestige", "samples", "conditions")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("shards", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    shards = sorted(path.parent for path in args.shards.glob("*/manifest.json"))
    if not shards:
        parser.error("No shard manifests found")
    manifests = [json.loads((shard / "manifest.json").read_text()) for shard in shards]
    keys = ("revision", "source_sha256", "runner_sha256", "through_day", "cached")
    common = {key: manifests[0][key] for key in (*keys, "integrated_patch_sha256") if key in manifests[0]}
    if any(any(manifest.get(key) != common.get(key) for key in (*keys, "integrated_patch_sha256")) for manifest in manifests):
        parser.error("The shards disagree about the source files, day coverage or arithmetic")
    args.output.mkdir(parents=True, exist_ok=True)
    expected = {(scenario, seed, profile) for scenario in SCENARIOS for seed in range(1, 31)
                for profile in PROFILES}
    row_counts = {}
    for table in TABLES:
        by_case = {}
        fields = None
        for shard in shards:
            with (shard / f"{table}.csv").open() as stream:
                reader = csv.DictReader(stream)
                if fields is None:
                    fields = reader.fieldnames
                if fields != reader.fieldnames:
                    parser.error(f"Unexpected header in {shard / table}")
                local = {}
                for row in reader:
                    key = (row["scenario"], int(row["seed"]), row["profile"])
                    local.setdefault(key, []).append(row)
                if local.keys() & by_case.keys() or local.keys() - expected:
                    parser.error(f"Duplicate or unexpected cases in {shard / table}")
                by_case.update(local)
        if table in ("summary", "checkpoints") and by_case.keys() != expected:
            parser.error(f"{table} has {len(by_case)} of {len(expected)} cases")
        counts = {"summary": 1, "checkpoints": 4, "samples": 12, "conditions": 108}
        if table in counts and any(len(rows) != counts[table] for rows in by_case.values()):
            parser.error(f"Unexpected number of {table} rows per case")
        if table in ("samples", "conditions") and by_case.keys() != {key for key in expected if key[0] == "full"}:
            parser.error(f"{table} must cover only the full collection")
        with (args.output / f"{table}.csv").open("w") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
            writer.writeheader()
            for scenario in SCENARIOS:
                for seed in range(1, 31):
                    for profile in PROFILES:
                        writer.writerows(by_case.get((scenario, seed, profile), ()))
        row_counts[table] = sum(map(len, by_case.values()))
    common.update({
        "seeds": list(range(1, 31)),
        "profiles": PROFILES,
        "scenarios": SCENARIOS,
        "rows": row_counts,
        "csv_sha256": {table: hashlib.sha256((args.output / f"{table}.csv").read_bytes()).hexdigest()
                       for table in TABLES},
    })
    (args.output / "manifest.json").write_text(json.dumps(common, indent=2, sort_keys=True) + "\n")


if __name__ == "__main__":
    main()
