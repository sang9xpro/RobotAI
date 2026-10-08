"""Private OpenAI-compatible HTTP adapter for Ken's ChatGPT-backed LLM port."""
import asyncio
import contextlib
import hmac
import json
import os
import time
import uuid
import base64
import binascii
from aiohttp import web
from codex_adapter import CodexSession, CodexError, DEFAULT_CLI


STATE = web.AppKey("gateway_state", dict)

def decode_image(url):
    """Only inline images: never fetch a URL or resolve a caller's local path."""
    if not isinstance(url, str):
        raise ValueError('Invalid inline image.')
    header, separator, encoded = url.partition(',')
    if not separator or header not in ('data:image/jpeg;base64', 'data:image/png;base64') or len(encoded) > 2_666_668:
        raise ValueError('One JPEG/PNG image of at most 2 MB is supported.')
    try:
        image = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error) as error:
        raise ValueError('Invalid image encoding.') from error
    if not 1 <= len(image) <= 2_000_000 or not (image.startswith(b'\xff\xd8\xff') if 'jpeg' in header else image.startswith(b'\x89PNG\r\n\x1a\n')):
        raise ValueError('Invalid image format.')
    return image, '.jpg' if 'jpeg' in header else '.png'

def validate(body):
    if not isinstance(body, dict):
        raise ValueError('Expected a JSON object.')
    if any(body.get(k) for k in ('tools', 'functions', 'tool_choice', 'response_format')):
        raise ValueError('Tools and structured output are unavailable for this provider.')
    messages = body.get('messages')
    if not isinstance(messages, list) or not 1 <= len(messages) <= 100:
        raise ValueError('messages must contain 1–100 text messages.')
    characters, images = 0, 0
    for msg in messages:
        if (not isinstance(msg, dict) or msg.get('role') not in ('system', 'developer', 'user', 'assistant')
                or msg.get('tool_calls')):
            raise ValueError('Unsupported message.')
        content = msg.get('content')
        if isinstance(content, str):
            characters += len(content)
        elif msg['role'] == 'user' and isinstance(content, list) and 1 <= len(content) <= 3:
            for part in content:
                if not isinstance(part, dict): raise ValueError('Invalid content part.')
                if part.get('type') == 'text' and isinstance(part.get('text'), str):
                    characters += len(part['text'])
                elif part.get('type') == 'image_url' and isinstance(part.get('image_url'), dict):
                    decode_image(part['image_url'].get('url')); images += 1
                else: raise ValueError('Unsupported content part.')
        else: raise ValueError('Unsupported message content.')
    if images > 1: raise ValueError('Only one image per request is supported.')
    if not any(m['role'] == 'user' for m in messages):
        raise ValueError('A user message is required.')
    if characters > 32000:
        raise ValueError('Conversation exceeds 32000 characters; truncate history in the backend.')
    if body.get('n', 1) != 1 or not isinstance(body.get('stream', False), bool):
        raise ValueError('Only n=1 and boolean stream are supported.')
    if not isinstance(body.get('model', 'codex-default'), str):
        raise ValueError('model must be a string.')
    return messages


