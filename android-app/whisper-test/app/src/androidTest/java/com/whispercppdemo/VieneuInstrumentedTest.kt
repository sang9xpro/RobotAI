package com.whispercppdemo

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whispercppdemo.tts.VieneuEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class VieneuInstrumentedTest {
    @Test fun offlineVietnameseSynthesis() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val load = SystemClock.elapsedRealtime()
        VieneuEngine(context).use { engine ->
            val loadMs = SystemClock.elapsedRealtime() - load
            val fixtures = JSONArray(instrumentation.context.assets.open("tts-fixtures.json").bufferedReader().readText())
            for (i in 0 until fixtures.length()) {
                val f = fixtures.getJSONObject(i)
                val phones = engine.phonemes(f.getString("text"))
                assertEquals("phonemes[$i]", f.getString("phones"), phones)
                val ids = f.getJSONArray("ids")
                assertArrayEquals("BPE[$i]", IntArray(ids.length()) { ids.getInt(it) }, engine.tokens(phones))
            }
            try { engine.synthesize("Xin chào", AtomicBoolean(true)); fail("Cancellation must stop synthesis") }
            catch (_: java.util.concurrent.CancellationException) { }
            val start = SystemClock.elapsedRealtime()
            val audio = engine.synthesize("Xin chào, mình là robot của bạn.", greedy = true)
            val elapsed = SystemClock.elapsedRealtime() - start
            assertTrue(audio.size > 48000)
            assertTrue(audio.all { it.isFinite() })
            assertTrue(audio.any { kotlin.math.abs(it) > 0.01f })
            val bytes = ByteBuffer.allocate(audio.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            audio.forEach { bytes.putFloat(it) }
            File(context.cacheDir, "tts-device.f32").writeBytes(bytes.array())
            File(context.cacheDir, "tts-device.json").writeText(JSONObject().put("loadMs", loadMs)
                .put("inferMs", elapsed).put("samples", audio.size).put("sampleRate", 48000)
                .put("pssKiB", android.os.Debug.getPss()).toString())
            val chunks = mutableListOf<FloatArray>()
            val streamStart = SystemClock.elapsedRealtime()
            var firstPcm = -1L
            val streamed = engine.synthesize("Xin chào, mình là robot của bạn.", greedy = true, onAudio = {
                if (firstPcm < 0) firstPcm = SystemClock.elapsedRealtime() - streamStart
                chunks += it
            })
            val streamMs = SystemClock.elapsedRealtime() - streamStart
            assertTrue(chunks.size > 1)
            assertTrue(firstPcm in 1 until streamMs)
            assertEquals(audio.size, streamed.size)
            val mae = audio.indices.sumOf { kotlin.math.abs(audio[it] - streamed[it]).toDouble() } / audio.size
            assertTrue("Full/stream MAE=$mae", mae < 0.001)
            File(context.cacheDir, "tts-stream-device.json").writeText(JSONObject().put("firstPcmMs", firstPcm)
                .put("inferMs", streamMs).put("chunks", chunks.size).put("samples", streamed.size).put("maeVsFull", mae).toString())
            val cancel = AtomicBoolean()
            try { engine.synthesize("Xin chào bạn, hôm nay bạn khỏe không?", cancel, greedy = true, onAudio = { cancel.set(true) }); fail("Stream cancellation") }
            catch (_: java.util.concurrent.CancellationException) { }
            // Reuse sessions a second time, which catches stale / prematurely closed KV tensors.
            assertTrue(engine.synthesize("Hôm nay bạn khỏe không?", greedy = true).size > 24000)
        }
    }
}
