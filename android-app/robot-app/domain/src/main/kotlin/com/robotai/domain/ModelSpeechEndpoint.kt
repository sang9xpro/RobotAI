package com.robotai.domain

/** 60 ms frames, supplied by the speech model rather than a loudness threshold. */
class ModelSpeechEndpoint {
    enum class Event { WAITING, START, SPEECH, SILENCE, END }
    private var voiced = 0
    private var quiet = 0
    private var duration = 0
    private var wakeOnly = false
    var started = false
        private set
    fun waitingForRequest(value: Boolean) { wakeOnly = value }
    fun accept(speech: Boolean): Event {
        if (!started) {
            voiced = if (speech) voiced + 1 else 0
            if (voiced < 3) return Event.WAITING
            started = true
            duration = 3
            return Event.START
        }
        duration++
        quiet = if (speech) 0 else quiet + 1
        return when {
            duration >= 500 || quiet >= if (wakeOnly) 67 else 20 -> Event.END
            speech -> Event.SPEECH
            else -> Event.SILENCE
        }
    }
}
