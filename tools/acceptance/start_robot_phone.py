#!/usr/bin/env python3
"""Open/inspect/wake/connect RobotAI on an explicitly selected Android device."""
import argparse,subprocess,re,xml.etree.ElementTree as ET,json,time,shlex
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--action',choices=['inspect','wake','connect','local-login'],default='inspect');a=p.parse_args()
assert re.fullmatch(r'[A-Za-z0-9_.:-]+',a.serial)
adb='/Volumes/SSD_Sang/Library/Android/sdk/platform-tools/adb'
def run(*args):return subprocess.run([adb,'-s',a.serial,*args],capture_output=True,text=True,check=True,timeout=20).stdout
if a.action=='local-login':
 rootDir=Path(__file__).resolve().parents[2]
 apk=rootDir/'android-app/robot-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
 proc=subprocess.Popen([adb,'-s',a.serial,'install','-r',str(apk)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
 deadline=time.monotonic()+150
 while proc.poll() is None and time.monotonic()<deadline:
  time.sleep(2)
  if proc.poll() is not None:break
  try:
   run('shell','uiautomator','dump','/data/local/tmp/robotai-install-ui.xml')
   ui=ET.fromstring(run('shell','cat','/data/local/tmp/robotai-install-ui.xml'))
   names=[n.get('text','') for n in ui.iter('node') if n.get('resource-id')=='com.oplus.stdsp:id/app_name']
   if names and names[0]=='com.robotai.robot.test':
    for n in ui.iter('node'):
     if n.get('resource-id')=='com.oplus.stdsp:id/btn_next' and n.get('enabled')=='true':
      b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
      if len(b)==4:run('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
  except (ET.ParseError,subprocess.TimeoutExpired):pass
 if proc.poll() is None:proc.terminate();raise SystemExit('Installation did not complete')
 stdout,stderr=proc.communicate()
 if proc.returncode:raise SystemExit(stdout+stderr)
 accounts=json.loads((rootDir/'.local/acceptance/accounts.json').read_text())
 account=accounts['accounts'][0]
 assert account['username'] in ('ken_accept_a','ken_accept_b')
 fixture={'api':'http://127.0.0.1:8091','socket':'ws://127.0.0.1:8092/ws/xiaozhi/v1/',
  'account':{'username':account['username'],'password':account['password'],'roleId':account['roles'][0]}}
 command='run-as com.robotai.robot sh -c '+shlex.quote('cat > files/robot-start.json')
 subprocess.run([adb,'-s',a.serial,'shell',command],input=json.dumps(fixture),text=True,capture_output=True,check=True)
 result=subprocess.run([adb,'-s',a.serial,'shell','am','instrument','-w','-r','-e','class','com.robotai.robot.RobotStartTest',
  'com.robotai.robot.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,text=True,timeout=60)
 if result.returncode or 'OK (' not in result.stdout:
  print(result.stdout[-1500:]);raise SystemExit('Local login failed')
 run('shell','am','start','-n','com.robotai.robot/.MainActivity')
 print('Local test account signed in. Credentials file consumed and removed.')
 raise SystemExit(0)
run('shell','uiautomator','dump','/data/local/tmp/robotai-start-ui.xml')
root=ET.fromstring(run('shell','cat','/data/local/tmp/robotai-start-ui.xml'))
allowed={'Đánh thức','Ngủ','Kết nối','Nói với Ken','Đăng nhập','Đăng xuất','Sẵn sàng kết nối','Người quen','Ken đang ngủ'}
labels=[]
for n in root.iter('node'):
 if n.get('package')!='com.robotai.robot':continue
 text=n.get('text','')
 if text in allowed or text.startswith(('Camera bật','Camera tắt','Cần cấp quyền camera','Đã kết nối','Đang kết nối','Đã ngắt kết nối','Sẵn sàng','Hello timeout','Không kết nối')):labels.append(text)
print(json.dumps({'packages':sorted({n.get('package','') for n in root.iter('node')}),'robotUi':labels},ensure_ascii=False))
if a.action!='inspect':
 label='Đánh thức' if a.action=='wake' else 'Kết nối'
 nodes=[n for n in root.iter('node') if n.get('package')=='com.robotai.robot' and n.get('text')==label and n.get('enabled')=='true']
 if not nodes:raise SystemExit('Button unavailable: '+label)
 b=list(map(int,re.findall(r'\d+',nodes[0].get('bounds',''))));assert len(b)==4
 run('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
 print('Pressed:',label)
