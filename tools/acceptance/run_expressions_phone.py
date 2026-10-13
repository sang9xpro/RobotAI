#!/usr/bin/env python3
"""Verify local expression models and shared Ken rendering on an explicitly selected device."""
import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADB = "/Volumes/SSD_Sang/Library/Android/sdk/platform-tools/adb"
parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("--skip-install", action="store_true")
parser.add_argument("--with-regressions", action="store_true")
args = parser.parse_args()
if not re.fullmatch(r"[A-Za-z0-9_.:-]+", args.serial):
    parser.error("Invalid device serial")
out = ROOT / ".local" / "expression-test" / args.serial
out.mkdir(parents=True, exist_ok=True)

def adb(*command, timeout=30):
    return subprocess.run([ADB, "-s", args.serial, *command], text=True, capture_output=True, timeout=timeout)

state = adb("get-state")
if state.returncode or state.stdout.strip() != "device":
    raise SystemExit("Selected device is not connected and authorized")
if not args.skip_install:
    for test in (False, True):
        command = [sys.executable, str(ROOT / "tools/acceptance/install_robot_apk.py"), "--serial", args.serial]
        if test:
            command.append("--test")
        subprocess.run(command, cwd=ROOT, check=True)

classes = ["com.robotai.robot.FacialExpressionPlatformTest"]
if args.with_regressions:
    classes += [
        "com.robotai.robot.KenAnimationPlatformTest",
        "com.robotai.robot.GestureInteractionPlatformTest",
        "com.robotai.robot.PersonTrackingPlatformTest#realFaceModelEnrollsReloadsAndKeepsBodyWhenFaceResultsDisappear",
        "com.robotai.robot.IdentityPlatformTest#nativeFaceAndVoiceRuntimesCoexist",
    ]
print("Running native expression tests on", args.serial, flush=True)
result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class", ",".join(classes),
             "com.robotai.robot.test/androidx.test.runner.AndroidJUnitRunner", timeout=300)
output = result.stdout + result.stderr
(out / ("instrumentation-with-regressions.log" if args.with_regressions else "instrumentation.log")).write_text(output)
print(output[-10000:], flush=True)
metrics = adb("shell", "run-as", "com.robotai.robot", "cat", "files/facial-expression-model-result.json")
if metrics.returncode == 0:
    (out / "model-result.json").write_text(metrics.stdout)
    print("Model metrics:", metrics.stdout, flush=True)
for name in ("expression-ken-smile.png", "expression-dock-smile.png"):
    capture = subprocess.run([ADB, "-s", args.serial, "exec-out", "run-as", "com.robotai.robot", "cat",
                              "files/expression-tests/" + name], capture_output=True, timeout=30)
    if capture.returncode == 0 and capture.stdout.startswith(b"\x89PNG"):
        (out / name).write_bytes(capture.stdout)
if result.returncode or "OK (" not in output or "FAILURES!!!" in output:
    raise SystemExit(1)
print("Expression verification passed; fixtures do not measure real-user accuracy.")
