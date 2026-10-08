package com.whispercppdemo

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whispercppdemo.media.decodeWaveFile
import com.whispercppdemo.speaker.SpeakerGate
import com.whispercppdemo.tts.VieneuEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SpeakerRuntimeInstrumentedTest {
    @Test fun enrollmentAndTtsUseTheirMatchingRuntimes() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val wav = File(context.cacheDir, "enrollment-regression.wav")
        inst.context.assets.open("enrollment.wav").use { src -> wav.outputStream().use { src.copyTo(it) } }
        val samples = decodeWaveFile(wav)
        val gate = SpeakerGate(context.assets)
        try {
            assertEquals(1, gate.enroll(samples))
            // Loading Java ORT must not prevent the Sherpa JNI from loading its own ORT.
            VieneuEngine(context).use { engine ->
                assertTrue(engine.phonemes("Xin chào").isNotBlank())
                assertEquals(2, gate.enroll(samples))
                assertEquals(3, gate.enroll(samples))
                assertTrue(gate.ready)
                val result = gate.verify(samples, 0.6f)
                assertTrue(result.scores.isNotEmpty())
                assertTrue(result.scores.all { it.isFinite() })
                assertTrue(engine.synthesize("Xin chào bạn.", greedy = true).isNotEmpty())
            }
            gate.clear(); assertEquals(0, gate.count)
        } finally { gate.release() }
    }
}
