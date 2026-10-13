package com.robotai.domain

import java.util.UUID

data class ListeningEvidence(val visible: Boolean = false, val facing: Boolean = false,
    val activeSpeaker: Boolean = false, val cameraAgeMs: Long = -1, val people: Int = 0,
    val overlapping: Boolean = false, val uncertain: Boolean = false) {
    val visualAdmission get() = visible && facing && activeSpeaker && people == 1 && cameraAgeMs in 0..500
}
data class IndividualAdmission(val conversationId: String, val revision: Long, val reason: String,
    val accepted: Boolean, val owner: String?, val status: String)

/** Policy has no Android/model/network dependency. Clock and recognition evidence are supplied. */
class IndividualListening {
    private var owner: String? = null
    private var status = "unknown"
    private var conversation = UUID.randomUUID().toString()
    private var revision = 0L
    private var acceptedAt = -1L
    private var completedAt = -1L
    fun admit(person: String?, identityStatus: String, evidence: ListeningEvidence, now: Long,
        wake: Boolean = false, manual: Boolean = false, interruption: Boolean = false): IndividualAdmission {
        val same = person != null && person == owner && identityStatus == status && acceptedAt >= 0 && now-acceptedAt in 0 until 180000
        val continuation = same && !evidence.uncertain && (evidence.visualAdmission || completedAt >= 0 && now-completedAt in 0..30000)
        val reason = when { manual -> "manual"; wake -> "wake"; interruption && same && !evidence.uncertain && !evidence.overlapping -> "interruption"; continuation -> "continuation";
            identityStatus == "recognized" && evidence.visualAdmission && !evidence.uncertain -> "face_voice"; else -> "wake" }
        val allowed = manual || wake || !evidence.overlapping && (continuation || reason == "face_voice" || reason == "interruption")
        if (allowed) {
            if (!same) { conversation = UUID.randomUUID().toString(); completedAt = -1L }
            owner = person; status = identityStatus; acceptedAt = now
        }
        return IndividualAdmission(conversation, ++revision, reason, allowed, person, identityStatus)
    }
    fun owns(person: String?, identityStatus: String, now: Long) = person != null && person == owner && identityStatus == status && acceptedAt >= 0 && now-acceptedAt in 0 until 180000
    fun playbackCompleted(now: Long) { completedAt = now }
    fun reset() { owner = null; status = "unknown"; acceptedAt = -1; completedAt = -1; conversation = UUID.randomUUID().toString(); revision = 0 }
}

/** Only one temporary voice anchor. Short/unknown chunks never update it. */
class TemporarySpeaker {
    private var id: String? = null
    private var vector: FloatArray? = null
    private var at = -1L
    fun match(windows: List<FloatArray>, now: Long, threshold: Float): String? {
        expire(now)
        val anchor = vector ?: return null
        return id?.takeIf { windows.isNotEmpty() && windows.all { IdentityMath.cosine(anchor, it) >= threshold } }
    }
    fun remember(windows: List<FloatArray>, speechMs: Long, now: Long, threshold: Float): String? {
        if (speechMs < 3000 || windows.isEmpty() || windows.any { IdentityMath.cosine(windows.first(), it) < threshold }) return null
        clear(); id = UUID.randomUUID().toString()
        vector = IdentityMath.normalize(FloatArray(windows.first().size) { i -> windows.map { it[i] }.average().toFloat() })
        at = now; return id
    }
    fun touch(now: Long) { expire(now); if(vector != null) at = now }
    fun expire(now: Long) { if(at >= 0 && now-at !in 0 until 180000) clear() }
    fun clear() { vector?.fill(0f); vector = null; id = null; at = -1 }
}
