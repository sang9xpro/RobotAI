import asyncio
import json
import unittest
from aiohttp import ClientSession, web
from gateway import create_app, validate, STATE

TOKEN = 'test-private-token-000000000000'
MODEL = {'model': 'test-model', 'isDefault': True, 'supportedReasoningEfforts': [{'reasoningEffort': 'low'}]}

class FakeSession:
    closed = 0
    gate = None
    entered = None
    fail = False
    async def __aenter__(self):
        return self
    async def __aexit__(self, *args):
        type(self).closed += 1
    async def models(self):
        return [MODEL]
    async def stream(self, messages, model, effort):
        if type(self).entered:
            type(self).entered.set()
        yield 'Chào '
        if type(self).gate:
            await type(self).gate.wait()
        if type(self).fail:
            raise OSError('private upstream failure')
        yield 'Ken!'

class GatewayTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        FakeSession.closed, FakeSession.gate, FakeSession.entered, FakeSession.fail = 0, None, None, False
        self.app = create_app(TOKEN, FakeSession, timeout=1, max_active=1)
        self.runner = web.AppRunner(self.app, handler_cancellation=True)
        await self.runner.setup()
        site = web.TCPSite(self.runner, '127.0.0.1', 0)
        await site.start()
        self.url = 'http://127.0.0.1:' + str(site._server.sockets[0].getsockname()[1])
        self.client = ClientSession(headers={'Authorization': 'Bearer ' + TOKEN})
        self.body = {'model': 'codex-default', 'messages': [{'role': 'user', 'content': 'Chào'}], 'stream': True}
    async def asyncTearDown(self):
        await self.client.close()
        await self.runner.cleanup()
    async def test_auth_and_validation(self):
        async with ClientSession() as anonymous:
            async with anonymous.get(self.url + '/health') as r:
                self.assertEqual(r.status, 401)
        for body in ({}, [], dict(self.body, tools=[{'type': 'function'}]),
                     dict(self.body, messages=[{'role':'user', 'content': [{'type':'image'}]}])):
            async with self.client.post(self.url + '/v1/chat/completions', json=body) as r:
                self.assertEqual(r.status, 400)
    async def test_stream_contract_and_alias(self):
        async with self.client.post(self.url + '/chat/completions', json=self.body) as r:
            self.assertEqual(r.status, 200)
            raw = await r.text()
            events = [json.loads(x[6:]) for x in raw.splitlines() if x.startswith('data: {')]
            self.assertEqual(''.join(e['choices'][0]['delta'].get('content','') for e in events), 'Chào Ken!')
            self.assertEqual(events[-1]['choices'][0]['finish_reason'], 'stop')
            self.assertIn('data: [DONE]', raw)
        self.assertEqual(FakeSession.closed, 1)
    async def test_nonstream_and_model_selection(self):
        async with self.client.post(self.url + '/v1/chat/completions', json=dict(self.body, stream=False)) as r:
            result = await r.json()
            self.assertEqual(result['choices'][0]['message']['content'], 'Chào Ken!')
        async with self.client.post(self.url + '/v1/chat/completions', json=dict(self.body, model='unknown')) as r:
            self.assertEqual(r.status, 400)
    async def test_disconnect_cancels_and_busy_admission(self):
        FakeSession.gate, FakeSession.entered = asyncio.Event(), asyncio.Event()
        response = await self.client.post(self.url + '/v1/chat/completions', json=self.body)
        await asyncio.wait_for(FakeSession.entered.wait(), 1)
        async with self.client.post(self.url + '/v1/chat/completions', json=self.body) as r:
            self.assertEqual(r.status, 429)
        response.close()
        for _ in range(30):
            if FakeSession.closed:
                break
            await asyncio.sleep(.01)
        self.assertEqual(FakeSession.closed, 1)
        self.assertFalse(self.app[STATE]['busy'])
    async def test_timeout_never_reports_success(self):
        FakeSession.gate = asyncio.Event()
        async with self.client.post(self.url + '/v1/chat/completions', json=self.body) as r:
            raw = await r.text()
            self.assertIn('"error"', raw)
            self.assertNotIn('[DONE]', raw)
        self.assertEqual(FakeSession.closed, 1)
        self.assertFalse(self.app[STATE]['busy'])
    async def test_catalog(self):
        async with self.client.get(self.url + '/v1/models') as r:
            self.assertEqual((await r.json())['data'][0]['id'], 'test-model')

    async def test_inline_images_and_no_external_fetch(self):
        import base64
        url = 'data:image/png;base64,' + base64.b64encode(b'\x89PNG\r\n\x1a\nexample').decode()
        messages = [{'role': 'user', 'content': [{'type': 'text', 'text': 'Describe'}, {'type': 'image_url', 'image_url': {'url': url}}]}]
        self.assertEqual(validate(dict(self.body, messages=messages)), messages)
        for bad in ('https://example.org/private.png', 'file:///tmp/private', 'data:image/png;base64,invalid', 'data:image/png;base64,' + base64.b64encode(b'x'*2_000_001).decode()):
            messages[0]['content'][1]['image_url']['url'] = bad
            with self.assertRaises(ValueError): validate(dict(self.body, messages=messages))

if __name__ == '__main__':
    unittest.main()
