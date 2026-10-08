#!/usr/bin/env python3
"""Run real voice acceptance on an explicitly selected physical Android device."""
import argparse
import json
import subprocess
import shlex
import time
import re
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--turns', type=int, default=30)
parser.add_argument('--test', choices=['voice','handsfree','features','reminders'], default='voice')
parser.add_argument('--skip-install', action='store_true', help='Use APKs already installed for this build')
parser.add_argument('--skip-app-install', action='store_true', help='Install only the updated instrumentation APK')
parser.add_argument('--audio-source', choices=['mac-speaker','phone-speaker'], default='mac-speaker',
                    help='Acoustic input source; both paths still record the physical microphone')
args = parser.parse_args()
assert 1 <= args.turns <= 100
assert re.fullmatch(r'[A-Za-z0-9_.:-]+',args.serial)
adb = ROOT / '.local/android-emulator/sdk/platform-tools/adb'
out = ROOT / '.local/acceptance'
out.mkdir(exist_ok=True)

# Startup is asynchronous. Probe the authenticated route without credentials.
fixture=json.loads((out/'accounts.json').read_text())
ready=False
deadline=time.monotonic()+60
while time.monotonic()<deadline:
    try:
        with urllib.request.urlopen(fixture['api']+'/api/user/check-token',timeout=2): ready=True
    except urllib.error.HTTPError as error:
        ready=error.code==401
    except (urllib.error.URLError,TimeoutError,OSError): pass
    if ready: break
    time.sleep(.5)
if not ready: raise RuntimeError('Backend did not become ready within 60 seconds')

