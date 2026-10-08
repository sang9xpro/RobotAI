#!/usr/bin/env python3
"""Project-owned Android Emulator runtime, AVD and test operations."""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
LOCAL = ROOT / '.local/android-emulator'
SDK = LOCAL / 'sdk'
USER = LOCAL / 'user'
AVDS = LOCAL / 'avd'
NAME = 'RobotAI_API36_1'
PORT = 5556
SERIAL = f'emulator-{PORT}'
IMAGE = Path('system-images/android-36.1/google_apis_playstore/arm64-v8a')
ADB = SDK / 'platform-tools/adb'
EMULATOR = SDK / 'emulator/emulator'


def environment():
    return dict(os.environ, ANDROID_HOME=str(SDK), ANDROID_USER_HOME=str(USER),
                ANDROID_EMULATOR_HOME=str(USER), ANDROID_AVD_HOME=str(AVDS))


def adb(*args, check=True, timeout=30):
    return subprocess.run([str(ADB), '-s', SERIAL, *args], env=environment(),
                          text=True, capture_output=True, check=check, timeout=timeout)


def owns_device():
    result = adb('emu', 'avd', 'name', check=False, timeout=5)
    return result.returncode == 0 and NAME in result.stdout.splitlines()


def setup(source):
    for relative in (Path('emulator'), Path('platform-tools'), IMAGE, Path('licenses')):
        if not (source / relative).is_dir():
            raise SystemExit(f'Missing SDK package: {relative}')
        target = SDK / relative
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            print(f'Copying {relative}', flush=True)
            shutil.copytree(source / relative, target, symlinks=True)
    USER.mkdir(parents=True, exist_ok=True)
    AVDS.mkdir(parents=True, exist_ok=True)
    avd = AVDS / (NAME + '.avd')
    avd.mkdir(exist_ok=True)
    # Never rewrite the configuration of an existing AVD or discard its data.
    if not (avd / 'config.ini').exists():
        fields = {
            'AvdId': NAME, 'avd.ini.displayname': 'RobotAI Android 16',
            'avd.ini.encoding': 'UTF-8', 'abi.type': 'arm64-v8a',
            'hw.cpu.arch': 'arm64', 'hw.cpu.ncore': '4', 'hw.ramSize': '2048',
            'hw.gpu.enabled': 'yes', 'hw.gpu.mode': 'auto',
            'hw.lcd.width': '1080', 'hw.lcd.height': '1920', 'hw.lcd.density': '420',
            'hw.keyboard': 'yes', 'hw.audioInput': 'yes', 'hw.audioOutput': 'yes',
            'hw.mainKeys': 'no', 'hw.sensors.orientation': 'yes',
            'hw.camera.back': 'emulated', 'hw.camera.front': 'emulated',
            'disk.dataPartition.size': '4G', 'vm.heapSize': '256',
            'image.sysdir.1': str(IMAGE) + '/', 'tag.id': 'google_apis_playstore',
            'tag.display': 'Google Play', 'PlayStore.enabled': 'true',
            'fastboot.forceColdBoot': 'yes', 'showDeviceFrame': 'no',
        }
        (avd / 'config.ini').write_text('\n'.join(f'{key}={value}' for key, value in fields.items()) + '\n')
    (AVDS / (NAME + '.ini')).write_text(f'avd.ini.encoding=UTF-8\npath={avd}\ntarget=android-36.1\n')
    print('Project emulator setup complete:', LOCAL)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['setup', 'start', 'status', 'stop', 'install', 'test-login', 'test-mock', 'test-dock'])
    parser.add_argument('--source-sdk', type=Path, default=Path('/Volumes/SSD_Sang/Library/Android/sdk'))
    parser.add_argument('--headless', action='store_true')
    args = parser.parse_args()
    if args.action == 'setup':
        setup(args.source_sdk)
        sys.exit(0)
    if not EMULATOR.is_file():
        raise SystemExit('Run setup first.')
    if args.action == 'start':
        for port in (PORT, PORT + 1):
            with socket.socket() as probe:
                if probe.connect_ex(('127.0.0.1', port)) == 0:
                    raise SystemExit(f'Emulator port {port} occupied; refusing to replace another instance.')
        flags = ['-avd', NAME, '-port', str(PORT), '-no-snapshot', '-no-boot-anim']
        if args.headless:
            flags += ['-no-window']
        with (LOCAL / 'emulator.log').open('ab') as log:
            process = subprocess.Popen([str(EMULATOR), *flags], env=environment(), cwd=LOCAL,
                          stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        (LOCAL / 'emulator.pid').write_text(str(process.pid))
        print(f'Emulator starting: {SERIAL}, PID {process.pid}; use status until boot_completed=1.')
    elif args.action == 'status':
        owned = owns_device()
        boot = adb('shell', 'getprop', 'sys.boot_completed').stdout.strip() if owned else '0'
        print(json.dumps(dict(serial=SERIAL, avd=NAME, owned=owned, boot_completed=boot)))
        sys.exit(0 if owned and boot == '1' else 1)
    else:
        if not owns_device():
            raise SystemExit('Project AVD is not running; refusing to affect another device.')
        if args.action == 'stop':
            print(adb('emu', 'kill').stdout.strip())
        elif args.action == 'install':
            apk = ROOT / 'android-app/robot-app/app/build/outputs/apk/debug/app-debug.apk'
            print(adb('install', '-r', str(apk), timeout=120).stdout.strip())
            adb('shell', 'pm', 'grant', 'com.robotai.robot', 'android.permission.RECORD_AUDIO')
            for port in (8091, 8092):
                adb('reverse', f'tcp:{port}', f'tcp:{port}')
            print(adb('shell', 'am', 'start', '-n', 'com.robotai.robot/.MainActivity').stdout.strip())
        else:
            if args.action == 'test-dock':
                app_apk = ROOT / 'android-app/robot-app/app/build/outputs/apk/debug/app-debug.apk'
                print(adb('install', '-r', str(app_apk), timeout=120).stdout.strip())
            ports = (8891, 8892) if args.action in ('test-login', 'test-dock') else (8766,)
            for port in ports:
                adb('reverse', f'tcp:{port}', f'tcp:{port}')
            apk = ROOT / 'android-app/robot-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
            print(adb('install', '-r', str(apk), timeout=120).stdout.strip())
            test = {'test-login': 'LoginFlowTest', 'test-dock': 'WirelessDockFlowTest', 'test-mock': 'RobotSmokeTest'}[args.action]
            result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                         'com.robotai.robot.' + test, 'com.robotai.robot.test/androidx.test.runner.AndroidJUnitRunner',
                         check=False, timeout=180)
            (LOCAL / (args.action + '.log')).write_text(result.stdout + result.stderr)
            print(result.stdout[-2000:])
            sys.exit(0 if result.returncode == 0 and 'OK (' in result.stdout else 1)
