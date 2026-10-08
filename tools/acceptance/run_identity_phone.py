#!/usr/bin/env python3
"""Run isolated identity platform tests on the explicitly selected OPPO."""
import argparse, subprocess, time, re, xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
ADB=Path('/Volumes/SSD_Sang/Library/Android/sdk/platform-tools/adb')
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--skip-install',action='store_true');a=p.parse_args()
assert re.fullmatch(r'[A-Za-z0-9_.:-]+',a.serial)
out=ROOT/'.local/identity-test';out.mkdir(parents=True,exist_ok=True)
def run(*args,timeout=30):
 return subprocess.run([str(ADB),'-s',a.serial,*args],capture_output=True,text=True,timeout=timeout)
model=run('shell','getprop','ro.product.model').stdout.strip()
assert model=='CPH2651',f'Refusing unexpected device model: {model}'
def install(path):
 process=subprocess.Popen([str(ADB),'-s',a.serial,'install','-r',str(path)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
 deadline=time.monotonic()+150
 while process.poll() is None and time.monotonic()<deadline:
  time.sleep(2)
  if process.poll() is not None:break
  run('shell','uiautomator','dump','/data/local/tmp/robotai-install-ui.xml',timeout=15)
  xml=run('shell','cat','/data/local/tmp/robotai-install-ui.xml').stdout
  try:
   root=ET.fromstring(xml)
   names=[n.get('text','') for n in root.iter('node') if n.get('resource-id')=='com.oplus.stdsp:id/app_name']
   if names and names[0] in ('RobotAI','RobotAI P2','com.robotai.robot.test'):
    for n in root.iter('node'):
     if n.get('resource-id')=='com.oplus.stdsp:id/btn_next' and n.get('enabled')=='true':
      b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
      if len(b)==4:run('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
  except ET.ParseError:pass
 if process.poll() is None:process.terminate();raise RuntimeError('OPPO installation confirmation did not finish')
 stdout,stderr=process.communicate()
 if process.returncode:raise RuntimeError(stdout+stderr)
 print('Installed:',path.name,flush=True)
if not a.skip_install:
 install(ROOT/'android-app/robot-app/app/build/outputs/apk/debug/app-debug.apk')
 install(ROOT/'android-app/robot-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
for permission in ('CAMERA','RECORD_AUDIO'):
 result=run('shell','pm','grant','com.robotai.robot','android.permission.'+permission)
 if result.returncode:print('Runtime permission will use the Android dialog:',permission,flush=True)
with (out/'oppo-instrumentation.log').open('w') as log:
 result=subprocess.run([str(ADB),'-s',a.serial,'shell','am','instrument','-w','-r','-e','class',
  'com.robotai.robot.IdentityPlatformTest','com.robotai.robot.test/androidx.test.runner.AndroidJUnitRunner'],stdout=log,stderr=subprocess.STDOUT,timeout=180)
text=(out/'oppo-instrumentation.log').read_text();print(text[-4500:])
metrics=run('shell','run-as','com.robotai.robot','cat','files/identity-microphone-result.json')
if metrics.returncode==0:(out/'oppo-microphone-result.json').write_text(metrics.stdout)
if result.returncode or 'OK (' not in text:raise SystemExit(1)
