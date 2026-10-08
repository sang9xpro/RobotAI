package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class ConversationIdleTest {
    @Test fun sleepsAfterTwoMinutesWaitingForSpeech() {
        val idle = ConversationIdle()
        idle.touch(1000)
        assertFalse(idle.shouldSleep(120999, Phase.LISTENING))
        assertTrue(idle.shouldSleep(121000, Phase.LISTENING))
        idle.touch(121000)
        assertFalse(idle.shouldSleep(122000, Phase.LISTENING))
    }
    @Test fun neverSleepsDuringResponseOrWakesASleepingRobot() {
        val idle = ConversationIdle()
        for (phase in listOf(Phase.THINKING, Phase.FINALIZING, Phase.SPEAKING, Phase.SLEEPING, Phase.ERROR))
            assertFalse(idle.shouldSleep(300000, phase))
    }
}
