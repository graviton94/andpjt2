"""Run the EX approval matrix on an isolated copy of the Kotlin core."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from tempfile import TemporaryDirectory


def replace(source, anchor, updated):
    if source.count(anchor) != 1:
        raise RuntimeError(f"Expected one shared-core anchor: {anchor}")
    return source.replace(anchor, updated)


def instrument(core, integrated):
    engine = core / "Engine.kt"
    source = engine.read_text()
    anchor = "        val room = jackpotRoom(s, rules.swipeLevelCap)\n"
    adjustment = (
        "val exBurstApps = if (ExExperiment.revisedConditions()) bursts.map { valueApp(s, it) } else emptyList()\n"
        "earned = ExExperiment.adjust(earned, room, s, now, actor, chain, overload, beats, rules.resetLevel,\n"
        "    removed.app.takeIf { removed.kind == RowKind.App }, exBurstApps)\n"
    )
    if integrated:
        source = replace(source, anchor, anchor + "        val neutralP = earned.min(room)\n" +
                         "        if (s.challengeStarsAtStart == null) {\n" +
                         "".join(f"            {line}\n" for line in adjustment.splitlines()) + "        }\n")
        rate = re.search(r"^            earnRate = (.+),$", source, re.MULTILINE)[1]
        neutral = rate.replace("s.earnRate", "(s.exNeutralRate ?: s.earnRate)").replace("earned", "neutralP")
        source = replace(source, f"            earnRate = {rate},\n",
                         f"            earnRate = {rate},\n            exNeutralRate = {neutral},\n")
        source = replace(source, "        val result = settleOfflineWithBonus(s, awayMs, now)\n", (
            "        val projected = ExExperiment.projectedOwnership(now)\n"
            "        val result = if (ExExperiment.current.get() == null) settleOfflineWithBonus(s, awayMs, now) else\n"
            "            settleOfflineWithBonus(s, awayMs, now, ExExperiment.completedSets(projected), projected != 0)\n"
        ))
    else:
        source = replace(source, anchor, anchor + "".join(f"        {line}\n" for line in adjustment.splitlines()))
        cap = "(rules.step(rules.offlineCapSteps, s.level) + s.backup.lv(BackupItem.Offline) + s.backup.cloud.lv(CloudItem.CloudOffline)) * 3_600_000L"
        source = replace(source, cap, f"ExExperiment.offlineCap({cap})")
    engine.write_text(source)


def cache_rules(core, integrated):
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
    if not integrated:
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
    if integrated:
        source = replace(source, "var lifetime = -1L", "var lifetime: BigInteger? = null")
    rules.write_text(source)


def protect_first_goal(daily, level):
    source = daily.read_text()
    source = replace(source, "            val gb = engine.cloudGbNow(s)\n", (
        "            val gb = engine.cloudGbNow(s)\n"
        f"            val allowPrestige = s.level < {level} || tl.level124Day != null\n"
    ))
    for expression in ("resets && gb > 0", "resets && s.level >= engine.rules.resetLevel"):
        source = replace(source, f"if ({expression}", f"if (allowPrestige && {expression}")
    daily.write_text(source)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--seeds", type=int, default=30, help="Use consecutive seeds from 1")
    parser.add_argument("--first-seed", type=int, default=1)
    parser.add_argument("--jobs", type=int, default=4)
    parser.add_argument("--profiles", default="all")
    parser.add_argument("--cases", default="all")
    parser.add_argument("--through-day", type=int, choices=(0, 56), default=56)
    parser.add_argument("--days", type=int, choices=(84, 168), default=84)
    parser.add_argument("--protect-level", type=int, choices=range(1, 124), metavar="LEVEL",
                        help="Diagnostic replay: postpone prestige at this level or above until first Lv124")
    parser.add_argument("--uncached", action="store_true", help="Run the unmodified core arithmetic without memoization")
    parser.add_argument("--integrated", action="store_true", help="Apply the reviewed candidate to the isolated engine copy")
    parser.add_argument("--audit-only", action="store_true", help="Run the integrated lifecycle fixture instead of the matrix")
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
        "first_seed": args.first_seed,
        "profiles": args.profiles,
        "cases": args.cases,
        "through_day": args.through_day,
        "protect_level": args.protect_level,
        "cached": not args.uncached,
    }
    candidate = sim / "precheck/integrated-core.patch"
    if args.integrated:
        manifest["integrated_patch_sha256"] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    if args.audit_only and not args.integrated:
        parser.error("--audit-only requires the integrated candidate")
    if args.audit_only:
        manifest["audit_only"] = True
    if args.days != 84:
        manifest["days"] = args.days
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
        if args.integrated:
            subprocess.run(["git", "apply", "--unidiff-zero", str(candidate)], cwd=scratch, check=True)
        engine, = core_sources.rglob("Engine.kt")
        core = engine.parent
        shutil.copy2(sim / "precheck/ExExperiment.kt", core)
        cli = project / "src/main/kotlin/ExPrecheckCli.kt"
        shutil.copy2(sim / "precheck/ExPrecheckCli.kt", cli)
        if args.integrated:
            text = cli.read_text().replace("else 0L\n", "else BigInteger.ZERO\n")
            for field in ("paidFiles", "paidGb"):
                text = text.replace(f"BigInteger.valueOf({field})", field).replace(f"{field} > 0", f"{field}.signum() > 0")
            cli.write_text(text)
        instrument(core, args.integrated)
        if args.protect_level is not None:
            protect_first_goal(project / "src/main/kotlin/Daily.kt", args.protect_level)
        if not args.uncached:
            cache_rules(core, args.integrated)
        subprocess.run([str(repo / "gradlew"), "-p", str(project), "installDist"], check=True, env=env)
        java = str(Path(env["JAVA_HOME"]) / "bin/java") if env.get("JAVA_HOME") else "java"
        libs, = (project / "build/install").glob("*/lib")
        command = [java, "-Xmx4g", "-Dfile.encoding=UTF-8", "-cp", str(libs / "*")]
        if args.audit_only:
            command += ["IntegratedAudit", str(out)]
        else:
            command += ["ExPrecheckCli", str(out), str(args.seeds), str(args.jobs), args.profiles, args.cases,
                        str(args.through_day), str(args.first_seed), str(args.integrated).lower(), str(args.days)]
        subprocess.run(command, check=True, env=env)


if __name__ == "__main__":
    main()
