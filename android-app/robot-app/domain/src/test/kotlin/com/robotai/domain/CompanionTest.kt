package com.robotai.domain
import org.junit.Test
import org.junit.Assert.*
class CompanionTest {
    @Test fun percentilesHandleThirtyTurnsAndEmpty() {
        assertNull(LatencyStats.percentile(emptyList(), 95))
        assertEquals(15L, LatencyStats.percentile((1L..30L).toList(), 50))
        assertEquals(29L, LatencyStats.percentile((1L..30L).toList(), 95))
    }
    @Test fun safetyRejectsReplayStaleTelemetryExpiryAndHazards() {
        val gate = RobotCommandGate()
        val c = RobotCommand(1, RobotAction.FORWARD, 1500, 500, 20)
        val t = RobotTelemetry(1000, true)
        assertNull(gate.validate(c, t, 1000))
        assertNotNull(gate.validate(c, t, 1000))
        assertNotNull(gate.validate(c.copy(id=2, expiresAt=1000), t, 1000))
        assertNotNull(gate.validate(c.copy(id=2), t.copy(receivedAt=0), 1000))
        assertNotNull(gate.validate(c.copy(id=2), t.copy(cliff=true), 1000))
        assertNotNull(gate.validate(c.copy(id=2, speed=31), t, 1000))
        assertNull(gate.validate(c.copy(action=RobotAction.STOP), t.copy(connected=false), 9000))
    }
    @Test fun transportStopsOnSilenceAndDuration() {
        val transport = SimulatedRobotTransport(); val link = RobotLink(transport)
        assertNull(link.propose(RobotCommand(1, RobotAction.FORWARD, 3000, 1000, 20), 1000))
        assertEquals(RobotAction.FORWARD, transport.action)
        transport.frozen = true; link.tick(1800)
        assertEquals(RobotAction.STOP, transport.action)
        transport.frozen = false
        link.propose(RobotCommand(2, RobotAction.LEFT, 3500, 500, 20), 2000)
        link.tick(2501); assertEquals(RobotAction.STOP, transport.action)
    }
    @Test fun moodFollowsSpeechTouchAndSleep() {
        val mind = CompanionMind()
        mind.sentence("Chúc mừng bạn!", 1000)
        assertEquals(Mood.HAPPY, mind.mood(Phase.SPEAKING, 1001))
        mind.touch(2000); assertEquals(Mood.HAPPY, mind.mood(Phase.IDLE, 2001))
        assertEquals(Mood.SLEEPY, mind.mood(Phase.SLEEPING, 2001))
    }
}
