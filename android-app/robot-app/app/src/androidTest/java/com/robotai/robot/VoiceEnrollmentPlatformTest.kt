package com.robotai.robot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.*
import com.robotai.domain.IdentityMath
import com.robotai.robot.identity.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class VoiceEnrollmentPlatformTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pausedShortPhrasesEnrollAndFailedRetryPreservesExistingVoice() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val raw = instrumentation.context.assets.open("identity-voice.wav").use { it.readBytes() }
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        var cursor = 12
        var samples = FloatArray(0)
        while (cursor + 8 <= raw.size) {
            val count = buffer.getInt(cursor + 4)
            if (String(raw, cursor, 4, Charsets.US_ASCII) == "data") {
                samples = FloatArray(count / 2) { buffer.getShort(cursor + 8 + it * 2) / 32768f }
                break
            }
            cursor += 8 + count + count % 2
        }
        val original = speechParts(samples)
        val source = original.maxBy { it.size }
        assertTrue(source.size > 28000)
        val phrase = source.copyOfRange(8000, 28000) // 1.25 seconds from a real voice fixture.
        val paused = FloatArray(4 * (phrase.size + 10400))
        repeat(4) { phrase.copyInto(paused, it * (phrase.size + 10400)) }
        val parts = speechParts(paused)
        assertTrue("Fixture must reproduce short VAD fragments", parts.size >= 3 && parts.all { it.size < 32000 })
        assertTrue("Fixture needs three seconds of speech", parts.sumOf { it.size } >= 48000)
        VoiceEmbedding(context.assets).use { model ->
            val embeddings = model.windows(paused, true)
            assertTrue(embeddings.isNotEmpty())
            assertTrue(embeddings.all { embedding -> embedding.all { it.isFinite() } })
            assertTrue(IdentityMath.cosine(embeddings.first(), embeddings.first()) > .999f)
        }
        val key = "test-paused-enrollment-${UUID.randomUUID()}"
        val controller = IdentityController(context, key)
        val store = PeopleStore(context, key)
        try {
            controller.add("Người thử ngắt nghỉ", "bạn")
            compose.waitUntil(10000) { controller.state.value.people.size == 1 }
            val id = controller.state.value.people.single().id
            controller.saveVoice(id, paused)
            assertNull(controller.state.value.error)
            val accepted = store.read().single().voices.single().copyOf()
            controller.saveVoice(id, phrase)
            assertNotNull(controller.state.value.error)
            assertEquals(1, store.read().single().voices.size)
            assertArrayEquals(accepted, store.read().single().voices.single(), 0f)
            controller.voiceBusy(true)
            compose.waitUntil(5000) { controller.state.value.voiceBusy && controller.state.value.error == null }
            controller.voiceBusy(false)
        } finally { controller.close(); store.write(emptyList()) }
    }

    private fun speechParts(samples: FloatArray): List<FloatArray> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vad = Vad(context.assets, VadModelConfig(sileroVadModelConfig = SileroVadModelConfig(
            model = "speaker/silero_vad.onnx", threshold = .5f, minSilenceDuration = .35f,
            minSpeechDuration = .25f, windowSize = 512, maxSpeechDuration = 30f),
            sampleRate = 16000, numThreads = 1, debug = false, provider = "cpu"))
        try {
            val parts = mutableListOf<FloatArray>()
            fun drain() { while (!vad.empty()) { parts.add(vad.front().samples.copyOf()); vad.pop() } }
            for (start in samples.indices.step(512)) {
                val frame = FloatArray(512)
                samples.copyInto(frame, 0, start, minOf(start + 512, samples.size))
                vad.acceptWaveform(frame); drain()
            }
            vad.flush(); drain()
            return parts
        } finally { vad.release() }
    }
}
