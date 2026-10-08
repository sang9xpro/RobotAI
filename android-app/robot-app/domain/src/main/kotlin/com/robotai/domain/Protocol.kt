package com.robotai.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AudioFrame(val generation: Long, val turn: Long, val seq: Long, val payload: ByteArray)

object Rai1 {
    const val HEADER_SIZE = 24
    const val MAX_PAYLOAD = 4096
    fun encode(frame: AudioFrame): ByteArray {
        require(frame.generation in 0..0xffffffffL && frame.turn > 0 && frame.seq in 0..0xffffffffL)
        require(frame.payload.size in 1..MAX_PAYLOAD)
        return ByteBuffer.allocate(HEADER_SIZE + frame.payload.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(0x52414931).putInt(frame.generation.toInt()).putLong(frame.turn)
            .putInt(frame.seq.toInt()).putInt(frame.payload.size).put(frame.payload).array()
    }
    fun decode(bytes: ByteArray): AudioFrame {
        require(bytes.size >= HEADER_SIZE) { "Header thiếu byte" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        require(b.int == 0x52414931) { "Sai magic RAI1" }
        val generation = b.int.toLong() and 0xffffffffL
        val turn = b.long
        val seq = b.int.toLong() and 0xffffffffL
        val size = b.int
        require(turn > 0 && size in 1..MAX_PAYLOAD && b.remaining() == size) { "Sai kích thước packet" }
        return AudioFrame(generation, turn, seq, ByteArray(size).also { b.get(it) })
    }
}

/** Used by the session owner before any text reaches UI or audio reaches the decoder. */
class TurnGuard {
    var generation = 0L; private set
    var turn = 0L; private set
    private var canceled = true
    private var audioSeq = -1L
    private var revision = -1
    private var final = false
    fun reset(generation: Long) {
        this.generation = generation
        turn = 0
        canceled = true
    }
    fun begin(): Long {
        turn++
        canceled = false
        audioSeq = -1
        revision = -1
        final = false
        return turn
    }
    fun cancel() { canceled = true }
    fun accepts(generation: Long, turn: Long) = !canceled && generation == this.generation && turn == this.turn
    fun acceptAudio(frame: AudioFrame): Boolean {
        if (!accepts(frame.generation, frame.turn) || frame.seq <= audioSeq) return false
        require(frame.seq == audioSeq + 1) { "Audio mất thứ tự" }
        audioSeq = frame.seq
        return true
    }
    fun acceptText(generation: Long, turn: Long, revision: Int, isFinal: Boolean): Boolean {
        if (!accepts(generation, turn) || final || revision <= this.revision) return false
        this.revision = revision
        final = isFinal
        return true
    }
}

enum class Phase { IDLE, LISTENING, FINALIZING, THINKING, SPEAKING, INTERRUPTED, ERROR, SLEEPING }

data class RobotUiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val phase: Phase = Phase.IDLE,
    val caption: String = "",
    val reply: String = "",
    val spokenSentence: String = "",
    val speechEmotion: String = "",
    val message: String = "Chưa kết nối backend giả lập",
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val discarded: Int = 0,
    val serverFrames: Int = 0,
    val decodedSamples: Int = 0,
    val latencyMs: Long? = null
)

interface BackendPort {
    fun connect(url: String, onText: (String) -> Unit, onAudio: (ByteArray) -> Unit, onClosed: (String) -> Unit)
    fun sendText(text: String): Boolean
    fun sendAudio(audio: ByteArray): Boolean
    fun close()
}

interface WakeWordPort {
    fun start(phrase: String, onWake: () -> Unit, onUnavailable: (String) -> Unit)
    fun stop()
}

/** Require the whole short call, never trigger on a name mentioned inside a conversation. */
object WakePhrase {
    fun normalize(text: String): String = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
        .lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .trim().replace(Regex("\\s+"), " ")
    fun matches(expected: String, candidates: List<String>): Boolean {
        val phrase = normalize(expected)
        return phrase.isNotBlank() && candidates.any { normalize(it) in setOf(phrase, "$phrase ơi") }
    }
}
