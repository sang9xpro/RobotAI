package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class ContractTest {
    @Test fun binaryFixtureUsesNetworkByteOrder() {
        val bytes = Rai1.encode(AudioFrame(1, 2, 3, byteArrayOf(0x12, 0x34)))
        assertEquals("5241493100000001000000000000000200000003000000021234", bytes.joinToString("") { "%02x".format(it) })
        val decoded = Rai1.decode(bytes)
        assertEquals(2L, decoded.turn)
        assertArrayEquals(byteArrayOf(0x12, 0x34), decoded.payload)
    }
    @Test fun rejectsMalformedPayload() {
        val good = Rai1.encode(AudioFrame(1, 1, 0, byteArrayOf(1)))
        assertThrows(IllegalArgumentException::class.java) { Rai1.decode(good.copyOf(good.size - 1)) }
        good[0] = 0
        assertThrows(IllegalArgumentException::class.java) { Rai1.decode(good) }
    }
    @Test fun abortedTextAndAudioCannotReachNewTurn() {
        val guard = TurnGuard()
        guard.reset(1); guard.begin(); guard.cancel()
        assertFalse(guard.acceptText(1, 1, 99, true))
        assertFalse(guard.acceptAudio(AudioFrame(1, 1, 0, byteArrayOf(1))))
        guard.begin()
        assertFalse(guard.acceptAudio(AudioFrame(1, 1, 0, byteArrayOf(1))))
        assertTrue(guard.acceptAudio(AudioFrame(1, 2, 0, byteArrayOf(1))))
    }
    @Test fun captionRevisionAndFinalAreMonotonic() {
        val guard = TurnGuard(); guard.reset(1); guard.begin()
        assertTrue(guard.acceptText(1, 1, 2, false))
        assertFalse(guard.acceptText(1, 1, 1, false))
        assertTrue(guard.acceptText(1, 1, 3, true))
        assertFalse(guard.acceptText(1, 1, 4, false))
    }
    @Test fun reconnectInvalidatesOldGeneration() {
        val guard = TurnGuard(); guard.reset(1); guard.begin(); guard.reset(2); guard.begin()
        assertFalse(guard.acceptText(1, 1, 1, true))
        assertTrue(guard.acceptText(2, 1, 1, true))
    }
    @Test fun duplicatesAreDroppedButGapsFail() {
        val guard = TurnGuard(); guard.reset(1); guard.begin()
        assertTrue(guard.acceptAudio(AudioFrame(1, 1, 0, byteArrayOf(1))))
        assertFalse(guard.acceptAudio(AudioFrame(1, 1, 0, byteArrayOf(1))))
        assertThrows(IllegalArgumentException::class.java) { guard.acceptAudio(AudioFrame(1, 1, 2, byteArrayOf(1))) }
    }
}
