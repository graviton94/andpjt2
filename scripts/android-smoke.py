import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.graviton94.notificationcleaner.playtest"
ACTIVITY = "com.graviton94.notificationcleaner.MainActivity"
OUTPUT = pathlib.Path("build/smoke")
REMOTE_DUMP = "/sdcard/cleaner-smoke.xml"
REMOTE_RECORDING = "/sdcard/cleaner-first-chain.mp4"


def adb(*args, check=True, **options):
    return subprocess.run(("adb", *args), check=check, **options)


def wait_node(label):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        # The board animates constantly, so a dump can be killed waiting for idle: retry instead of failing.
        if adb("shell", "uiautomator", "dump", REMOTE_DUMP, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False).returncode != 0:
            time.sleep(0.5)
            continue
        xml = adb("exec-out", "cat", REMOTE_DUMP, stdout=subprocess.PIPE).stdout
        for node in ET.fromstring(xml).iter("node"):
            if label in (node.get("text"), node.get("content-desc")):
                return tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))
        time.sleep(0.5)
    raise AssertionError(f"Android screen did not show {label!r}")


def screenshot(filename):
    with (OUTPUT / filename).open("wb") as file:
        adb("exec-out", "screencap", "-p", stdout=file)


def start():
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/" + ACTIVITY)


def swipe_row(index, width):
    _, y1, _, y2 = wait_node(f"알림 줄 {index}")
    y = str((y1 + y2) // 2)
    adb("shell", "input", "swipe", str(width * 2 // 3), y, str(width // 8), y, "250")


def main(apk):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "wm", "size", "720x1520")
    adb("shell", "wm", "density", "294")
    adb("install", "-r", apk)
    start()
    wait_node("알림 청소부")
    screenshot("board.png")
    dimensions = adb("shell", "wm", "size", stdout=subprocess.PIPE, text=True).stdout
    width = int(re.findall(r"(\d+)x\d+", dimensions)[-1])
    with subprocess.Popen(
        ("adb", "shell", "screenrecord", "--time-limit", "12", "--size", "360x760", "--bit-rate", "1500000", REMOTE_RECORDING)
    ) as recording:
        time.sleep(.9)
        for _ in range(6):
            swipe_row(1, width)
            time.sleep(.8)
        recording.wait(timeout=40)
    screenshot("after-swipes.png")
    adb("pull", REMOTE_RECORDING, str(OUTPUT / "swipes.mp4"))
    pid = adb("shell", "pidof", PACKAGE, stdout=subprocess.PIPE, text=True, check=False).stdout.strip()
    assert pid, "app died during swipes"

    adb("shell", "am", "force-stop", PACKAGE)
    start()
    wait_node("알림 청소부")
    wait_node("알림 줄 1")
    screenshot("after-restart.png")
    print("Verified board, six swipes without a crash, and relaunch from save on Android.")


if __name__ == "__main__":
    main(sys.argv[1])
