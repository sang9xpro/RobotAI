"""Isolated login/UI fixture; no real accounts, LLM or passwords. Bind loopback only."""
import asyncio
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading
import websockets

revoked = set()
valid = {f'fixture-token-{i}': i for i in (1, 2)}

def data(uid):
    return dict(token=f'fixture-token-{uid}', userId=uid, expiresIn=3600,
                user=dict(name=f'Fixture {uid}', username=f'fixture{uid}'))

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass
    def do_GET(self): self.handle_api()
    def do_POST(self): self.handle_api()
    def do_PUT(self): self.handle_api()
    def handle_api(self):
        payload = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')
        token = self.headers.get('Authorization', '').removeprefix('Bearer ')
        uid = valid.get(token) if token not in revoked else None
        result = None
        code = 200
        if self.path == '/api/user/login':
            uid = {'fixture1': 1, 'fixture2': 2}.get(payload.get('username'))
            if uid and payload.get('password') == 'fixture-only-password':
                revoked.discard(f'fixture-token-{uid}')
                result = data(uid)
            else:
                code = 401
        elif not uid:
            code = 401
        elif self.path == '/api/user/check-token':
            result = data(uid)
        elif self.path == '/api/user/logout':
            revoked.add(token)
        elif self.path.startswith('/api/user/robot-profile'):
            if self.command == 'PUT' and payload.get('roleId') != uid:
                code = 403
            else:
                result = dict(deviceId=f'user_chat_{uid}', roleId=uid,
                              roles=[dict(roleId=uid, roleName=f'Role {uid}', voiceName='fixture', sttId=None, modelId=2, ttsId=None)])
        else:
            code = 404
        body = json.dumps(dict(code=code, data=result)).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

async def websocket(ws):
    token = ws.request.headers.get('Authorization', '').removeprefix('Bearer ')
    uid = valid.get(token) if token not in revoked else None
    if not uid or ws.request.headers.get('device-id') != f'user_chat_{uid}':
        await ws.close(1008, 'Unauthorized fixture')
        return
    async for message in ws:
        if isinstance(message, str) and json.loads(message).get('type') == 'hello':
            await ws.send(json.dumps(dict(type='hello', session_id=f'fixture-session-{uid}',
                audio_params=dict(format='opus', sample_rate=16000, channels=1, frame_duration=60))))

async def main():
    http = ThreadingHTTPServer(('127.0.0.1', 8891), Handler)
    threading.Thread(target=http.serve_forever, daemon=True).start()
    async with websockets.serve(websocket, '127.0.0.1', 8892):
        print('Auth fixture ready on loopback 8891/8892 (test accounts only)', flush=True)
        await asyncio.Future()

if __name__ == '__main__':
    asyncio.run(main())
