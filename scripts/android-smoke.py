"""Emulator smoke test for the web game screen: launch, swipe the board like a finger, check no JS errors and no crash,
then kill and relaunch from the save. Frames go to build/smoke/."""
import json
import pathlib
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

PACKAGE = "com.graviton94.notificationcleaner.playtest"
ACTIVITY = "com.graviton94.notificationcleaner.MainActivity"
OUTPUT = pathlib.Path("build/smoke")


def adb(*args, check=True, **options):
    return subprocess.run(("adb", *args), check=check, **options)


def screenshot(name):
    with (OUTPUT / name).open("wb") as f:
        adb("exec-out", "screencap", "-p", stdout=f)


def alive():
    return adb("shell", "pidof", PACKAGE, stdout=subprocess.PIPE, text=True, check=False).stdout.strip() != ""


def page_errors():
    log = adb("logcat", "-d", "-s", "GamePage:E", "AndroidRuntime:E", stdout=subprocess.PIPE, text=True, check=False).stdout
    return [l for l in log.splitlines() if " E " in l or "FATAL" in l]


def start(*options):
    result = adb("shell", "am", "start", "-W", "-n", PACKAGE + "/" + ACTIVITY, *options,
                 capture_output=True, text=True, timeout=30)
    assert "Error" not in result.stdout, result.stdout


def stop():
    adb("shell", "am", "force-stop", PACKAGE, timeout=15)


def wait_for(check, message, timeout=45):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if result := check():
            return result
        time.sleep(0.5)
    raise AssertionError(message)


def game_pid():
    return adb("shell", "pidof", PACKAGE, capture_output=True, text=True, check=False, timeout=5).stdout.strip().split()


def run_balance_phase(phase):
    pid = wait_for(game_pid, "WebView app process did not start")[0]
    port = adb("forward", "tcp:0", f"localabstract:webview_devtools_remote_{pid}",
               capture_output=True, text=True, timeout=10).stdout.strip()
    assert port.isdigit(), f"adb did not assign a port: {port!r}"
    endpoint = f"http://127.0.0.1:{port}"
    url = "https://appassets.androidplatform.net/assets/game/index.html?auto=0"

    def webview_ready():
        try:
            with urllib.request.urlopen(endpoint + "/json/list", timeout=2) as response:
                return any(target.get("url") == url for target in json.load(response))
        except (OSError, urllib.error.URLError, ValueError):
            return False

    try:
        wait_for(webview_ready, "Debug WebView did not expose the game page")
        completed = subprocess.run(("node", "scripts/android-balance-smoke.cjs", endpoint, phase, str(OUTPUT), PACKAGE),
                                   capture_output=True, text=True, timeout=90)
        print(completed.stdout, end="")
        if completed.returncode:
            print(completed.stderr, file=sys.stderr, end="")
            reason = completed.stderr.splitlines()[0] if completed.stderr else "Playwright failed"
            raise AssertionError(reason)
    finally:
        adb("forward", "--remove", f"tcp:{port}", check=False, timeout=10)


def balance_flow(fixture):
    stop()
    with pathlib.Path(fixture).open("rb") as source:
        adb("shell", "-T", "run-as", PACKAGE, "tee", "files/save.json", stdin=source,
            stdout=subprocess.DEVNULL, timeout=15)
    for phase in ("purchase", "reset", "verify"):
        start("--ez", "balance_smoke", "true")
        try:
            run_balance_phase(phase)
        except (AssertionError, subprocess.SubprocessError) as error:
            screenshot(f"failure-{phase}.png")
            message = str(error).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
            print(f"::error title=WebView balance {phase}::{message}")
            raise
        stop()


def main(apk, fixture=None):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    wait_for(lambda: adb("shell", "getprop", "sys.boot_completed", capture_output=True,
                         text=True, timeout=5).stdout.strip() == "1", "Android never finished booting")
    adb("install", "-r", apk, timeout=90)
    adb("logcat", "-c")
    start()
    time.sleep(8)
    screenshot("board.png")
    w, h = map(int, re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size", stdout=subprocess.PIPE, text=True).stdout)[-1])
    board_h = max(740, round(390 * h / w))
    row0 = 176 + round((board_h - 740) * .35)
    scale = w / 390
    for i in range(8):
        y = int((row0 + (2 + i % 4) * 72 + 30) * scale)
        adb("shell", "input", "swipe", str(int(300 * scale)), str(y), str(int(60 * scale)), str(y), "180")
        time.sleep(0.6 if i % 2 else 2.2)
        if i == 1:
            screenshot("after-swipe.png")
    time.sleep(3)
    screenshot("after-swipes.png")
    assert alive(), "app died during swipes"
    errors = page_errors()
    assert not errors, "page errors:\n" + "\n".join(errors)
    stop()
    start()
    time.sleep(6)
    screenshot("after-restart.png")
    assert alive(), "app died on relaunch"
    errors = page_errors()
    assert not errors, "page errors after relaunch:\n" + "\n".join(errors)
    print("Verified the web board loads, eight swipes without errors, and relaunch from save.")
    if fixture:
        balance_flow(fixture)
        errors = page_errors()
        assert not errors, "page errors during balance flow:\n" + "\n".join(errors)


if __name__ == "__main__":
    main(*sys.argv[1:])
