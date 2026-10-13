package com.xiaozhi.config;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Parses an uploaded WAV, preserving the STT service's PCM 16 kHz mono contract. */
final class PcmWavUpload {
    private static final int MAX_BYTES = 960000 + 65536;
    private PcmWavUpload() {}

    static byte[] decode(byte[] wav) {
        if (wav == null || wav.length < 44 || wav.length > MAX_BYTES)
            throw new IllegalArgumentException("Upload a WAV file of at most 30 seconds");
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157
                || Integer.toUnsignedLong(b.getInt(4)) + 8 != wav.length)
            throw new IllegalArgumentException("Invalid WAV container");
        boolean formatValid = false;
        byte[] pcm = null;
        int offset = 12;
        for (; offset + 8 <= wav.length; ) {
            int type = b.getInt(offset);
            long size = Integer.toUnsignedLong(b.getInt(offset + 4));
            long next = offset + 8L + size + (size & 1);
            if (next > wav.length) throw new IllegalArgumentException("Invalid WAV chunk");
            int start = offset + 8;
            if (type == 0x20746d66) {
                if (size < 16 || b.getShort(start) != 1 || b.getShort(start + 2) != 1
                        || b.getInt(start + 4) != 16000 || b.getInt(start + 8) != 32000
                        || b.getShort(start + 12) != 2 || b.getShort(start + 14) != 16)
                    throw new IllegalArgumentException("WAV must be PCM 16-bit mono at 16 kHz");
                formatValid = true;
            } else if (type == 0x61746164) {
                if (pcm != null || size == 0 || size > 960000 || size % 2 != 0)
                    throw new IllegalArgumentException("WAV audio must be nonempty PCM of at most 30 seconds");
                pcm = Arrays.copyOfRange(wav, start, start + (int) size);
            }
            offset = (int) next;
        }
        if (offset != wav.length || !formatValid || pcm == null) throw new IllegalArgumentException("WAV format and audio chunks required");
        return pcm;
    }
}
