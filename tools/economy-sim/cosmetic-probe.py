"""Run the same JVM simulation with a trial swipe income bonus in an isolated source copy."""
import argparse
import shutil
import subprocess
from pathlib import Path
from tempfile import TemporaryDirectory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    bonus = parser.add_mutually_exclusive_group()
    bonus.add_argument("--bonus-permille", type=int, choices=range(31), metavar="0–30")
    bonus.add_argument("--ex-plan", choices=("free", "ex"),
                       help="Unlock +1%% at Lv10 and model no EX or day-one EX ownership (+2%%)")
    parser.add_argument("--profiles", default="all", help="Comma-separated simulator profiles")
    parser.add_argument("--available-rewards", action="store_true", help="Use only ad placements exposed by the screen")
    opts = parser.parse_args()
    output = opts.output.resolve()
    sim = Path(__file__).resolve().parent
    repo = sim.parents[1]
    with TemporaryDirectory(prefix="cosmetic-income-probe-") as folder:
        scratch = Path(folder)
        project = scratch / "tools/economy-sim"
        project.mkdir(parents=True)
        for name in ("build.gradle.kts", "settings.gradle.kts"):
            shutil.copy2(sim / name, project / name)
        shutil.copytree(sim / "src", project / "src")
        core = scratch / "core/src/main/kotlin"
        shutil.copytree(repo / "core/src/main/kotlin", core)
        engine, = core.rglob("Engine.kt")
        source = engine.read_text()
        anchor = "        // Jackpot cap: luck may jump a few levels, never the whole run."
        if source.count(anchor) != 1:
            raise RuntimeError("Could not locate the shared engine's final swipe credit")
        if opts.ex_plan:
            ex_income = 20 if opts.ex_plan == "ex" else 0
            # Highest preserved level carries the free unlock through both prestige layers.
            adjustment = (
                "        val collectionIncome = if (maxOf(s.level, s.backup.bestLevel) >= 10) 10L else 0L\n"
                f"        val cosmeticIncome = {ex_income}L + collectionIncome\n"
                "        if (cosmeticIncome > 0L) {\n"
                "            earned = earned * BigInteger.valueOf(1000L + cosmeticIncome) / BigInteger.valueOf(1000L)\n"
                "        }\n"
            )
        else:
            steady_bonus = opts.bonus_permille if opts.bonus_permille is not None else 30
            adjustment = (f"        earned = earned * BigInteger.valueOf({1000 + steady_bonus}L)"
                          " / BigInteger.valueOf(1000L)\n")
        engine.write_text(source.replace(anchor, adjustment + anchor))
        arguments = f'balance-report "{output}" "{opts.profiles}"'
        if opts.available_rewards:
            arguments += " available-rewards"
        subprocess.run([str(repo / "gradlew"), "-p", str(project), "run", f"--args={arguments}"], check=True)


if __name__ == "__main__":
    main()
