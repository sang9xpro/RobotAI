package com.robotai.domain

/** Only quiet, available states can sleep; response time never counts as inactivity. */
class ConversationIdle(private val timeoutMs: Long = 120_000) {
    private var lastActivity = 0L
    fun touch(now: Long) { lastActivity = now }
    fun shouldSleep(now: Long, phase: Phase): Boolean =
        phase in listOf(Phase.IDLE, Phase.INTERRUPTED, Phase.LISTENING) && now - lastActivity >= timeoutMs
}
