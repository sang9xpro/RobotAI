package com.robotai.domain

import kotlin.math.sqrt

data class PersonTemplate(val id: String, val samples: List<FloatArray>)
data class IdentityMatch(val personId: String, val score: Float)
object IdentityMath {
    fun normalize(v: FloatArray): FloatArray {
        require(v.isNotEmpty() && v.all { it.isFinite() })
        val norm = sqrt(v.sumOf { it.toDouble() * it }.toFloat())
        require(norm > 1e-6f && norm.isFinite())
        return FloatArray(v.size) { v[it] / norm }
    }
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return -1f
        return runCatching { val x = normalize(a); val y = normalize(b); x.indices.sumOf { (x[it] * y[it]).toDouble() }.toFloat() }.getOrDefault(-1f)
    }
    fun match(v: FloatArray, people: List<PersonTemplate>, threshold: Float, margin: Float = .08f): IdentityMatch? {
        require(threshold in 0f..1f && margin in 0f..1f)
        val scores = people.filter { it.samples.isNotEmpty() }.map { p ->
            IdentityMatch(p.id, p.samples.map { cosine(v, it) }.sortedDescending().take(3).average().toFloat())
        }.sortedByDescending { it.score }
        val first = scores.firstOrNull() ?: return null
        return first.takeIf { it.score >= threshold && (scores.size == 1 || it.score - scores[1].score >= margin) }
    }
}
/** A turn cannot silently change owner. Mixed speakers/unknown voice windows remain unknown. */
class SpeakerTurn {
    private val visible = mutableSetOf<String>()
    private var voice: String? = null
    private var uncertain = false
    private var windows = 0
    fun observeFace(id: String) { visible.add(id) }
    fun observeVoice(id: String?) {
        windows++
        if (id == null || (voice != null && voice != id)) uncertain = true
        if (id != null) voice = id
    }
    fun result(): Pair<String, String>? = voice?.takeIf { !uncertain && windows > 0 }?.let {
        it to if (it in visible) "face_voice" else "voice"
    }
}
