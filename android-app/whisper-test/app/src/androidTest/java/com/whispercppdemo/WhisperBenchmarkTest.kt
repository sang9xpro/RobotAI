package com.whispercppdemo

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whispercpp.whisper.WhisperContext
import com.whispercppdemo.media.decodeWaveFile
import com.whispercppdemo.tts.VieneuEngine
import com.whispercppdemo.speaker.SpeakerGate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WhisperBenchmarkTest {
    @Test fun benchmarkSameWave() = runBlocking {
        val inst = InstrumentationRegistry.getInstrumentation()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
        val app = inst.targetContext
        val args = InstrumentationRegistry.getArguments()
        val name = args.getString("model") ?: "ggml-phowhisper-small.bin"
        val gpu = args.getString("gpu") != "false"
        val file = File(app.filesDir, "models/$name"); file.parentFile!!.mkdirs()
        if (!file.exists()) app.assets.open("models/$name").use { src -> file.outputStream().use { src.copyTo(it) } }
        val wav = File(app.cacheDir, "benchmark.wav")
        inst.context.assets.open("enrollment.wav").use { src -> wav.outputStream().use { src.copyTo(it) } }
        val audio = decodeWaveFile(wav)
        val report = JSONObject().put("model", name).put("gpu", gpu).put("audioSeconds", audio.size / 16000.0)
        val runs = JSONArray(); report.put("runs", runs)
        val output = File(app.cacheDir, "benchmark-$name-${if (gpu) "gpu" else "cpu"}.json")
        output.writeText(report.toString(2))
        val start = SystemClock.elapsedRealtime()
        val context = WhisperContext.createContextFromFile(file.absolutePath, gpu)
        try {
            report.put("loadMs", SystemClock.elapsedRealtime() - start).put("backend", if (gpu) WhisperContext.getGpuInfo() else "CPU")
            repeat(args.getString("repeats")?.toInt() ?: 2) { index ->
                val t = SystemClock.elapsedRealtime()
                val text = context.transcribeData(audio, false).trim()
                val elapsed = SystemClock.elapsedRealtime() - t
                assertTrue(text.isNotBlank())
                runs.put(JSONObject().put("index", index).put("elapsedMs", elapsed).put("rtf", elapsed / 1000.0 / (audio.size / 16000.0))
                    .put("text", text).put("timings", context.timings()).put("pssKiB", android.os.Debug.getPss()))
                output.writeText(report.toString(2))
            }
            if (args.getString("tts") == "true") {
                VieneuEngine(app).use { tts ->
                    val t = SystemClock.elapsedRealtime(); var first = -1L; var chunks = 0
                    val pcm = tts.synthesize("Xin chào bạn.", greedy = true, onAudio = {
                        if (first < 0) first = SystemClock.elapsedRealtime() - t
                        chunks++
                    })
                    assertTrue(pcm.isNotEmpty() && chunks > 0)
                    val gate = SpeakerGate(app.assets)
                    try {
                        repeat(3) { gate.enroll(audio) }
                        assertTrue(gate.verify(audio, 0.6f).accepted)
                    } finally { gate.release() }
                    report.put("coexistence", JSONObject().put("ttsFirstPcmMs", first).put("ttsChunks", chunks)
                        .put("speakerGatePassed", true).put("pssKiB", android.os.Debug.getPss()))
                    output.writeText(report.toString(2))
                }
            }
        } finally { context.release() }
        } finally { scenario.close() }
    }
}
