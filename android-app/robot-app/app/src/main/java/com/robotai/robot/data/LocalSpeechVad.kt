package com.robotai.robot.data

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.*

/** Silero consumes 512 samples; preserve the remainder between 60 ms capture frames. */
class LocalSpeechVad(assets: AssetManager) : AutoCloseable {
    private val vad = Vad(assets, VadModelConfig(sileroVadModelConfig = SileroVadModelConfig(
        model = "speaker/silero_vad.onnx", threshold = .5f, minSilenceDuration = .1f,
        minSpeechDuration = .1f, windowSize = 512, maxSpeechDuration = 30f),
        sampleRate = 16000, numThreads = 1, debug = false, provider = "cpu"))
    private val pending = FloatArray(512)
    private var size = 0
    fun speech(pcm: ShortArray): Boolean {
        var detected = false
        for (sample in pcm) {
            pending[size++] = sample / 32768f
            if (size == 512) {
                detected = vad.compute(pending) >= .5f || detected
                size = 0
            }
        }
        return detected
    }
    override fun close() { pending.fill(0f); vad.release() }
}
