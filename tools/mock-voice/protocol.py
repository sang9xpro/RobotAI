"""RobotAI RAI1 contract. No audio or text is stored by the mock."""
import struct

HEADER = struct.Struct("!4sIQII")
MAX_PACKET = 4096

def pack(generation, turn, seq, payload):
    if not 0 < len(payload) <= MAX_PACKET:
        raise ValueError("invalid payload size")
    return HEADER.pack(b"RAI1", generation, turn, seq, len(payload)) + payload

def unpack(data):
    if len(data) < HEADER.size:
        raise ValueError("short header")
    magic, generation, turn, seq, size = HEADER.unpack_from(data)
    if magic != b"RAI1" or not 0 < size <= MAX_PACKET or len(data) != HEADER.size + size:
        raise ValueError("invalid envelope")
    return generation, turn, seq, data[HEADER.size:]
