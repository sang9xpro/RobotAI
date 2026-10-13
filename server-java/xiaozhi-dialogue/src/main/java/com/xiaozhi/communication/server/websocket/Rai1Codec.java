package com.xiaozhi.communication.server.websocket;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Same 24-byte envelope as the Android domain adapter. */
public final class Rai1Codec {
    public record Frame(long generation, long turn, long sequence, byte[] payload) {}
    public static byte[] encode(Frame f) {
        if (f.generation() < 0 || f.generation() > 0xffffffffL || f.turn() <= 0 || f.sequence() < 0
                || f.sequence() > 0xffffffffL || f.payload().length < 1 || f.payload().length > 4096)
            throw new IllegalArgumentException("Invalid RAI1 frame");
        return ByteBuffer.allocate(24 + f.payload().length).order(ByteOrder.BIG_ENDIAN)
                .putInt(0x52414931).putInt((int) f.generation()).putLong(f.turn())
                .putInt((int) f.sequence()).putInt(f.payload().length).put(f.payload()).array();
    }
    public static Frame decode(byte[] data) {
        if (data.length < 25) throw new IllegalArgumentException("Short RAI1 frame");
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        if (b.getInt() != 0x52414931) throw new IllegalArgumentException("Invalid RAI1 magic");
        long generation = Integer.toUnsignedLong(b.getInt()), turn = b.getLong(), sequence = Integer.toUnsignedLong(b.getInt());
        int size = b.getInt();
        if (turn <= 0 || size < 1 || size > 4096 || size != b.remaining()) throw new IllegalArgumentException("Invalid RAI1 length");
        byte[] payload = new byte[size]; b.get(payload); return new Frame(generation, turn, sequence, payload);
    }
    private Rai1Codec() {}
}
