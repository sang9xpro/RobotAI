package com.robotai.domain

/** 16 kHz speech selected by VAD; silence never counts toward enrollment duration. */
object SpeakerAudioWindows {
    const val SAMPLE_RATE = 16000
    const val ENROLLMENT_SAMPLES = SAMPLE_RATE * 8
    private const val MIN_WINDOW = SAMPLE_RATE * 2
    private const val MIN_ENROLLMENT = SAMPLE_RATE * 3
    private const val MAX_WINDOW = SAMPLE_RATE * 5

    fun prepare(parts: List<FloatArray>, enrollment: Boolean): List<FloatArray> {
        val speech = parts.filter { it.isNotEmpty() }
        require(speech.isNotEmpty()) { "Chưa nghe rõ lời nói. Hãy đọc một câu trong lúc thu mẫu." }
        val sources = if (enrollment) {
            val size = speech.sumOf { it.size }
            require(size >= MIN_ENROLLMENT) { "Mẫu chưa đủ 3 giây lời nói. Hãy thu lại và đọc câu dài hơn." }
            // One person enrolls at a time. Short phrases separated by pauses belong to
            // the same sample; a short VAD tail must not invalidate usable speech.
            val combined = FloatArray(size)
            var offset = 0
            for (part in speech) { part.copyInto(combined, offset); offset += part.size }
            listOf(combined)
        } else {
            // Conversation can contain several speakers. Preserve the existing unknown
            // result if any speech fragment is too brief to establish its speaker.
            require(speech.all { it.size >= MIN_WINDOW }) { "Câu nói quá ngắn để xác định người nói" }
            speech
        }
        return sources.flatMap { source ->
            val count = (source.size + MAX_WINDOW - 1) / MAX_WINDOW
            // Balanced windows avoid leaving a sub-two-second remainder.
            (0 until count).map { index ->
                source.copyOfRange(index * source.size / count, (index + 1) * source.size / count)
            }
        }
    }
}
