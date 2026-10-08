#!/usr/bin/env python3
"""Update RobotAI on the selected device, handling its observed OPPO installer screen."""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADB = "/Volumes/SSD_Sang/Library/Android/sdk/platform-tools/adb"
p = argparse.ArgumentParser()
p.add_argument("--serial", required=True)
p.add_argument("--test", action="store_true", help="Install the instrumentation APK instead of the app")
args = p.parse_args()
assert re.fullmatch(r"[A-Za-z0-9_.:-]+", args.serial)
apk = ROOT / ("android-app/robot-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
              if args.test else "android-app/robot-app/app/build/outputs/apk/debug/app-debug.apk")
installer_name = "com.robotai.robot.test" if args.test else "RobotAI"
assert apk.is_file()

def adb(*command):
    return subprocess.run([ADB, "-s", args.serial, *command], capture_output=True, text=True, timeout=15)

process = subprocess.Popen([ADB, "-s", args.serial, "install", "-r", str(apk)], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
deadline = time.monotonic() + 150
while process.poll() is None and time.monotonic() < deadline:
    time.sleep(2)
    if process.poll() is not None:
        break
    try:
        adb("shell", "uiautomator", "dump", "/data/local/tmp/robotai-install-ui.xml")
        root = ET.fromstring(adb("shell", "cat", "/data/local/tmp/robotai-install-ui.xml").stdout)
        names = [n.get("text") for n in root.iter("node") if n.get("resource-id") == "com.oplus.stdsp:id/app_name"]
        if names != [installer_name]:
            continue
        for node in root.iter("node"):
            if node.get("resource-id") == "com.oplus.stdsp:id/btn_next" and node.get("enabled") == "true":
                bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
                if len(bounds) == 4:
                    adb("shell", "input", "tap", str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))
    except (ET.ParseError, subprocess.TimeoutExpired):
        pass
if process.poll() is None:
    process.terminate()
    process.communicate(timeout=10)
    raise SystemExit("RobotAI installation did not finish; inspect the phone installer.")
stdout, stderr = process.communicate()
if process.returncode:
    raise SystemExit(stdout + stderr)
print("RobotAI test APK installed." if args.test else "RobotAI APK installed; existing app data preserved.")
if not args.test:
    result = adb("shell", "am", "start", "-n", "com.robotai.robot/.MainActivity")
    if result.returncode:
        raise SystemExit(result.stderr)
