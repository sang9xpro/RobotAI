import asyncio
import json
import math
import struct
import unittest
import websockets
from opus_codec import Codec
from protocol import pack, unpack
from server import Session

class EnvelopeTest(unittest.TestCase):
    def test_fixture_and_invalid_length(self):
        self.assertEqual(pack(1, 2, 3, b"\x12\x34").hex(), "5241493100000001000000000000000200000003000000021234")
        self.assertEqual(unpack(pack(1, 2, 3, b"\x12\x34")), (1, 2, 3, b"\x12\x34"))
        for bad in (b"", b"X" + pack(1, 2, 3, b"a")[1:], pack(1, 2, 3, b"a")[:-1]):
            with self.assertRaises(ValueError):
                unpack(bad)

class ProtocolTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        encoder = Codec(24000, True)
        pcm = struct.pack("<1440h", *(int(math.sin(i * .1) * 5000) for i in range(1440)))
        self.reply = [encoder.encode(pcm) for _ in range(3)]
        encoder.close()
        self.server = await websockets.serve(lambda ws: Session(ws, self.reply, True).run(), "127.0.0.1", 0)
        port = self.server.sockets[0].getsockname()[1]
        self.ws = await websockets.connect(f"ws://127.0.0.1:{port}/ws/xiaozhi/v1/")
        await self.ws.send(json.dumps(dict(type="hello", audio_params=dict(format="opus", sample_rate=16000, channels=1, frame_duration=60), robotai=dict(audio_envelopes=["rai1"]))))
        self.hello = json.loads(await self.ws.recv())
        self.encoder = Codec(16000, True)
        self.packet = self.encoder.encode(struct.pack("<960h", *([2000] * 960)))

    async def asyncTearDown(self):
        self.encoder.close()
        await self.ws.close()
        self.server.close()
        await self.server.wait_closed()

    async def event(self, kind, turn, **fields):
        await self.ws.send(json.dumps(dict(type=kind, session_id=self.hello["session_id"], generation=1, turn_id=str(turn), **fields)))

    async def start(self, turn):
        await self.event("listen", turn, state="start", mode="auto")
        for seq in range(5):
            await self.ws.send(pack(1, turn, seq, self.packet))

    async def test_thirty_complete_turns_and_real_opus_decode(self):
        for turn in range(1, 31):
            await self.start(turn)
            await self.event("listen", turn, state="stop")
            decoder = Codec(24000)
            frames = 0
            saw_partial = saw_final = saw_diagnostics = False
            while True:
                message = await asyncio.wait_for(self.ws.recv(), 3)
                if isinstance(message, bytes):
                    generation, audio_turn, seq, packet = unpack(message)
                    self.assertEqual((generation, audio_turn, seq), (1, turn, frames))
                    self.assertEqual(decoder.decode(packet)[0], 1440)
                    frames += 1
                else:
                    e = json.loads(message)
                    self.assertEqual(e["turn_id"], str(turn))
                    if e["type"] == "stt":
                        saw_partial |= e["state"] == "partial"
                        saw_final |= e["state"] == "final"
                    if e["type"] == "diagnostics":
                        self.assertEqual(e["uplink_frames"], 5)
                        self.assertEqual(e["decoded_samples"], 4800)
                        saw_diagnostics = True
                    if e["type"] == "tts" and e["state"] == "stop":
                        break
            decoder.close()
            self.assertEqual(frames, 3)
            self.assertTrue(saw_partial and saw_final and saw_diagnostics)

    async def test_abort_injects_old_frame_and_next_turn_still_works(self):
        await self.start(1)
        await self.event("listen", 1, state="stop")
        while True:
            m = await self.ws.recv()
            if isinstance(m, bytes):
                break
        await self.event("abort", 1, request_id="a1", reason="user_interrupt")
        while True:
            m = await self.ws.recv()
            if isinstance(m, str) and json.loads(m)["type"] == "abort_ack":
                break
        self.assertEqual(unpack(await self.ws.recv())[1:3], (1, 999))
        self.assertEqual(json.loads(await self.ws.recv())["revision"], 99)
        await self.start(2)
        await self.event("listen", 2, state="stop")
        while True:
            m = await self.ws.recv()
            if isinstance(m, str):
                e = json.loads(m)
                self.assertEqual(e["turn_id"], "2")
                if e["type"] == "tts" and e["state"] == "stop":
                    break

    async def test_malformed_audio_is_rejected(self):
        await self.start(1)
        await self.ws.send(b"bad")
        while True:
            event = json.loads(await self.ws.recv())
            if event["type"] == "error":
                self.assertEqual(event["code"], "invalid_contract")
                break

if __name__ == "__main__":
    unittest.main()
