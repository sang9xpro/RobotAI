"""Manage only this gateway's local process. Secrets stay in ignored .local/."""
import argparse
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import time
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
LOCAL = ROOT / '.local'
CONFIG = LOCAL / 'llm-gateway.json'
PID = LOCAL / 'llm-gateway.pid'

def load():
    LOCAL.mkdir(exist_ok=True, mode=0o700)
    if not CONFIG.exists():
        fd = os.open(CONFIG, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as f:
            json.dump({'token': secrets.token_urlsafe(32), 'port': 8787}, f)
    return json.loads(CONFIG.read_text())

def running():
    if not PID.exists():
        return None
    pid = int(PID.read_text())
    result = subprocess.run(['ps', '-p', str(pid), '-o', 'command='], capture_output=True, text=True)
    return pid if str(ROOT / 'services/llm-gateway/gateway.py') in result.stdout else None

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--python', default=None, help='Python runtime containing aiohttp (persisted for subsequent starts)')
    parser.add_argument('action', choices=['start', 'stop', 'status', 'models', 'smoke', 'configure-backend'])
    args = parser.parse_args()
    config = load()
    runtime_file = LOCAL / 'llm-gateway-python'
    if args.python:
        runtime_file.write_text(str(Path(args.python).resolve()))
    runtime = runtime_file.read_text().strip() if runtime_file.exists() else sys.executable
    if args.action == 'start':
        if running():
            print('Gateway already running.')
            sys.exit(0)
        with socket.socket() as probe:
            if probe.connect_ex(('127.0.0.1', config['port'])) == 0:
                raise SystemExit('Gateway port occupied; refusing to replace another service.')
        check = subprocess.run([runtime, '-c', 'import aiohttp'], capture_output=True)
        if check.returncode:
            raise SystemExit('Gateway runtime lacks aiohttp; pass --python with the configured runtime.')
        env = dict(os.environ, KEN_LLM_TOKEN=config['token'], KEN_LLM_PORT=str(config['port']))
        fd = os.open(LOCAL / 'llm-gateway.log', os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
        with os.fdopen(fd, 'ab') as log:
            process = subprocess.Popen([runtime, str(ROOT / 'services/llm-gateway/gateway.py')],
                         env=env, cwd=ROOT, stdout=log, stderr=log, start_new_session=True)
        PID.write_text(str(process.pid))
        print('Gateway started on 127.0.0.1:' + str(config['port']))
    elif args.action == 'stop':
        pid = running()
        if pid:
            os.kill(pid, signal.SIGTERM)
            deadline = time.monotonic() + 10
            while running() and time.monotonic() < deadline:
                time.sleep(.1)
            if running():
                raise SystemExit('Gateway still stopping; retry status later.')
        print('Gateway stopped.')
    elif args.action == 'configure-backend':
        import runpy
        if not (LOCAL / 'schema-owned').exists():
            raise SystemExit('Refusing to configure an unowned database.')
        shared = runpy.run_path(str(ROOT / 'server-java/scripts/robotai-preflight.py'))
        env = shared['load_env']()
        env['MYSQL_PWD'] = env['SPRING_DATASOURCE_PASSWORD']
        token_hex = config['token'].encode().hex()
        sql = f"""START TRANSACTION;
SET @owner=(SELECT MIN(userId) FROM sys_user);
SET @default_flag=IF(EXISTS(SELECT 1 FROM sys_config WHERE configType='llm' AND modelType='chat' AND isDefault='1' AND state='1'),'0','1');
INSERT INTO sys_config(userId,configType,modelType,provider,configName,configDesc,apiKey,apiUrl,isDefault,enableThinking,contextLength,state)
SELECT @owner,'llm','chat','codex-chatgpt','codex-default','Ken local ChatGPT gateway',CONVERT(0x{token_hex} USING utf8mb4),'http://127.0.0.1:{config['port']}/v1',@default_flag,0,4096,'1'
WHERE @owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_config WHERE provider='codex-chatgpt' AND configDesc='Ken local ChatGPT gateway');
UPDATE sys_config SET apiKey=CONVERT(0x{token_hex} USING utf8mb4),apiUrl='http://127.0.0.1:{config['port']}/v1'
WHERE provider='codex-chatgpt' AND configDesc='Ken local ChatGPT gateway';
SELECT configId,provider,configName,isDefault,state FROM sys_config WHERE provider='codex-chatgpt' AND configDesc='Ken local ChatGPT gateway';
COMMIT;"""
        result = subprocess.run(['docker','exec','-i','-e','MYSQL_PWD',env.get('ROBOTAI_MYSQL_CONTAINER','dev-mysql'),
                                'mysql','-uroot','-N','robotai_ken'], input=sql, env=env, text=True, capture_output=True)
        if result.returncode or not result.stdout.strip():
            raise SystemExit('Backend configuration failed; verify database and admin user. Secrets not printed.')
        print('Backend gateway config (id/provider/model/default/enabled):\n' + result.stdout.strip())
    elif args.action == 'status':
        print('Gateway running.' if running() else 'Gateway stopped.')
    else:
        import urllib.request
        import time
        url = 'http://127.0.0.1:' + str(config['port'])
        headers = {'Authorization': 'Bearer ' + config['token'], 'Content-Type': 'application/json'}
        if args.action == 'models':
            req = urllib.request.Request(url + '/v1/models', headers=headers)
            with urllib.request.urlopen(req, timeout=30) as response:
                print(response.read().decode())
        else:
            body = {'model': 'codex-default', 'stream': True,
                    'messages': [{'role': 'user', 'content': 'Chào Ken. Trả lời một câu ngắn bằng tiếng Việt.'}]}
            req = urllib.request.Request(url + '/v1/chat/completions', json.dumps(body).encode(), headers)
            start = time.monotonic()
            first = None
            done = False
            with urllib.request.urlopen(req, timeout=100) as response:
                for line in response:
                    if line.strip() == b'data: [DONE]':
                        done = True
                    if line.startswith(b'data: {'):
                        event = json.loads(line[6:])
                        if 'error' in event:
                            raise RuntimeError(event['error']['message'])
                        for choice in event.get('choices', []):
                            text = choice.get('delta', {}).get('content', '')
                            if text:
                                if first is None:
                                    first = time.monotonic() - start
                                print(text, end='', flush=True)
            print('\nTTFT:', round(first, 2) if first is not None else None,
                  's; total:', round(time.monotonic() - start, 2), 's; DONE:', done)
            if not done or first is None:
                sys.exit(1)
