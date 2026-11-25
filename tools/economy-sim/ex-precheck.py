"""Run the EX approval matrix on an isolated copy of the Kotlin core."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from tempfile import TemporaryDirectory


def replace(source, anchor, updated):
    if source.count(anchor) != 1:
        raise RuntimeError(f"Expected one shared-core anchor: {anchor}")
    return source.replace(anchor, updated)


def instrument(core):
    engine = core / "Engine.kt"
    source = engine.read_text()
    anchor = "        val room = jackpotRoom(s, rules.swipeLevelCap)\n"
    source = replace(source, anchor, anchor + (
        "        earned = ExExperiment.adjust(earned, room, s, now, actor, chain, overload, beats, rules.resetLevel)\n"
    ))
    cap = "(rules.step(rules.offlineCapSteps, s.level) + s.backup.lv(BackupItem.Offline) + s.backup.cloud.lv(CloudItem.CloudOffline)) * 3_600_000L"
    engine.write_text(replace(source, cap, f"ExExperiment.offlineCap({cap})"))


def cache_rules(core):
    """Memoize pure quotes and thresholds. Keep the engine's arithmetic and ordering."""
    rules = core / "Rules.kt"
    source = rules.read_text()
    for name, argument, key_type, result in (
        ("appPrice", "app: AppDef", "AppDef", "app"),
        ("upgradePrice", "app: AppDef, level: Int", "Pair<AppDef, Int>", "app to level"),
        ("proPrice", "appId: String", "String", "appId"),
        ("cardPrice", "card: EventCard", "EventCard", "card"),
        ("assistPrice", "a: Assist, owned: Int", "Pair<Assist, Int>", "a to owned"),
    ):
        values = ", ".join(part.split(":")[0] for part in argument.split(", "))
        signature = f"fun {name}({argument}): BigInteger"
        source = replace(source, signature, (
            f"private val audit_{name} = java.util.concurrent.ConcurrentHashMap<{key_type}, BigInteger>()\n"
            f"    {signature} = audit_{name}.computeIfAbsent({result}) {{ raw_{name}({values}) }}\n\n"
            f"    private fun raw_{name}({argument}): BigInteger"
        ))
    old_level = """        val boosted = runEarned * big(10).pow(fastBoot) / big(9).pow(fastBoot)
        var l = 1
        while (l + 1 < levelTable.size && boosted >= levelTable[l + 1]) l++
        return l"""
    source = replace(source, old_level, """        val ceilings = auditCeils.computeIfAbsent(fastBoot) { boot ->
            Array(levelTable.size) { rawLevelCeil(it, boot) }
        }
        var low = 1
        var high = ceilings.lastIndex
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (runEarned >= ceilings[middle]) low = middle else high = middle - 1
        }
        return low""")
    source = replace(source, "fun levelCeil(level: Int, fastBoot: Int): BigInteger =", (
        "private val auditCeils = java.util.concurrent.ConcurrentHashMap<Int, Array<BigInteger>>()\n"
        "    fun levelCeil(level: Int, fastBoot: Int): BigInteger = auditCeils.computeIfAbsent(fastBoot) { boot ->\n"
        "        Array(levelTable.size) { rawLevelCeil(it, boot) }\n"
        "    }[level.coerceIn(1, levelTable.lastIndex)]\n\n"
        "    private fun rawLevelCeil(level: Int, fastBoot: Int): BigInteger ="
    ))
    base = "fun baseValue(app: AppDef, level: Int, backup: Backup): Pair<BigInteger, BigInteger>"
    source = replace(source, base, """private class AuditBaseCache {
        var backupItems: Map<BackupItem, Int>? = null
        var cloudItems: Map<CloudItem, Int>? = null
        var lifetime = -1L
        val values = HashMap<Long, Pair<BigInteger, BigInteger>>()
    }
    private val auditBases = ThreadLocal.withInitial { AuditBaseCache() }
    fun baseValue(app: AppDef, level: Int, backup: Backup): Pair<BigInteger, BigInteger> {
        val cache = auditBases.get()
        if (cache.backupItems !== backup.items || cache.cloudItems !== backup.cloud.items || cache.lifetime != backup.lifetime) {
            cache.values.clear()
            cache.backupItems = backup.items
            cache.cloudItems = backup.cloud.items
            cache.lifetime = backup.lifetime
        }
        val key = (app.tier.toLong() shl 32) or (level.toLong() and 0xffffffffL)
        return cache.values.getOrPut(key) { rawBaseValue(app, level, backup) }
    }

    private """ + base.replace("fun baseValue", "fun rawBaseValue"))
    rules.write_text(source)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--seeds", type=int, default=30, help="Use consecutive seeds from 1")
    parser.add_argument("--jobs", type=int, default=4)
    parser.add_argument("--profiles", default="all")
    parser.add_argument("--cases", default="all")
    parser.add_argument("--through-day", type=int, choices=(0, 56), default=56)
    parser.add_argument("--uncached", action="store_true", help="Run the unmodified core arithmetic without memoization")
    args = parser.parse_args()
    sim = Path(__file__).resolve().parent
    repo = sim.parents[1]
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ)
    for key in ("SIM_RULES", "SIM_TRACE", "SIM_SEED", "SIM_RESETS"):
        env.pop(key, None)
    digest = hashlib.sha256()
    for root in (sim / "src", sim / "precheck", repo / "core/src/main/kotlin"):
        for source in sorted(root.rglob("*.kt")):
            digest.update(source.relative_to(repo).as_posix().encode() + b"\0" + source.read_bytes() + b"\0")
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    manifest = {
        "revision": commit,
        "source_sha256": digest.hexdigest(),
        "runner_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "seeds": args.seeds,
        "profiles": args.profiles,
        "cases": args.cases,
        "through_day": args.through_day,
        "cached": not args.uncached,
    }
    manifest_path = out / "manifest.json"
    if manifest_path.exists() and json.loads(manifest_path.read_text()) != manifest:
        parser.error("The output directory contains checkpoints made with a different run configuration")
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")

    with TemporaryDirectory(prefix="ex-precheck-") as folder:
        scratch = Path(folder)
        project = scratch / "tools/economy-sim"
        project.mkdir(parents=True)
        for name in ("build.gradle.kts", "settings.gradle.kts"):
            shutil.copy2(sim / name, project / name)
        shutil.copytree(sim / "src", project / "src")
        core_sources = scratch / "core/src/main/kotlin"
        shutil.copytree(repo / "core/src/main/kotlin", core_sources)
        engine, = core_sources.rglob("Engine.kt")
        core = engine.parent
        shutil.copy2(sim / "precheck/ExExperiment.kt", core)
        shutil.copy2(sim / "precheck/ExPrecheckCli.kt", project / "src/main/kotlin")
        instrument(core)
        if not args.uncached:
            cache_rules(core)
        subprocess.run([str(repo / "gradlew"), "-p", str(project), "installDist"], check=True, env=env)
        java = str(Path(env["JAVA_HOME"]) / "bin/java") if env.get("JAVA_HOME") else "java"
        libs, = (project / "build/install").glob("*/lib")
        subprocess.run([java, "-Xmx4g", "-Dfile.encoding=UTF-8", "-cp", str(libs / "*"), "ExPrecheckCli",
                        str(out), str(args.seeds), str(args.jobs), args.profiles, args.cases, str(args.through_day)],
                       check=True, env=env)


if __name__ == "__main__":
    main()
