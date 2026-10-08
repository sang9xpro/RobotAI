package com.whispercppdemo

import com.whispercppdemo.media.encodeWaveFile
import com.whispercppdemo.media.decodeWaveFile
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WaveFileTest {
    @Test fun headerMatchesActualAudioLengthAndSamplesRoundTrip() {
        val file = File.createTempFile("whisper-test", ".wav")
        try {
            val samples = shortArrayOf(-32768, -1234, 0, 1234, 32767)
            encodeWaveFile(file, samples)
            val bytes = file.readBytes()
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(bytes.size - 8, header.getInt(4))
            assertEquals(samples.size * 2, header.getInt(40))
            assertEquals(16000, header.getInt(24))
            val decoded = decodeWaveFile(file)
            assertEquals(samples.size, decoded.size)
            assertEquals(-1f, decoded.first(), 0.0001f)
            assertEquals(1f, decoded.last(), 0.0001f)
        } finally { file.delete() }
    }
}
