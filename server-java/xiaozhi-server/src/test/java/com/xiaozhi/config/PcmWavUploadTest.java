package com.xiaozhi.config;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;

class PcmWavUploadTest {
    @Test void extractsExactPcmFromValidWav() { assertArrayEquals(new byte[]{1,2,3,4},PcmWavUpload.decode(wav(16000,1,16,new byte[]{1,2,3,4}))); }
    @Test void rejectsWrongSampleRateChannelsFormatAndTruncatedData() {
        assertThrows(IllegalArgumentException.class,()->PcmWavUpload.decode(wav(44100,1,16,new byte[4])));
        assertThrows(IllegalArgumentException.class,()->PcmWavUpload.decode(wav(16000,2,16,new byte[4])));
        assertThrows(IllegalArgumentException.class,()->PcmWavUpload.decode(wav(16000,1,8,new byte[4])));
        byte[] file=wav(16000,1,16,new byte[4]);file[40]=100;
        assertThrows(IllegalArgumentException.class,()->PcmWavUpload.decode(file));
        assertThrows(IllegalArgumentException.class,()->PcmWavUpload.decode(wav(16000,1,16,new byte[960002])));
    }
    private static byte[] wav(int sampleRate,int channels,int bits,byte[] pcm) {
        return ByteBuffer.allocate(44+pcm.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x46464952).putInt(36+pcm.length).putInt(0x45564157).putInt(0x20746d66).putInt(16)
                .putShort((short)1).putShort((short)channels).putInt(sampleRate).putInt(sampleRate*channels*bits/8)
                .putShort((short)(channels*bits/8)).putShort((short)bits).putInt(0x61746164).putInt(pcm.length).put(pcm).array();
    }
}
