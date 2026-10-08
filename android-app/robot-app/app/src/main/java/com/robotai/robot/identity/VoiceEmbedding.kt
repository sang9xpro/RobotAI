package com.robotai.robot.identity

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.*
import com.robotai.domain.IdentityMath

class VoiceEmbedding(private val assets: AssetManager) : AutoCloseable {
    private val extractor = SpeakerEmbeddingExtractor(assets, SpeakerEmbeddingExtractorConfig(
        model="speaker/campplus.onnx", numThreads=2, debug=false, provider="cpu"))
    private val vad = Vad(assets, VadModelConfig(sileroVadModelConfig=SileroVadModelConfig(
        model="speaker/silero_vad.onnx", threshold=.5f, minSilenceDuration=.35f, minSpeechDuration=.25f,
        windowSize=512, maxSpeechDuration=30f), sampleRate=16000, numThreads=1, debug=false, provider="cpu"))
    fun windows(samples: FloatArray, enrollment: Boolean = false): List<FloatArray> {
        vad.reset()
        val parts=mutableListOf<FloatArray>()
        fun drain() { while (!vad.empty()) { parts.add(vad.front().samples.copyOf()); vad.pop() } }
        for (start in samples.indices.step(512)) {
            val frame=FloatArray(512); samples.copyInto(frame,0,start,minOf(start+512,samples.size))
            vad.acceptWaveform(frame); drain()
        }
        vad.flush(); drain()
        require(parts.isNotEmpty()) { "Không đủ lời nói; hãy nói rõ một câu 3–5 giây" }
        if (enrollment) require(parts.sumOf { it.size } >= 48000) { "Cần ít nhất 3 giây lời nói" }
        return parts.flatMap { part ->
            require(part.size >= 32000) { "Có đoạn quá ngắn để xác định người nói" }
            val result=mutableListOf<FloatArray>(); var start=0
            while (start < part.size) {
                val end=if (part.size-start <= 80000) part.size else start+48000
                val stream=extractor.createStream()
                try {
                    stream.acceptWaveform(part.copyOfRange(start,end),16000); stream.inputFinished()
                    require(extractor.isReady(stream))
                    result.add(IdentityMath.normalize(extractor.compute(stream)))
                } finally { stream.release() }
                start=end
            }
            result
        }
    }
    override fun close() { vad.release(); extractor.release() }
}
