"""Thin libopus bindings for testing Android's portable Opus codec."""
import ctypes as C
import ctypes.util
import os
from pathlib import Path

library = os.environ.get("OPUS_LIBRARY") or ctypes.util.find_library("opus")
if not library:
    library = next((p for p in ("/opt/homebrew/lib/libopus.dylib", "/usr/local/lib/libopus.dylib") if Path(p).exists()), "libopus.so.0")
lib = C.CDLL(library)
lib.opus_encoder_create.argtypes = [C.c_int, C.c_int, C.c_int, C.POINTER(C.c_int)]
lib.opus_encoder_create.restype = C.c_void_p
lib.opus_decoder_create.argtypes = [C.c_int, C.c_int, C.POINTER(C.c_int)]
lib.opus_decoder_create.restype = C.c_void_p
lib.opus_encode.argtypes = [C.c_void_p, C.POINTER(C.c_int16), C.c_int, C.POINTER(C.c_ubyte), C.c_int]
lib.opus_decode.argtypes = [C.c_void_p, C.POINTER(C.c_ubyte), C.c_int, C.POINTER(C.c_int16), C.c_int, C.c_int]
lib.opus_encoder_destroy.argtypes = [C.c_void_p]
lib.opus_decoder_destroy.argtypes = [C.c_void_p]

class Codec:
    def __init__(self, rate, encoder=False):
        error = C.c_int()
        self.encoder = encoder
        self.state = (lib.opus_encoder_create(rate, 1, 2048, C.byref(error)) if encoder
                      else lib.opus_decoder_create(rate, 1, C.byref(error)))
        if error.value or not self.state:
            raise RuntimeError(f"opus create: {error.value}")
        self.rate = rate

    def encode(self, pcm):
        samples = (C.c_int16 * (len(pcm) // 2)).from_buffer_copy(pcm)
        packet = (C.c_ubyte * 4096)()
        size = lib.opus_encode(self.state, samples, len(samples), packet, len(packet))
        if size < 0:
            raise ValueError(f"opus encode: {size}")
        return bytes(packet[:size])

    def decode(self, packet):
        raw = (C.c_ubyte * len(packet)).from_buffer_copy(packet)
        samples = (C.c_int16 * (self.rate * 120 // 1000))()
        count = lib.opus_decode(self.state, raw, len(packet), samples, len(samples), 0)
        if count < 0:
            raise ValueError(f"opus decode: {count}")
        return count, max((abs(samples[i]) for i in range(count)), default=0)

    def close(self):
        if self.state:
            (lib.opus_encoder_destroy if self.encoder else lib.opus_decoder_destroy)(self.state)
            self.state = None
