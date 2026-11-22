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


def adb(*args, **options):
    return subprocess.run(("adb", *args), check=True, **options)


def wait_node(label):
    deadline = time.monotonic() + 35
    while time.monotonic() < deadline:
        adb("shell", "uiautomator", "dump", REMOTE_DUMP, stdout=subprocess.DEVNULL)
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


def swipe_and_record():
    _, y1, _, y2 = wait_node("문앞왔")
    dimensions = adb("shell", "wm", "size", stdout=subprocess.PIPE, text=True).stdout
    width = int(re.findall(r"(\d+)x\d+", dimensions)[-1])
    y = str((y1 + y2) // 2)
    with subprocess.Popen(
        ("adb", "shell", "screenrecord", "--time-limit", "7", "--size", "540x1140", REMOTE_RECORDING)
    ) as recording:
        time.sleep(.9)
        adb("shell", "input", "swipe", str(width * 2 // 3), y, str(width // 6), y, "320")
        if recording.wait(timeout=25) != 0:
            raise RuntimeError("Android screen recording failed")
    wait_node("11.7만 P")
    wait_node("레벨 2")
    screenshot("after-first-chain.png")
    adb("pull", REMOTE_RECORDING, str(OUTPUT / "first-chain.mp4"))


def main(apk):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "settings", "put", "global", "animator_duration_scale", "1")
    adb("install", "-r", apk)
    start()
    wait_node("알림 센터")
    x1, y1, x2, y2 = wait_node("블루투스")
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    wait_node("블루투스 켜짐")
    screenshot("notification-shade.png")

    swipe_and_record()
    adb("shell", "am", "force-stop", PACKAGE)
    start()
    wait_node("11.7만 P")
    wait_node("블루투스 켜짐")
    print("Verified quick settings, first seven-hit overload, and persisted points on Android.")


if __name__ == "__main__":
    main(sys.argv[1])
