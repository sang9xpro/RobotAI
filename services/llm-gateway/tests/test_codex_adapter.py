import asyncio
import unittest
from unittest.mock import AsyncMock, patch
from codex_adapter import CodexSession, CodexError

class AdapterTest(unittest.IsolatedAsyncioTestCase):
    async def test_notifications_before_turn_reply_are_retained(self):
        session = CodexSession()
        events = iter([
            {'id':1, 'result':{'thread':{'id':'thread'}}},
            {'method':'item/agentMessage/delta','params':{'threadId':'thread','delta':'Chào Ken'}},
            {'id':2,'result':{'turn':{'id':'turn'}}},
            {'method':'turn/completed','params':{'threadId':'thread','turn':{'status':'completed'}}},
        ])
        session.directory = type('Directory', (), {'name':'/tmp/empty'})()
        session.send = AsyncMock()
        session.receive = AsyncMock(side_effect=lambda: next(events))
        output = [d async for d in session.stream([{'role':'user','content':'hi'}], 'model','low')]
        self.assertEqual(output, ['Chào Ken'])
        start = session.send.call_args_list[0].args[0]['params']
        self.assertEqual(start['environments'], [])
        self.assertEqual(start['dynamicTools'], [])
        self.assertTrue(start['ephemeral'])
        self.assertEqual(start['approvalPolicy'],'never')

    async def test_unknown_tool_request_is_denied(self):
        session = CodexSession()
        session.process = type('Process', (), {'stdout': type('Stdout', (), {'readline':AsyncMock(return_value=b'{"id":10,"method":"item/commandExecution/requestApproval"}\n')})()})()
        session.send = AsyncMock()
        with self.assertRaises(CodexError):
            await session.receive()
        response = session.send.call_args.args[0]
        self.assertIn('error', response)
        self.assertNotIn('result', response)

    async def test_abort_interrupts_then_terminates_own_process(self):
        session = CodexSession()
        process = type('Process', (), {'returncode':None, 'terminate':lambda self: setattr(self,'terminated',True),
                                        'wait':AsyncMock(return_value=0)})()
        session.process, session.thread_id, session.turn_id = process, 'thread','turn'
        session.send = AsyncMock()
        await session.close()
        self.assertEqual(session.send.call_args.args[0]['method'], 'turn/interrupt')
        self.assertTrue(process.terminated)


class StartupIsolationTest(unittest.IsolatedAsyncioTestCase):
    async def test_enabled_inherited_mcp_causes_startup_failure(self):
        from codex_adapter import DISABLED
        session = CodexSession()
        process = type('Process', (), {'returncode':0})()
        session.send = AsyncMock()
        session.rpc = AsyncMock(side_effect=[{}, {'config': {
            'features': {flag:False for flag in DISABLED},
            'mcp_servers': {'unexpected': {'enabled':True}}}}])
        with patch('asyncio.create_subprocess_exec', AsyncMock(return_value=process)):
            with self.assertRaisesRegex(CodexError, 'inherited MCP'):
                await session.__aenter__()
        self.assertFalse(__import__('pathlib').Path(session.directory.name).exists())

if __name__ == '__main__':
    unittest.main()
