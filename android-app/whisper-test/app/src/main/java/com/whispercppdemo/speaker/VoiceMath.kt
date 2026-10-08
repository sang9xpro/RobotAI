package com.whispercppdemo.speaker

import kotlin.math.sqrt

/** Pure math, shared by enrollment and verification; scores are not probabilities. */
object VoiceMath {
    fun normalize(vector: FloatArray): FloatArray {
        require(vector.isNotEmpty() && vector.all { it.isFinite() }) { "Embedding không hợp lệ" }
        val norm = sqrt(vector.sumOf { it.toDouble() * it })
        require(norm > 1e-8) { "Embedding rỗng" }
        return FloatArray(vector.size) { (vector[it] / norm).toFloat() }
    }

    fun centroid(vectors: List<FloatArray>): FloatArray {
        require(vectors.isNotEmpty())
        val normalized = vectors.map(::normalize)
        require(normalized.all { it.size == normalized.first().size })
        return normalize(FloatArray(normalized.first().size) { i ->
            normalized.sumOf { it[i].toDouble() }.toFloat() / normalized.size
        })
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size)
        val x = normalize(a); val y = normalize(b)
        return x.indices.sumOf { x[it].toDouble() * y[it] }.toFloat().coerceIn(-1f, 1f)
    }

    fun accepts(scores: List<Float>, threshold: Float): Boolean =
        scores.isNotEmpty() && scores.all { it.isFinite() && it >= threshold }

    /** Simple energy check, not a speech/noise classifier or speaker separator. */
    fun prepareAudio(samples: FloatArray, sampleRate: Int = 16000): FloatArray {
        require(samples.all { it.isFinite() })
        val frameSize = sampleRate / 50
        val active = samples.indices.step(frameSize).filter { start ->
            val end = minOf(start + frameSize, samples.size)
            sqrt((start until end).sumOf { samples[it].toDouble() * samples[it] } / (end - start)) >= 0.008
        }
        require(active.size >= 75) { "Âm thanh quá nhỏ hoặc quá ít lời nói. Hãy nói rõ ít nhất 3–5 giây." }
        val start = maxOf(0, active.first() - frameSize * 10)
        val end = minOf(samples.size, active.last() + frameSize * 11)
        require(end - start >= sampleRate * 2) { "Đoạn nói quá ngắn để kiểm tra giọng" }
        return samples.copyOfRange(start, end)
    }

    /** Merge a short tail so no audio at the end is silently skipped. */
    fun windows(samples: FloatArray, sampleRate: Int = 16000): List<FloatArray> {
        val result = mutableListOf<FloatArray>()
        var start = 0
        while (start < samples.size) {
            var end = minOf(start + sampleRate * 3, samples.size)
            if (samples.size - end < sampleRate * 2) end = samples.size
            result.add(samples.copyOfRange(start, end))
            start = end
        }
        return result
    }
}
