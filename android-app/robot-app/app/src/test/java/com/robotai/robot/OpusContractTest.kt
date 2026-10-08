package com.robotai.robot

import com.robotai.domain.*
import io.github.jaredmdobson.concentus.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class OpusContractTest {
    @Test fun portableCodecRoundTripsSixtyMillisecondPackets() {
        val encoder = OpusEncoder(16000, 1, OpusApplication.OPUS_APPLICATION_VOIP)
        val decoder = OpusDecoder(16000, 1)
        val pcm = ShortArray(960) { (sin(it * 2 * Math.PI * 440 / 16000) * 6000).toInt().toShort() }
        val bytes = ByteArray(4096)
        repeat(10) { seq ->
            val n = encoder.encode(pcm, 0, pcm.size, bytes, 0, bytes.size)
            val packet = Rai1.decode(Rai1.encode(AudioFrame(1, 1, seq.toLong(), bytes.copyOf(n)))).payload
            val output = ShortArray(1920)
            assertEquals(960, decoder.decode(packet, 0, packet.size, output, 0, output.size, false))
            assertTrue(output.any { it.toInt() != 0 })
        }
    }
}
