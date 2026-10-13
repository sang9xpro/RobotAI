package com.robotai.robot.identity

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.*
import com.robotai.domain.IdentityMath
import com.robotai.domain.SpeakerAudioWindows

class VoiceEmbedding(private val assets: AssetManager) : AutoCloseable {
    private val extractor = SpeakerEmbeddingExtractor(assets, SpeakerEmbeddingExtractorConfig(
        model="speaker/campplus.onnx", numThreads=2, debug=false, provider="cpu"))
    private val vad = Vad(assets, VadModelConfig(sileroVadModelConfig=SileroVadModelConfig(
        model="speaker/silero_vad.onnx", threshold=.5f, minSilenceDuration=.35f, minSpeechDuration=.25f,
        windowSize=512, maxSpeechDuration=30f), sampleRate=16000, numThreads=1, debug=false, provider="cpu"))
    var lastSpeechMs: Long = 0
        private set
    fun windows(samples: FloatArray, enrollment: Boolean = false): List<FloatArray> {
        vad.reset()
        val parts=mutableListOf<FloatArray>()
        fun drain() { while (!vad.empty()) { parts.add(vad.front().samples.copyOf()); vad.pop() } }
        for (start in samples.indices.step(512)) {
            val frame=FloatArray(512); samples.copyInto(frame,0,start,minOf(start+512,samples.size))
            vad.acceptWaveform(frame); drain()
        }
        vad.flush(); drain()
        lastSpeechMs = parts.sumOf { it.size.toLong() } * 1000 / 16000
        return SpeakerAudioWindows.prepare(parts,enrollment).map { part ->
            val stream=extractor.createStream()
            try {
                stream.acceptWaveform(part,16000); stream.inputFinished()
                require(extractor.isReady(stream)) { "Chưa đủ âm thanh để kiểm tra mẫu giọng" }
                IdentityMath.normalize(extractor.compute(stream))
            } finally { stream.release() }
        }
    }
    fun shortWindow(samples: FloatArray): FloatArray? {
        if(samples.size < 9600) return null
        vad.reset()
        val parts=mutableListOf<FloatArray>()
        fun drain() { while(!vad.empty()) { parts.add(vad.front().samples.copyOf());vad.pop() } }
        for(start in samples.indices.step(512)) {
            val frame=FloatArray(512);samples.copyInto(frame,0,start,minOf(start+512,samples.size))
            vad.acceptWaveform(frame);drain()
        }
        vad.flush();drain()
        if(parts.sumOf { it.size } < 9600) return null
        val speech=FloatArray(parts.sumOf { it.size });var offset=0
        parts.forEach { it.copyInto(speech,offset);offset+=it.size;it.fill(0f) }
        val stream=extractor.createStream()
        return try {
            stream.acceptWaveform(speech,16000);stream.inputFinished()
            if(extractor.isReady(stream)) IdentityMath.normalize(extractor.compute(stream)) else null
        } finally { speech.fill(0f);stream.release() }
    }
    override fun close() { vad.release(); extractor.release() }
}
