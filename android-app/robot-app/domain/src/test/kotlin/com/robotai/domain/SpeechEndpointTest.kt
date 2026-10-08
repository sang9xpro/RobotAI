package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class SpeechEndpointTest {
    private val silence = ShortArray(960)
    private val voice = ShortArray(960) { if (it % 2 == 0) 2000 else -2000 }
    @Test fun silenceNeverCreatesAnEmptyTurn() {
        val endpoint = SpeechEndpoint()
        repeat(3000) { assertEquals(SpeechEndpoint.Event.WAITING, endpoint.accept(silence)) }
        assertFalse(endpoint.started)
    }
    @Test fun ignoresClicksAndAllowsPausesInsideASentence() {
        val endpoint = SpeechEndpoint()
        endpoint.accept(voice)
        endpoint.accept(silence)
        assertFalse(endpoint.started)
        repeat(2) { assertEquals(SpeechEndpoint.Event.WAITING, endpoint.accept(voice)) }
        assertEquals(SpeechEndpoint.Event.START, endpoint.accept(voice))
        repeat(19) { assertEquals(SpeechEndpoint.Event.SILENCE, endpoint.accept(silence)) }
        assertEquals(SpeechEndpoint.Event.SPEECH, endpoint.accept(voice))
        repeat(19) { endpoint.accept(silence) }
        assertEquals(SpeechEndpoint.Event.END, endpoint.accept(silence))
    }
    @Test fun boundsContinuousSpeechToThirtySeconds() {
        val endpoint = SpeechEndpoint()
        repeat(3) { endpoint.accept(voice) }
        repeat(499) { assertEquals(SpeechEndpoint.Event.SPEECH, endpoint.accept(voice)) }
        assertEquals(SpeechEndpoint.Event.END, endpoint.accept(voice))
    }
}