def create_app(token, session_factory=CodexSession, timeout=90, max_active=2):
    if not token or len(token) < 24:
        raise ValueError('Set KEN_LLM_TOKEN to a private gateway token of at least 24 characters.')

    @web.middleware
    async def auth(request, handler):
        if not hmac.compare_digest(request.headers.get('Authorization', ''), 'Bearer ' + token):
            return web.json_response({'error': {'message': 'Unauthorized', 'type': 'authentication_error'}}, status=401)
        return await handler(request)

    app = web.Application(middlewares=[auth], client_max_size=3 * 1024 * 1024,
                          handler_args={'handler_cancellation': True})
    state = {'busy': False, 'active': 0, 'catalog': []}
    app[STATE] = state

    async def catalog():
        async with asyncio.timeout(20):
            async with session_factory() as session:
                return await session.models()

    async def health(request):
        return web.json_response({'status': 'ok', 'provider': 'codex-chatgpt', 'busy': state['busy']})

    async def models(request):
        try:
            state['catalog'] = await catalog()
            return web.json_response({'object': 'list', 'data': [
                {'id': m['model'], 'object': 'model', 'owned_by': 'codex-chatgpt'} for m in state['catalog']]})
        except (CodexError, TimeoutError, OSError):
            return web.json_response({'error': {'message': 'Codex authentication/catalog unavailable.'}}, status=503)

    async def completions(request):
        try:
            body = await request.json()
            messages = validate(body)
        except (ValueError, json.JSONDecodeError):
            return web.json_response({'error': {'message': 'Invalid request; text or one inline JPEG/PNG required.'}}, status=400)
        if state['active'] >= max_active:
            return web.json_response({'error': {'message': 'LLM busy; retry after the current turn.'}}, status=429,
                                     headers={'Retry-After': '2'})
        state['active'] += 1
        state['busy'] = True
        response = None
        try:
            async with asyncio.timeout(timeout):
                async with session_factory() as session:
                    available = state['catalog'] or await session.models()
                    state['catalog'] = available
                    requested = body.get('model', 'codex-default')
                    chosen = next((m for m in available if m['model'] == requested), None)
                    if requested == 'codex-default':
                        chosen = next((m for m in available if m.get('isDefault')), available[0] if available else None)
                    if not chosen:
                        return web.json_response({'error': {'message': 'Model unavailable; query /v1/models.'}}, status=400)
                    effort = body.get('reasoning_effort') or os.getenv('KEN_LLM_EFFORT', 'low')
                    allowed = [x['reasoningEffort'] for x in chosen['supportedReasoningEfforts']]
                    if effort not in allowed:
                        return web.json_response({'error': {'message': 'Unsupported reasoning_effort for model.'}}, status=400)
                    ident, created = 'chatcmpl-' + uuid.uuid4().hex, int(time.time())
                    def chunk(delta, finish=None):
                        return {'id': ident, 'object': 'chat.completion.chunk', 'created': created,
                                'model': chosen['model'], 'choices': [{'index': 0, 'delta': delta, 'finish_reason': finish}]}
                    if body.get('stream'):
                        response = web.StreamResponse(headers={'Content-Type': 'text/event-stream',
                                                   'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no'})
                        await response.prepare(request)
                        async def emit(obj):
                            await response.write(('data: ' + json.dumps(obj, ensure_ascii=False) + '\n\n').encode())
                        await emit(chunk({'role': 'assistant'}))
                        async for delta in session.stream(messages, chosen['model'], effort):
                            await emit(chunk({'content': delta}))
                        await emit(chunk({}, 'stop'))
                        await response.write(b'data: [DONE]\n\n')
                        await response.write_eof()
                        return response
                    text = ''.join([d async for d in session.stream(messages, chosen['model'], effort)])
                    return web.json_response({'id': ident, 'object': 'chat.completion', 'created': created,
                        'model': chosen['model'], 'choices': [{'index': 0, 'message': {'role': 'assistant', 'content': text},
                                                             'finish_reason': 'stop'}]})
        except (CodexError, TimeoutError, OSError, ConnectionError) as exc:
            # Do not leak prompts, process output, credentials or upstream details.
            if response is not None:
                with contextlib.suppress(ConnectionError, RuntimeError):
                    await response.write(b'data: {"error":{"message":"LLM turn failed or timed out"}}\n\n')
                    await response.write_eof()
                return response
            return web.json_response({'error': {'message': 'LLM unavailable or turn timed out.'}}, status=503)
        finally:
            state['active'] -= 1
            state['busy'] = state['active'] > 0

    app.router.add_get('/health', health)
    app.router.add_get('/v1/models', models)
    for path in ('/v1/chat/completions', '/chat/completions'):
        app.router.add_post(path, completions)
    return app

if __name__ == '__main__':
    token = os.environ.get('KEN_LLM_TOKEN', '')
    cli = os.environ.get('KEN_CODEX_CLI', DEFAULT_CLI)
    app = create_app(token, lambda: CodexSession(cli), int(os.environ.get('KEN_LLM_TIMEOUT', '90')))
    web.run_app(app, host='127.0.0.1', port=int(os.environ.get('KEN_LLM_PORT', '8787')),
                access_log=None, handler_cancellation=True)
