#!/usr/bin/env python3
"""Configure only the owned local schema and disposable acceptance data."""
import json, runpy, subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
assert (ROOT/'.local/schema-owned').read_text().strip() == 'robotai_ken'
env = runpy.run_path(str(ROOT/'server-java/scripts/robotai-preflight.py'))['load_env']()
env['MYSQL_PWD'] = env['SPRING_DATASOURCE_PASSWORD']
def query(sql):
    result = subprocess.run(['docker','exec','-i','-e','MYSQL_PWD',env.get('ROBOTAI_MYSQL_CONTAINER','dev-mysql'),'mysql','-uroot','-N','robotai_ken'],input=sql,text=True,capture_output=True,env=env)
    if result.returncode: raise RuntimeError('Feature fixture database operation failed (details suppressed).')
    return result.stdout.strip()
if not query("SELECT configId FROM sys_config WHERE configType='llm' AND modelType='vision' AND state='1' AND isDefault='1' LIMIT 1;"):
    source = query("SELECT configId FROM sys_config WHERE provider='codex-chatgpt' AND modelType='chat' AND state='1' ORDER BY configId LIMIT 1;")
    assert source.isdigit()
    columns = [line.split('\t')[0] for line in query('DESCRIBE sys_config;').splitlines() if line.split('\t')[0]!='configId' and 'GENERATED' not in line]
    changes = {'modelType':"'vision'",'isDefault':"'1'",'configDesc':"'Ken local vision gateway'"}
    query('INSERT INTO sys_config('+','.join('`'+c+'`' for c in columns)+') SELECT '+','.join(changes.get(c,'`'+c+'`') for c in columns)+f' FROM sys_config WHERE configId={source};')
    print('Configured local vision model using the existing private gateway.')
else: print('Existing default vision model preserved.')
a=json.loads((ROOT/'.local/acceptance/accounts.json').read_text())['accounts'][0]
user, role = int(a['userId']), int(a['roles'][0])
query(f"INSERT INTO sys_summary(deviceId,roleId,sessionId,lastMessageTimestamp,summary,promptTokens,completionTokens,createTime) SELECT 'user_chat_{user}',{role},'ken-feature-acceptance',NOW(),'Disposable acceptance memory',0,0,NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_summary WHERE deviceId='user_chat_{user}' AND roleId={role} AND sessionId='ken-feature-acceptance');")
print('Disposable memory fixture ready for edit/delete verification.')
