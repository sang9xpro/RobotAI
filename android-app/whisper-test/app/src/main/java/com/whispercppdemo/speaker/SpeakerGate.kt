package com.whispercppdemo.speaker

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig

/** Call only on the ViewModel's serial native dispatcher. Profile stays in RAM. */
class SpeakerGate(private val assets: AssetManager) {
    private var extractor: SpeakerEmbeddingExtractor? = null
    private var vad: Vad? = null
    private val enrollment = mutableListOf<FloatArray>()
    val count get() = enrollment.size
    val ready get() = count >= 3

    private fun speechSegments(samples: FloatArray): List<FloatArray> {
        val detector = vad ?: Vad(assets, VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(model = "speaker/silero_vad.onnx",
                threshold = 0.5f, minSilenceDuration = 0.5f, minSpeechDuration = 0.25f,
                windowSize = 512, maxSpeechDuration = 30f),
            sampleRate = 16000, numThreads = 1, provider = "cpu", debug = false
        )).also { vad = it }
        detector.reset()
        val segments = mutableListOf<FloatArray>()
        fun drain() {
            while (!detector.empty()) {
                segments.add(detector.front().samples.copyOf())
                detector.pop()
            }
        }
        for (start in samples.indices.step(512)) {
            val frame = FloatArray(512)
            samples.copyInto(frame, 0, start, minOf(start + 512, samples.size))
            detector.acceptWaveform(frame)
            drain()
        }
        detector.flush()
        drain()
        require(segments.isNotEmpty()) { "Không phát hiện lời nói. Hãy nói rõ hơn." }
        return segments
    }

    private fun joinSpeech(segments: List<FloatArray>): FloatArray {
        val gap = 3200 // Preserve a small pause between utterances for Whisper.
        val output = FloatArray(segments.sumOf { it.size } + gap * (segments.size - 1))
        var offset = 0
        segments.forEach { it.copyInto(output, offset); offset += it.size + gap }
        return output
    }

    private fun embedding(samples: FloatArray): FloatArray {
        val engine = extractor ?: SpeakerEmbeddingExtractor(
            assets,
            SpeakerEmbeddingExtractorConfig(model = "speaker/campplus.onnx", numThreads = 2, debug = false, provider = "cpu")
        ).also { extractor = it }
        val stream = engine.createStream()
        try {
            stream.acceptWaveform(samples, 16000)
            stream.inputFinished()
            check(engine.isReady(stream)) { "Chưa đủ audio để tạo mẫu giọng" }
            return VoiceMath.normalize(engine.compute(stream))
        } finally { stream.release() }
    }

    fun enroll(samples: FloatArray): Int {
        check(!ready) { "Đã đủ 3 mẫu; xóa mẫu giọng để đăng ký lại" }
        val segments = speechSegments(VoiceMath.prepareAudio(samples))
        require(segments.sumOf { it.size } >= 16000 * 3) { "Mẫu đăng ký cần ít nhất 3 giây lời nói" }
        val audio = joinSpeech(segments)
        enrollment.add(embedding(audio))
        return count
    }

    fun verify(samples: FloatArray, threshold: Float): Verification {
        check(ready) { "Cần đăng ký đủ 3 mẫu giọng trước" }
        val segments = speechSegments(VoiceMath.prepareAudio(samples))
        require(segments.all { it.size >= 16000 * 2 }) { "Có đoạn nói quá ngắn để xác minh giọng. Hãy thử một câu liền mạch 3–5 giây." }
        val reference = VoiceMath.centroid(enrollment)
        val scores = segments.flatMap { VoiceMath.windows(it) }.map { VoiceMath.cosine(reference, embedding(it)) }
        return Verification(VoiceMath.accepts(scores, threshold), scores, joinSpeech(segments))
    }

    fun clear() = enrollment.clear()
    fun release() { extractor?.release(); extractor = null; vad?.release(); vad = null; clear() }
}

data class Verification(val accepted: Boolean, val scores: List<Float>, val audio: FloatArray)
