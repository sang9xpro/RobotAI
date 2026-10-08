"""Scripted voice backend: real Opus, fake captions, pre-recorded reply.

No STT/LLM/TTS inference. Run --help. Only local development, not production.
"""
import argparse
import asyncio
import json
import subprocess
import uuid
from pathlib import Path
import websockets
from protocol import pack, unpack
from opus_codec import Codec

ROOT = Path(__file__).resolve().parents[2]
REPLY = "Xin chào, mình là robot đồng hành của bạn. Hôm nay bạn muốn trò chuyện hay cùng mình khám phá điều gì?"

def make_packets(wav):
    pcm = subprocess.check_output(["ffmpeg", "-v", "error", "-i", str(wav), "-f", "s16le", "-ac", "1", "-ar", "24000", "pipe:1"])
    codec = Codec(24000, True)
    try:
        return [codec.encode(pcm[i:i + 2880].ljust(2880, b"\0")) for i in range(0, len(pcm), 2880)]
    finally:
        codec.close()

class Session:
    def __init__(self, ws, packets, inject_late=False):
        self.ws, self.packets, self.inject_late = ws, packets, inject_late
        self.sid = uuid.uuid4().hex
        self.generation = 1
        self.turn = 0
        self.last_turn = 0
        self.seq = -1
        self.frames = self.samples = self.peak = 0
        self.decoder = None
        self.reply = self.endpoint = None
        self.ready = False
        self.canceled = set()
        self.last_voiced = None
        self.replying = False

    async def send(self, kind, **fields):
        await self.ws.send(json.dumps(dict(type=kind, session_id=self.sid,
                                          generation=self.generation, turn_id=str(self.turn), **fields)))

    def valid(self, event):
        return (event.get("session_id") == self.sid and event.get("generation") == self.generation
                and event.get("turn_id") == str(self.turn))

    async def finish(self):
        if self.replying or not self.turn or self.turn in self.canceled:
            return
        self.replying = True
        if self.endpoint and self.endpoint != asyncio.current_task():
            self.endpoint.cancel()
        await self.send("utterance_end")
        if not self.frames:
            await self.send("error", code="no_speech", message="Chưa có frame audio", retryable=True)
            return
        await self.send("stt", state="final", revision=3,
                        text="[GIẢ LẬP] Tôi muốn trò chuyện với robot.")
        await self.send("diagnostics", uplink_frames=self.frames,
                        decoded_samples=self.samples, peak=self.peak)
        self.reply = asyncio.create_task(self.speak(self.turn))

    async def speak(self, turn):
        await asyncio.sleep(.25)
        await self.send("tts", state="start")
        await self.send("tts", state="sentence_start", sentence_id=0, text=REPLY)
        for seq, packet in enumerate(self.packets):
            if turn in self.canceled or turn != self.turn:
                return
            await self.ws.send(pack(self.generation, turn, seq, packet))
            await asyncio.sleep(.06)
        await self.send("tts", state="stop")

    async def timeout(self, turn):
        await asyncio.sleep(30)
        if self.turn == turn:
            await self.finish()

    async def run(self):
        try:
            async for message in self.ws:
                if isinstance(message, bytes):
                    if not self.ready:
                        raise ValueError("audio before hello")
                    generation, turn, seq, packet = unpack(message)
                    if generation != self.generation or turn != self.turn or turn in self.canceled or self.replying:
                        continue
                    if seq != self.seq + 1:
                        raise ValueError("audio sequence discontinuity")
                    self.seq = seq
                    count, peak = self.decoder.decode(packet)
                    self.frames += 1
                    self.samples += count
                    self.peak = max(self.peak, peak)
                    if self.frames in (5, 15):
                        await self.send("stt", state="partial", revision=1 if self.frames == 5 else 2,
                                        text="[GIẢ LẬP] Tôi muốn" if self.frames == 5 else "[GIẢ LẬP] Tôi muốn trò chuyện")
                    # Simple energy endpoint is intentionally a mock, not a speech VAD.
                    now = asyncio.get_running_loop().time()
                    if peak > 900:
                        self.last_voiced = now
                    if self.last_voiced and now - self.last_voiced > .9:
                        await self.finish()
                    continue
                event = json.loads(message)
                kind = event.get("type")
                if kind == "hello":
                    params = event.get("audio_params", {})
                    if self.ready or params != dict(format="opus", sample_rate=16000, channels=1, frame_duration=60) or "rai1" not in event.get("robotai", {}).get("audio_envelopes", []):
                        raise ValueError("unsupported hello")
                    self.ready = True
                    await self.ws.send(json.dumps(dict(type="hello", transport="websocket", session_id=self.sid,
                        audio_params=dict(format="opus", sample_rate=24000, channels=1, frame_duration=60),
                        robotai=dict(contract_version=1, audio_envelope="rai1", generation=1,
                                     stt_mode="mock_scripted", tts_mode="mock_fixture", mock=True))))
                elif kind == "listen" and event.get("state") == "start":
                    if not self.ready or event.get("session_id") != self.sid or event.get("generation") != 1:
                        raise ValueError("invalid session")
                    turn = int(event["turn_id"])
                    if turn <= self.last_turn or (self.reply and not self.reply.done()):
                        raise ValueError("turn not monotonic or response still active")
                    self.turn = self.last_turn = turn
                    if self.decoder:
                        self.decoder.close()
                    self.decoder = Codec(16000)
                    self.seq = -1
                    self.frames = self.samples = self.peak = 0
                    self.replying = False
                    self.last_voiced = None
                    self.endpoint = asyncio.create_task(self.timeout(turn))
                elif kind == "listen" and event.get("state") == "stop" and self.valid(event):
                    await self.finish()
                elif kind == "abort" and self.valid(event):
                    old = self.turn
                    self.canceled.add(old)
                    for task in (self.reply, self.endpoint):
                        if task:
                            task.cancel()
                    if self.reply:
                        await asyncio.gather(self.reply, return_exceptions=True)
                    await self.send("abort_ack", request_id=event.get("request_id"))
                    if self.inject_late:
                        await self.ws.send(pack(1, old, 999, self.packets[0]))
                        await self.send("stt", state="final", revision=99, text="KHÔNG ĐƯỢC HIỂN THỊ")
                elif kind == "playback":
                    pass
                else:
                    raise ValueError("unsupported control event")
        except (ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
            await self.send("error", code="invalid_contract", message=str(exc), retryable=False)
            await self.ws.close(1008, "invalid contract")
        finally:
            for task in (self.reply, self.endpoint):
                if task:
                    task.cancel()
            await asyncio.gather(*(t for t in (self.reply, self.endpoint) if t), return_exceptions=True)
            if self.decoder:
                self.decoder.close()

async def main(args):
    packets = make_packets(args.wav)
    async def handle(ws):
        if ws.request.path != "/ws/xiaozhi/v1/":
            await ws.close(1008, "invalid path")
            return
        await Session(ws, packets, args.inject_late).run()
    async with websockets.serve(handle, args.host, args.port, max_size=8192, max_queue=16):
        print(f"Mock ready ws://{args.host}:{args.port}/ws/xiaozhi/v1/ ({len(packets)} reply frames)", flush=True)
        await asyncio.Future()

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8766)
    parser.add_argument("--wav", type=Path, default=ROOT / "samples/tts/vieneu-v3-turbo/hai-dang.wav")
    parser.add_argument("--inject-late", action="store_true")
    asyncio.run(main(parser.parse_args()))
