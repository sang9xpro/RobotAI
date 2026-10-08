#!/usr/bin/env python3
"""Run a private, resident PhoWhisper whisper.cpp worker on loopback."""
import argparse
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
LOCAL = ROOT / '.local'
BUILD = LOCAL / 'phowhisper-build'
PID = LOCAL / 'phowhisper.pid'
CONFIG = LOCAL / 'phowhisper.json'
SOURCE = ROOT / 'android-app/vendor/whisper.cpp'
BINARY = BUILD / 'bin/whisper-server'


def config():
    LOCAL.mkdir(exist_ok=True)
    if not CONFIG.exists():
        CONFIG.write_text(json.dumps({'model': str(ROOT / 'android-app/whisper-test/app/src/main/assets/models/ggml-phowhisper-small.bin'), 'port': 8788, 'threads': 4}, indent=2))
    return json.loads(CONFIG.read_text())


def running():
    if not PID.exists():
        return None
    pid = int(PID.read_text())
    result = subprocess.run(['ps', '-p', str(pid), '-o', 'command='], capture_output=True, text=True)
    return pid if str(BINARY) in result.stdout else None


def health(c):
    with urllib.request.urlopen(f"http://127.0.0.1:{c['port']}/health", timeout=3) as response:
        return response.status == 200 and json.load(response).get('status') == 'ok'


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['build', 'start', 'stop', 'status', 'configure-backend', 'smoke'])
    parser.add_argument('--wav', type=Path)
    args = parser.parse_args()
    c = config()
    if args.action == 'build':
        subprocess.run(['cmake', '-S', str(SOURCE), '-B', str(BUILD), '-DWHISPER_BUILD_SERVER=ON',
                        '-DWHISPER_BUILD_TESTS=OFF', '-DGGML_METAL=' + ('ON' if sys.platform == 'darwin' else 'OFF'),
                        '-DGGML_METAL_EMBED_LIBRARY=ON', '-DCMAKE_BUILD_TYPE=Release'], check=True)
        subprocess.run(['cmake', '--build', str(BUILD), '--target', 'whisper-server', '-j', '6'], check=True)
    elif args.action == 'start':
        if running():
            raise SystemExit('PhoWhisper already running.')
        if not BINARY.is_file() or not Path(c['model']).is_file():
            raise SystemExit('Build worker and configure an existing PhoWhisper GGML model first.')
        with socket.socket() as probe:
            if probe.connect_ex(('127.0.0.1', c['port'])) == 0:
                raise SystemExit('Port occupied; refusing to replace another service.')
        fd = os.open(LOCAL / 'phowhisper.log', os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
        with os.fdopen(fd, 'ab') as log:
            process = subprocess.Popen([str(BINARY), '--host', '127.0.0.1', '--port', str(c['port']),
                         '-m', c['model'], '-l', 'vi', '-t', str(c['threads']), '-bs', '1', '-bo', '1', '-nf',
                         '--public', str(LOCAL / 'phowhisper-public')], cwd=ROOT,
                         stdout=log, stderr=log, start_new_session=True)
        PID.write_text(str(process.pid))
        print('PhoWhisper starting; use status to verify model readiness.')
    elif args.action == 'stop':
        pid = running()
        if pid:
            os.kill(pid, signal.SIGTERM)
            deadline = time.monotonic() + 10
            while running() and time.monotonic() < deadline:
                time.sleep(.1)
            if running():
                raise SystemExit('Worker still stopping.')
        print('PhoWhisper stopped.')
    elif args.action == 'status':
        ready = False
        if running():
            try:
                ready = health(c)
            except (OSError, ValueError):
                pass
        print(f'PhoWhisper process={bool(running())}, model_ready={ready}, model={Path(c["model"]).name}')
        sys.exit(0 if ready else 1)
    elif args.action == 'configure-backend':
        if not health(c):
            raise SystemExit('Worker must be ready before configuring backend.')
        path = LOCAL / 'backend.env'
        lines = path.read_text().splitlines()
        updates = {'XIAOZHI_STT_DEFAULT_PROVIDER': 'phowhisper',
                   'XIAOZHI_STT_PHOWHISPER_API_URL': f"http://127.0.0.1:{c['port']}/inference"}
        lines = [line for line in lines if line.split('=', 1)[0] not in updates]
        path.write_text('\n'.join(lines + [f'{key}={value}' for key, value in updates.items()]) + '\n')
        path.chmod(0o600)
        print('Backend default STT configured as phowhisper; restart Java to apply. Explicit role STT selections take precedence.')
    elif args.action == 'smoke':
        if not args.wav:
            parser.error('--wav is required')
        import uuid
        boundary = 'pho-' + uuid.uuid4().hex
        body = bytearray()
        for name, value in [('language', 'vi'), ('translate', 'false'), ('temperature', '0.0'), ('temperature_inc', '0.0'), ('response_format', 'json')]:
            body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
        body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="audio.wav"\r\nContent-Type: audio/wav\r\n\r\n'.encode())
        body.extend(args.wav.read_bytes())
        body.extend(f'\r\n--{boundary}--\r\n'.encode())
        request = urllib.request.Request(f"http://127.0.0.1:{c['port']}/inference", body,
                                         {'Content-Type': f'multipart/form-data; boundary={boundary}'})
        started = time.monotonic()
        with urllib.request.urlopen(request, timeout=95) as response:
            result = json.load(response)
        if not isinstance(result.get('text'), str) or not result['text'].strip():
            raise SystemExit('No transcript returned.')
        print(json.dumps({'text': result['text'].strip(), 'seconds': round(time.monotonic() - started, 3)}, ensure_ascii=False))