def run(*command, input=None, timeout=60, check=True):
    result = subprocess.run([str(adb), '-s', args.serial, *command], input=input,
                            capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError('ADB operation failed: ' + str(command[:3]))
    return result

def install(apk):
    process = subprocess.Popen([str(adb), '-s', args.serial, 'install', '-r', str(apk)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    deadline = time.monotonic() + 150
    while process.poll() is None and time.monotonic() < deadline:
        time.sleep(2)
        if process.poll() is not None: break
        run('shell', 'uiautomator', 'dump', '/data/local/tmp/robotai-install-ui.xml', check=False, timeout=15)
        xml = run('shell', 'cat', '/data/local/tmp/robotai-install-ui.xml', check=False).stdout
        try:
            root = ET.fromstring(xml)
            names = [n.get('text','') for n in root.iter('node') if n.get('resource-id') == 'com.oplus.stdsp:id/app_name']
            if names and names[0] in ('RobotAI P2', 'RobotAI', 'com.robotai.robot.test'):
                buttons = [n for n in root.iter('node') if n.get('resource-id') == 'com.oplus.stdsp:id/btn_next' and n.get('enabled') == 'true']
                for button in buttons:
                    coords = list(map(int, re.findall(r'\d+', button.get('bounds',''))))
                    if len(coords) == 4:
                        run('shell','input','tap',str((coords[0]+coords[2])//2),str((coords[1]+coords[3])//2))
        except ET.ParseError: pass
    if process.poll() is None:
        process.terminate(); raise RuntimeError('Installation did not finish within 150 seconds')
    stdout, stderr = process.communicate()
    if process.returncode: raise RuntimeError('APK installation failed: ' + stdout.decode().strip() + stderr.decode().strip())
    print(stdout.decode().strip(), flush=True)

run('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
run('shell', 'wm', 'dismiss-keyguard', check=False)
if not args.skip_install:
    apks=['app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']
    if not args.skip_app_install: apks.insert(0,'app/build/outputs/apk/debug/app-debug.apk')
    for apk in apks:
        install(ROOT / 'android-app/robot-app' / apk)
for permission in ['RECORD_AUDIO', 'CAMERA', 'POST_NOTIFICATIONS']:
    run('shell', 'pm', 'grant', 'com.robotai.robot', 'android.permission.' + permission, check=False)
for port in [8091, 8092]:
    run('reverse', 'tcp:' + str(port), 'tcp:' + str(port))
run('shell', 'run-as', 'com.robotai.robot', 'mkdir', '-p', 'files')
run('shell', 'run-as', 'com.robotai.robot', 'rm', '-f', 'files/acceptance-result.json', check=False)
for source, destination in [(out / 'accounts.json', 'acceptance.json'),
                            (ROOT / '.local/phowhisper-smoke.wav', 'acceptance.wav')]:
    run('shell', 'run-as com.robotai.robot sh -c ' + shlex.quote('cat > files/' + destination), input=source.read_bytes())
run('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
if args.test=='reminders':
    prepare=run('shell','am','instrument','-w','-r','-e','class','com.robotai.robot.ReminderRestartTest#prepare','com.robotai.robot.test/androidx.test.runner.AndroidJUnitRunner',timeout=60)
    if b'OK (' not in prepare.stdout: raise RuntimeError('Reminder preparation failed')
    run('shell','am','force-stop','com.robotai.robot')
testclass={'voice':'RealVoiceAcceptanceTest','handsfree':'HandsFreeAcceptanceTest','features':'CompanionFeatureTest','reminders':'ReminderRestartTest#verifyAfterForceStop'}[args.test]
process = subprocess.Popen([str(adb), '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r',
    '-e', 'class', 'com.robotai.robot.' + testclass, '-e', 'turns', str(args.turns),
    '-e', 'externalAudio', str(args.audio_source=='mac-speaker').lower(),
    'com.robotai.robot.test/androidx.test.runner.AndroidJUnitRunner'],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
with (out / 'phone-instrumentation.log').open('w') as log:
    for line in process.stdout:
        log.write(line)
        log.flush()
        if 'PLAY_INPUT' in line:
            player=ROOT / '.local/acceptance-speaker'
            if not player.exists(): raise RuntimeError('Compile tools/acceptance/Speaker.swift before acoustic acceptance.')
            request=re.search(r'PLAY_INPUT:([a-f0-9-]{36})',line)
            if request is None: raise RuntimeError('Missing acoustic playback request ID')
            played=subprocess.run([str(player), str(ROOT / '.local/phowhisper-smoke.wav')],capture_output=True,timeout=15)
            status=request.group(1)+(':ok' if played.returncode==0 else ':error')
            run('shell','run-as com.robotai.robot sh -c '+shlex.quote('cat > files/acceptance-audio.done'),input=status.encode())
        if any(word in line for word in ['Completed real turn', 'FAILURES', 'OK (', 'Error']):
            print(line.strip(), flush=True)
process.wait()
result = run('shell', 'run-as', 'com.robotai.robot', 'cat', 'files/acceptance-result.json', check=False)
report = {}
if result.returncode == 0:
    (out / 'phone-result.json').write_bytes(result.stdout)
    report = json.loads(result.stdout)
    report['deviceSerial']=args.serial;report['test']=args.test
    if args.test in ('voice','handsfree'): report['audioSource']=args.audio_source
    (out / 'phone-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    (out / f'{args.serial.replace(":","_")}-{args.test}-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps({key: report.get(key) for key in ['result', 'p50Ms', 'p95Ms', 'error']},
                     ensure_ascii=False), flush=True)
    print('Completed turns:', len(report.get('turns', [])), flush=True)
else:
    print('No acceptance result produced; inspect instrumentation log.', flush=True)
run('shell', 'run-as', 'com.robotai.robot', 'rm', '-f', 'files/acceptance.json', check=False)
instrumentation_passed='OK (' in (out/'phone-instrumentation.log').read_text()
(out/f'{args.serial.replace(":","_")}-{args.test}-instrumentation.log').write_text((out/'phone-instrumentation.log').read_text())
raise SystemExit(0 if process.returncode == 0 and instrumentation_passed and report.get('result') == 'passed' else 1)
