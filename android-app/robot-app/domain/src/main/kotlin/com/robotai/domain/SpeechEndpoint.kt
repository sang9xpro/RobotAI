package com.robotai.domain

import kotlin.math.sqrt

/** Local energy endpoint, 60 ms PCM frames. No speech means no empty turn. */
class SpeechEndpoint {
    enum class Event { WAITING, START, SPEECH, SILENCE, END }
    private var noise = 100.0
    private var voiced = 0
    private var quiet = 0
    private var duration = 0
    var started = false
        private set

    fun accept(pcm: ShortArray): Event {
        if (pcm.isEmpty()) return Event.WAITING
        val rms = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
        val voice = rms > maxOf(450.0, noise * 3.0)
        if (!started) {
            if (!voice) noise = noise * .97 + rms * .03
            voiced = if (voice) voiced + 1 else 0
            if (voiced < 3) return Event.WAITING
            started = true
            return Event.START
        }
        duration++
        quiet = if (voice) 0 else quiet + 1
        return when {
            quiet >= 20 || duration >= 500 -> Event.END
            voice -> Event.SPEECH
            else -> Event.SILENCE
        }
    }
}
