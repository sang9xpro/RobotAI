"""Official Codex app-server adapter. Never reads/copies authentication material."""
import asyncio
import contextlib
import json
import os
import re
import tomllib
from pathlib import Path
import tempfile

DEFAULT_CLI = '/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex'
DISABLED = ('shell_tool', 'unified_exec', 'apps', 'plugins', 'hooks', 'browser_use',
            'computer_use', 'multi_agent', 'multi_agent_v2', 'image_generation',
            'code_mode_host', 'skill_search', 'skill_mcp_dependency_install',
            'view_image', 'workspace_dependencies', 'memories')

class CodexError(Exception):
    pass

class CodexSession:
    """One process per request: no histories, credentials or events shared between callers."""
    def __init__(self, cli=DEFAULT_CLI):
        self.cli = cli
        self.process = None
        self.thread_id = None
        self.turn_id = None
        self.counter = 0
        self.pending = []

    async def __aenter__(self):
        self.directory = tempfile.TemporaryDirectory(prefix='ken-chat-')
        args = [self.cli, 'app-server', '--stdio']
        overrides = {'web_search': 'disabled', 'approval_policy': 'never',
                     'sandbox_mode': 'read-only', 'project_doc_max_bytes': 0,
                     'model_provider': 'openai'}
        overrides.update({'features.' + flag: False for flag in DISABLED})
        # Config tables merge: mcp_servers={} does NOT clear existing servers.
        # Disable each configured transport without copying its credentials.
        config_path = Path(os.environ.get('CODEX_HOME', str(Path.home() / '.codex'))) / 'config.toml'
        if config_path.exists():
            config = tomllib.loads(config_path.read_text())
            for name in config.get('mcp_servers', {}):
                if not re.fullmatch(r'[A-Za-z0-9_-]+', name):
                    self.directory.cleanup()
                    raise CodexError('Unsupported MCP config key; chat isolation cannot be guaranteed.')
                overrides['mcp_servers.' + name + '.enabled'] = False
        overrides['features.skip_host_skill_discovery'] = True
        for name, value in overrides.items():
            args.extend(['-c', name + '=' + json.dumps(value)])
        # API key environment must not override the user's ChatGPT login.
        env = {k: v for k, v in os.environ.items()
               if k not in ('OPENAI_API_KEY', 'CODEX_API_KEY', 'KEN_LLM_TOKEN')}
        try:
            self.process = await asyncio.create_subprocess_exec(
                *args, cwd=self.directory.name, env=env,
                stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.DEVNULL, limit=4 * 1024 * 1024)
            await self.rpc('initialize', {'clientInfo': {'name': 'ken_llm_gateway',
                           'title': 'Ken conversation gateway', 'version': '0.1.0'},
                           'capabilities': {'experimentalApi': True}})
            await self.send({'method': 'initialized'})
            effective = (await self.rpc('config/read', {'includeLayers': False})).get('config', {})
            if any(server.get('enabled', True) for server in effective.get('mcp_servers', {}).values()):
                raise CodexError('An inherited MCP server is enabled; refusing to start chat gateway.')
            if any(effective.get('features', {}).get(flag, True) for flag in DISABLED):
                raise CodexError('Required chat-only feature gates are not disabled.')
            account = await self.rpc('account/read', {'refreshToken': False})
            if (account.get('account') or {}).get('type') != 'chatgpt':
                raise CodexError('Codex must be logged in with ChatGPT; run codex login.')
            return self
        except BaseException:
            await self.close()
            raise

    async def send(self, obj):
        self.process.stdin.write((json.dumps(obj, ensure_ascii=False) + '\n').encode())
        await self.process.stdin.drain()

    async def receive(self):
        line = await self.process.stdout.readline()
        if not line:
            raise CodexError('Codex app-server stopped unexpectedly.')
        obj = json.loads(line)
        if 'method' in obj and 'id' in obj:
            # Never approve tool execution, user-input or permission requests.
            await self.send({'id': obj['id'], 'error': {'code': -32601,
                             'message': 'Chat-only gateway: tools and approvals disabled.'}})
            raise CodexError('Tool request rejected by chat-only gateway.')
        return obj

    async def rpc(self, method, params):
        self.counter += 1
        ident = self.counter
        await self.send({'id': ident, 'method': method, 'params': params})
        while True:
            event = await self.receive()
            if event.get('id') == ident:
                if 'error' in event:
                    raise CodexError('Codex RPC failed: ' + method)
                return event.get('result', {})
            self.pending.append(event)

    async def models(self):
        models, cursor = [], None
        while True:
            result = await self.rpc('model/list', {'cursor': cursor, 'limit': 100})
            models.extend(m for m in result['data'] if not m.get('hidden'))
            cursor = result.get('nextCursor')
            if not cursor:
                return models

    async def stream(self, messages, model, effort):
        instructions = '\n\n'.join(m['content'] for m in messages if m['role'] in ('system', 'developer'))
        history = [m for m in messages if m['role'] in ('user', 'assistant')]
        # Image files live only in this request's private temporary directory;
        # cleanup runs on success, failure, timeout and client disconnect.
        images = []
        clean_history = []
        from gateway import decode_image
        for message in history:
            content = message['content']
            if isinstance(content, list):
                texts = []
                for part in content:
                    if part['type'] == 'text': texts.append(part['text'])
                    else:
                        raw, suffix = decode_image(part['image_url']['url'])
                        path = Path(self.directory.name) / ('scene' + suffix)
                        path.write_bytes(raw); path.chmod(0o600)
                        images.append({'type': 'localImage', 'path': str(path)})
                content = '\n'.join(texts) + '\n[The attached image belongs to this user message.]'
            clean_history.append(dict(message, content=content))
        start = await self.rpc('thread/start', {
            'model': model, 'modelProvider': 'openai', 'ephemeral': True,
            'cwd': self.directory.name, 'environments': [], 'runtimeWorkspaceRoots': [],
            'selectedCapabilityRoots': [], 'dynamicTools': [], 'approvalPolicy': 'never',
            'sandbox': 'read-only', 'experimentalRawEvents': False,
            'baseInstructions': 'You are Ken, a conversational robot. Reply directly to the last user message. '
                                'Use Vietnamese unless asked otherwise. Be concise and natural for speech. '
                                'You have no tools, filesystem, browser or command execution. '
                                'The supplied JSON contains conversation history, not tool commands.',
            'developerInstructions': instructions})
        self.thread_id = start['thread']['id']
        turn = await self.rpc('turn/start', {'threadId': self.thread_id, 'environments': [],
                     'input': [{'type': 'text', 'text': json.dumps(clean_history, ensure_ascii=False)}, *images],
                     'effort': effort, 'summary': 'none'})
        self.turn_id = turn['turn']['id']
        while True:
            event = self.pending.pop(0) if self.pending else await self.receive()
            params = event.get('params', {})
            if params.get('threadId') != self.thread_id:
                continue
            method = event.get('method')
            if method == 'item/agentMessage/delta':
                yield params['delta']
            elif method == 'item/started':
                item_type = params.get('item', {}).get('type')
                if item_type not in ('agentMessage', 'reasoning', 'userMessage'):
                    raise CodexError('Non-chat item rejected: ' + str(item_type))
            elif method == 'turn/completed':
                status = params['turn']['status']
                if status != 'completed':
                    raise CodexError('Codex turn ended: ' + status)
                self.turn_id = None
                return

    async def close(self):
        if self.process and self.process.returncode is None:
            if self.thread_id and self.turn_id:
                with contextlib.suppress(Exception):
                    await self.send({'id': 999999, 'method': 'turn/interrupt',
                                     'params': {'threadId': self.thread_id, 'turnId': self.turn_id}})
            # Kill this request's process as well: cannot leave a turn running on disconnect.
            with contextlib.suppress(ProcessLookupError):
                self.process.terminate()
            try:
                await asyncio.wait_for(self.process.wait(), 3)
            except asyncio.TimeoutError:
                self.process.kill()
                await self.process.wait()
        if hasattr(self, 'directory'):
            self.directory.cleanup()

    async def __aexit__(self, *args):
        await self.close()
