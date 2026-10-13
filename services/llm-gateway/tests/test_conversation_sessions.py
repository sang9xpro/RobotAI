import asyncio
import time
import unittest
from unittest.mock import AsyncMock
from conversation_sessions import ConversationSessions
from codex_adapter import CodexSession


class SessionTests(unittest.IsolatedAsyncioTestCase):
    def registry(self, limit=3):
        self.created = []
        def factory():
            session = AsyncMock()
            self.created.append(session)
            return session
        return ConversationSessions(factory, limit=limit, lease=1)

    async def open(self, registry, ident, owner='A'):
        return await registry.open(ident, owner, 'model', 'low', [], AsyncMock(return_value={'model': 'model'}))

    async def test_identity_isolation_idempotence_and_owner_conflict(self):
        registry = self.registry()
        a, b = await asyncio.gather(self.open(registry, 'a'), self.open(registry, 'b', 'B'))
        self.assertIsNot(a.session, b.session)
        self.assertIs(a, await self.open(registry, 'a'))
        with self.assertRaises(ValueError): await self.open(registry, 'a', 'B')
        a.session.prepare.assert_awaited_once()
        await registry.close('a')
        await registry.close('a')
        a.session.close.assert_awaited_once()
        self.assertTrue(b.ready)
        await registry.shutdown()
        b.session.close.assert_awaited_once()

    async def test_parallel_users_and_same_session_busy(self):
        registry = self.registry()
        await self.open(registry, 'a')
        await self.open(registry, 'b', 'B')
        async with registry.use('a'):
            async with registry.use('b'): pass
            with self.assertRaises(OverflowError):
                async with registry.use('a'): pass
        await registry.shutdown()

    async def test_cancel_interrupts_and_next_turn_can_reuse_connection(self):
        registry = self.registry()
        entry = await self.open(registry, 'a')
        with self.assertRaises(asyncio.CancelledError):
            async with registry.use('a'): raise asyncio.CancelledError()
        entry.session.interrupt.assert_awaited_once()
        async with registry.use('a'): pass
        await registry.shutdown()

    async def test_failed_preparation_and_expired_lease_release_resources(self):
        registry = self.registry(limit=1)
        entry = await self.open(registry, 'a')
        with self.assertRaises(OverflowError): await self.open(registry, 'b')
        entry.touched = time.monotonic() - 2
        await registry.reap()
        entry.session.close.assert_awaited_once()
        with self.assertRaises(KeyError):
            async with registry.use('a'): pass
        choose = AsyncMock(side_effect=OSError())
        with self.assertRaises(OSError): await registry.open('b', 'B', 'model', 'low', [], choose)
        self.assertFalse(registry.entries)
        self.created[-1].close.assert_awaited_once()


class HistoryTests(unittest.IsolatedAsyncioTestCase):
    async def test_warmup_and_new_turns_do_not_repeat_history(self):
        session = CodexSession()
        session.directory = type('Directory', (), {'name': '/tmp'})()
        counter = 0
        async def rpc(method, params):
            nonlocal counter
            if method == 'thread/start':
                counter += 1
                return {'thread': {'id': str(counter)}}
            return {}
        session.rpc = AsyncMock(side_effect=rpc)
        inputs = []
        async def run_turn(content, effort):
            inputs.append(content[0]['text'])
            yield '[happy] Chào'
        session.run_turn = run_turn
        system = {'role': 'system', 'content': 'Ken'}
        user = {'role': 'user', 'content': 'hi'}
        await session.prepare([system], 'model', 'low')
        self.assertEqual(counter, 1)
        self.assertEqual([x async for x in session.stream([system, user], 'model', 'low')], ['[happy] Chào'])
        followup = [system, user, {'role': 'assistant', 'content': 'Chào'}, {'role': 'user', 'content': 'again'}]
        _ = [x async for x in session.stream(followup, 'model', 'low')]
        self.assertEqual(counter, 1)
        self.assertNotIn('hi', inputs[-1])
        self.assertIn('again', inputs[-1])
        # A backend truncation/changed speaker must reset upstream context.
        _ = [x async for x in session.stream([system, {'role': 'user', 'content': 'other person'}], 'model', 'low')]
        self.assertEqual(counter, 2)

    async def test_interrupt_drains_completion_then_invalidates_partial_context(self):
        session = CodexSession()
        session.thread_id, session.turn_id, session.instructions = 't', 'u', 'old'
        session.rpc = AsyncMock(return_value={})
        session.pending = [{'method': 'item/agentMessage/delta', 'params': {'delta': 'partial'}},
                           {'method': 'turn/completed', 'params': {'turn': {'id': 'u', 'status': 'interrupted'}}}]
        await session.interrupt()
        self.assertIsNone(session.turn_id)
        self.assertIsNone(session.instructions)
