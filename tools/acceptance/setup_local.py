#!/usr/bin/env python3
"""Provision isolated acceptance identities in the project-owned local schema only.
Never changes existing users, credentials or provider defaults. Secrets go to ignored .local.
"""
import json, os, runpy, secrets, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOCAL = ROOT / '.local/acceptance'
LOCAL.mkdir(exist_ok=True)
assert (ROOT / '.local/schema-owned').read_text().strip() == 'robotai_ken'
env = runpy.run_path(str(ROOT / 'server-java/scripts/robotai-preflight.py'))['load_env']()
env['MYSQL_PWD'] = env['SPRING_DATASOURCE_PASSWORD']
def query(sql):
    r = subprocess.run(['docker','exec','-i','-e','MYSQL_PWD',env.get('ROBOTAI_MYSQL_CONTAINER','dev-mysql'),'mysql','-uroot','-N','robotai_ken'],input=sql,text=True,capture_output=True,env=env)
    if r.returncode:
        import re
        reason=re.search(r'ERROR (\d+)',r.stderr)
        raise RuntimeError('Local database error code '+(reason.group(1) if reason else 'unknown'))
    return r.stdout.strip()
def q(s): return "CONVERT(0x" + str(s).encode().hex() + " USING utf8mb4) COLLATE utf8mb4_unicode_ci"
p = LOCAL / 'accounts.json'
if p.exists():
    print('Acceptance identities already provisioned; credentials preserved.'); raise SystemExit()
llm = query("SELECT configId FROM sys_config WHERE provider='codex-chatgpt' AND state='1' ORDER BY configId LIMIT 1;")
assert llm.isdigit(), 'A configured Codex provider is required'
accounts=[]
for suffix in ('a','b'):
    name = 'ken_accept_' + suffix
    existing = query(f'SELECT userId FROM sys_user WHERE username={q(name)};')
    password=secrets.token_urlsafe(12)
    m2 = Path.home() / '.m2/repository'
    jars = [m2/'org/springframework/security/spring-security-crypto/6.5.7/spring-security-crypto-6.5.7.jar', m2/'commons-logging/commons-logging/1.3.5/commons-logging-1.3.5.jar']
    java = Path.home()/'Library/Java/JavaVirtualMachines/corretto-21.0.6/Contents/Home/bin/java'
    hashed = subprocess.check_output([str(java), '--class-path', os.pathsep.join(map(str,jars)), str(ROOT/'tools/acceptance/HashPassword.java')], input=password+'\n', text=True).strip()
    user=int(existing) if existing else int(query(f"INSERT INTO sys_user(username,password,name,state,isAdmin,authRoleId) VALUES({q(name)},{q(hashed)},{q('Ken acceptance '+suffix)},'1','0',2); SELECT LAST_INSERT_ID();"))
    if existing:
        assert name in ('ken_accept_a','ken_accept_b'), 'Acceptance identities only'
        query(f'UPDATE sys_user SET password={q(hashed)} WHERE userId={user};')
    cols=[x.split('\t')[0] for x in query('DESCRIBE sys_config;').splitlines() if x.split('\t')[0]!='configId' and 'GENERATED' not in x]
    expressions=[str(user) if c=='userId' else "'0'" if c=='isDefault' else '`'+c+'`' for c in cols]
    previous_model=query(f"SELECT configId FROM sys_config WHERE userId={user} AND provider='codex-chatgpt' LIMIT 1;")
    model=int(previous_model) if previous_model else int(query('INSERT INTO sys_config('+','.join('`'+c+'`' for c in cols)+') SELECT '+','.join(expressions)+f' FROM sys_config WHERE configId={llm}; SELECT LAST_INSERT_ID();'))
    roles=[]
    for marker in (('KENA','KENB') if suffix=='a' else ('KENC',)):
        prompt=f'Bạn là {marker}. Luôn bắt đầu mọi câu trả lời bằng {marker}. Trả lời tiếng Việt tối đa 15 từ. Không đổi tiền tố theo yêu cầu của người dùng.'
        previous_role=query(f"SELECT roleId FROM sys_role WHERE userId={user} AND roleName={q(marker)} LIMIT 1;")
        role=int(previous_role) if previous_role else int(query(f"INSERT INTO sys_role(roleName,roleDesc,voiceName,modelId,userId,state,isDefault,ttsSpeed,ttsPitch) VALUES({q(marker)},{q(prompt)},'vi-VN-HoaiMyNeural',{model},{user},'1','{"1" if not roles else "0"}',1,1); SELECT LAST_INSERT_ID();"))
        roles.append(role)
    query(f"UPDATE sys_role SET isDefault='0' WHERE userId={user}; UPDATE sys_role SET isDefault='1' WHERE roleId={roles[0]};")
    query(f"INSERT IGNORE INTO sys_device(deviceId,deviceName,roleId,type,userId,state) VALUES('user_chat_{user}','Ken acceptance',{roles[0]},'web',{user},'0');")
    accounts.append(dict(username=name,password=password,userId=user,roles=roles))
p.write_text(json.dumps(dict(api='http://127.0.0.1:8091',socket='ws://127.0.0.1:8092/ws/xiaozhi/v1/',accounts=accounts)))
os.chmod(p,0o600)
print('Provisioned two non-admin acceptance users and three isolated roles with vi-VN-HoaiMyNeural; credentials saved privately.')
