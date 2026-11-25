"""Run the same JVM simulation with a trial swipe income bonus in an isolated source copy."""
import argparse
import shutil
import subprocess
from pathlib import Path
from tempfile import TemporaryDirectory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--bonus-permille", type=int, default=30, choices=range(31), metavar="0–30")
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
        adjustment = (f"        earned = earned * BigInteger.valueOf({1000 + opts.bonus_permille}L)"
                      " / BigInteger.valueOf(1000L)\n")
        engine.write_text(source.replace(anchor, adjustment + anchor))
        subprocess.run([str(repo / "gradlew"), "-p", str(project), "run",
                        f'--args=balance-report "{output}"'], check=True)


if __name__ == "__main__":
    main()
