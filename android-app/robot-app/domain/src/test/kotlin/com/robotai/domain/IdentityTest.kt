package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test
class IdentityTest {
    @Test fun strangersAndAmbiguousMatchesAreRejected() {
        val people=listOf(PersonTemplate("a",listOf(floatArrayOf(1f,0f))), PersonTemplate("b",listOf(floatArrayOf(0f,1f))))
        assertEquals("a",IdentityMath.match(floatArrayOf(1f,.02f),people,.6f)?.personId)
        assertNull(IdentityMath.match(floatArrayOf(-1f,-1f),people,.6f))
        assertNull(IdentityMath.match(floatArrayOf(1f,1f),people,.6f))
        assertNull(IdentityMath.match(floatArrayOf(Float.NaN,1f),people,.6f))
    }
    @Test fun visibleFaceAloneIsNotSpeakerEvidence() {
        val turn=SpeakerTurn();turn.observeFace("a");assertNull(turn.result())
        turn.observeVoice("b");assertEquals("b" to "voice",turn.result())
    }
    @Test fun conflictingAndUnknownWindowsNeverChooseLastSpeaker() {
        val turn=SpeakerTurn();turn.observeFace("a");turn.observeVoice("a")
        assertEquals("a" to "face_voice",turn.result())
        turn.observeVoice("b");assertNull(turn.result())
        val unknown=SpeakerTurn();unknown.observeVoice(null);unknown.observeVoice("a");assertNull(unknown.result())
        assertNull(SpeakerTurn().result())
    }
    @Test fun templatesWithWrongModelDimensionsCannotMatch() {
        assertNull(IdentityMath.match(floatArrayOf(1f,0f),listOf(PersonTemplate("a",listOf(floatArrayOf(1f)))),.6f))
    }
}
