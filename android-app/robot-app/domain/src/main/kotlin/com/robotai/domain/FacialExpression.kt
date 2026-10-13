package com.robotai.domain

import kotlin.math.exp

/** Order of the seven logits in the pinned OpenCV Zoo model. UNKNOWN is not a model class. */
enum class FacialExpression(val label: String) {
    ANGRY("Nhíu mày"), DISGUST("Chưa rõ"), FEARFUL("Chưa rõ"), HAPPY("Nụ cười"),
    NEUTRAL("Bình thường"), SAD("Nét buồn"), SURPRISED("Ngạc nhiên"), UNKNOWN("Chưa rõ")
}

data class ExpressionOwner(val personId: String, val trackId: Int, val faceTrack: Int)

/** Session-only evidence, never a diagnosis or a personal memory. */
data class KnownFaceExpression(
    val owner: ExpressionOwner,
    val expression: FacialExpression = FacialExpression.UNKNOWN,
    val score: Float = 0f,
    val seenAtMs: Long,
    val evidenceAtMs: Long = -1L,
    val x: Float = 0f
) {
    fun visible(now: Long) = now - seenAtMs in 0..750
    fun current(now: Long) = if (visible(now) && evidenceAtMs >= 0 && now - evidenceAtMs in 0..1500)
        expression else FacialExpression.UNKNOWN
}

object ExpressionScores {
    /** The model ends in Gemm, not Softmax. These scores are not calibrated correctness probabilities. */
    fun fromLogits(logits: FloatArray): FloatArray? {
        if (logits.size != 7 || logits.any { !it.isFinite() }) return null
        val highest = logits.maxOrNull() ?: return null
        val weights = DoubleArray(7) { exp((logits[it].toDouble() - highest)) }
        val total = weights.sum()
        return FloatArray(7) { (weights[it] / total).toFloat() }
    }
}

/** Time-based confirmation, separate from camera FPS and the identity recognition cadence. */
class FacialExpressionEngine {
    var state: KnownFaceExpression? = null
        private set
    private var smoothed: FloatArray? = null
    private var lastSample = -1L
    private var candidate = FacialExpression.UNKNOWN
    private var candidateAt = -1L
    private var samples = 0

    fun reset() {
        state = null
        clearCandidate()
        smoothed = null
        lastSample = -1L
    }

    fun present(owner: ExpressionOwner, now: Long, x: Float): KnownFaceExpression {
        val previous = state
        if (previous == null || previous.owner != owner || now - previous.seenAtMs !in 0..750) reset()
        val current = state ?: KnownFaceExpression(owner, seenAtMs = now)
        state = current.copy(seenAtMs = now, x = if (x.isFinite()) x.coerceIn(-1f, 1f) else 0f,
            expression = current.current(now), score = if (current.current(now) == FacialExpression.UNKNOWN) 0f else current.score)
        return state!!
    }

    fun observe(owner: ExpressionOwner, scores: FloatArray?, capturedAt: Long, now: Long): KnownFaceExpression? {
        val current = state ?: return null
        if (current.owner != owner || !current.visible(now) || capturedAt > current.seenAtMs ||
            now - capturedAt !in 0..500 || capturedAt <= lastSample) return current
        if (scores == null || scores.size != 7 || scores.any { !it.isFinite() || it !in 0f..1f } ||
            kotlin.math.abs(scores.sum() - 1f) > .01f) {
            clearCandidate(); smoothed = null; lastSample = capturedAt
            return current
        }
        val gap = if (lastSample < 0) Long.MAX_VALUE else capturedAt - lastSample
        if (gap > 750) { clearCandidate(); smoothed = null }
        val old = smoothed
        val alpha = if (old == null) 1f else (1.0 - exp(-gap / 250.0)).toFloat()
        val filtered = FloatArray(7) { i -> old?.let { it[i] + alpha * (scores[i] - it[i]) } ?: scores[i] }
        smoothed = filtered
        lastSample = capturedAt
        val ranked = filtered.indices.sortedByDescending { filtered[it] }
        val kind = FacialExpression.values()[ranked[0]]
        val score = filtered[ranked[0]]
        val minimum = when (kind) {
            FacialExpression.HAPPY -> .65f
            FacialExpression.NEUTRAL -> .60f
            FacialExpression.SAD -> .75f
            FacialExpression.SURPRISED -> .70f
            else -> 2f // Unsupported negative categories deliberately have no screen reaction.
        }
        val maintaining = current.current(now) == kind
        if (score < minimum - (if (maintaining) .08f else 0f) || score - filtered[ranked[1]] < .20f) {
            clearCandidate()
            return current
        }
        if (maintaining) {
            state = current.copy(score = score, evidenceAtMs = capturedAt)
            clearCandidate()
            return state
        }
        if (candidate != kind) { candidate = kind; candidateAt = capturedAt; samples = 0 }
        samples++
        val hold = when (kind) {
            FacialExpression.SAD -> 1600L
            FacialExpression.SURPRISED -> 900L
            else -> 650L
        }
        val count = if (kind == FacialExpression.SAD) 5 else 3
        if (capturedAt - candidateAt >= hold && samples >= count) {
            state = current.copy(expression = kind, score = score, evidenceAtMs = capturedAt)
            clearCandidate()
        }
        return state
    }

    private fun clearCandidate() { candidate = FacialExpression.UNKNOWN; candidateAt = -1L; samples = 0 }
}

data class VisualMood(val mood: Mood, val fromCamera: Boolean = false)

object VisualMoodPolicy {
    fun select(base: Mood, phase: Phase, observed: KnownFaceExpression?, now: Long,
        awake: Boolean, enabled: Boolean, speechActive: Boolean, directReaction: Boolean = false): VisualMood {
        if (!awake || !enabled || directReaction || observed?.visible(now) != true ||
            phase !in setOf(Phase.IDLE, Phase.INTERRUPTED, Phase.LISTENING) ||
            phase == Phase.LISTENING && speechActive) return VisualMood(base)
        return VisualMood(when (observed.current(now)) {
            FacialExpression.HAPPY -> Mood.HAPPY
            FacialExpression.SAD -> Mood.CARING
            FacialExpression.NEUTRAL -> Mood.CALM
            else -> Mood.CURIOUS
        }, fromCamera = true)
    }
}
