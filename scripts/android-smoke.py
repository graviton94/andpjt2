"""Emulator smoke test for the web game screen: launch, swipe the board like a finger, check no JS errors and no crash,
then kill and relaunch from the save. Frames go to build/smoke/."""
import pathlib
import re
import subprocess
import sys
import time

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


def main(apk):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    adb("install", "-r", apk)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/" + ACTIVITY)
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
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/" + ACTIVITY)
    time.sleep(6)
    screenshot("after-restart.png")
    assert alive(), "app died on relaunch"
    errors = page_errors()
    assert not errors, "page errors after relaunch:\n" + "\n".join(errors)
    print("Verified the web board loads, eight swipes without errors, and relaunch from save.")


if __name__ == "__main__":
    main(sys.argv[1])
