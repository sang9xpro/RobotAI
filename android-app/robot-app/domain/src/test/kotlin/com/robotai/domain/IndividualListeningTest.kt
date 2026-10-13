package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class IndividualListeningTest {
    private val visual=ListeningEvidence(true,true,true,100,1)
    @Test fun familiarNeedsFaceVoiceAndVerifiedAttention() {
        val policy=IndividualListening()
        assertFalse(policy.admit("a","recognized",visual.copy(activeSpeaker=false),0).accepted)
        assertFalse(policy.admit("a","recognized",visual.copy(facing=false),0).accepted)
        assertFalse(policy.admit("a","recognized",visual.copy(cameraAgeMs=501),0).accepted)
        assertFalse(policy.admit("a","recognized",visual.copy(people=2),0).accepted)
        assertFalse(policy.admit("a","unknown",visual,0).accepted)
        assertTrue(policy.admit("a","recognized",visual,0).accepted)
    }
    @Test fun temporaryOutOfFrameContinuationNeedsCompletedPlaybackAndSameVoice() {
        val policy=IndividualListening()
        val first=policy.admit("a","temporary",ListeningEvidence(),0,wake=true)
        assertTrue(first.accepted)
        assertFalse(policy.admit("a","temporary",ListeningEvidence(),1000).accepted)
        policy.playbackCompleted(1000)
        assertFalse(policy.admit("b","temporary",ListeningEvidence(),2000).accepted)
        val next=policy.admit("a","temporary",ListeningEvidence(),2000)
        assertTrue(next.accepted);assertEquals(first.conversationId,next.conversationId)
        assertFalse(policy.admit("a","temporary",ListeningEvidence(),31001).accepted)
        val switched=policy.admit("b","temporary",ListeningEvidence(),32000,wake=true)
        assertNotEquals(first.conversationId,switched.conversationId)
    }
    @Test fun guestsExpireAndMixedVoiceCannotReplaceAnchor() {
        val voice=TemporarySpeaker()
        val a=floatArrayOf(1f,0f);val b=floatArrayOf(0f,1f)
        assertNull(voice.remember(listOf(a),2999,0,.7f))
        assertNull(voice.remember(listOf(a,b),4000,0,.7f))
        val id=voice.remember(listOf(a,a),4000,0,.7f)
        assertNotNull(id);assertEquals(id,voice.match(listOf(a),179999,.7f))
        assertNull(voice.match(listOf(a,b),1000,.7f))
        assertNull(voice.match(listOf(a),180000,.7f))
    }
    @Test fun interruptionsNeedCurrentOwnerOrExplicitCall() {
        val policy=IndividualListening()
        policy.admit("a","recognized",visual,0)
        assertTrue(policy.admit("a","recognized",ListeningEvidence(),1000,interruption=true).accepted)
        assertFalse(policy.admit("b","recognized",ListeningEvidence(),2000,interruption=true).accepted)
        assertFalse(policy.admit("a","recognized",ListeningEvidence(overlapping=true),2000,interruption=true).accepted)
        assertTrue(policy.admit(null,"unknown",ListeningEvidence(overlapping=true),2000,wake=true).accepted)
    }
    @Test fun mentionsAreNotDirectCalls() {
        assertTrue(DirectRobotCall.matches("Ken","Ken ơi, hôm nay thế nào?"))
        assertTrue(DirectRobotCall.matches("Ken","Này Ken ơi"))
        assertTrue(DirectRobotCall.matches("Ken","Bạn giúp mình nhé, Ken ơi"))
        assertFalse(DirectRobotCall.matches("Ken","Tôi vừa nói chuyện với Ken"))
        assertFalse(DirectRobotCall.matches("Ken","Ken là một robot"))
        assertFalse(DirectRobotCall.matches("Ken","Kenobi ơi"))
    }
    @Test fun modelEndpointWaitsForPauseAndPreservesLoneCallWindow() {
        val endpoint=ModelSpeechEndpoint()
        repeat(2) { assertEquals(ModelSpeechEndpoint.Event.WAITING,endpoint.accept(true)) }
        assertEquals(ModelSpeechEndpoint.Event.START,endpoint.accept(true))
        repeat(19) { assertEquals(ModelSpeechEndpoint.Event.SILENCE,endpoint.accept(false)) }
        assertEquals(ModelSpeechEndpoint.Event.END,endpoint.accept(false))
        val wake=ModelSpeechEndpoint();repeat(3) { wake.accept(true) };wake.waitingForRequest(true)
        repeat(66) { assertNotEquals(ModelSpeechEndpoint.Event.END,wake.accept(false)) }
        assertEquals(ModelSpeechEndpoint.Event.END,wake.accept(false))
    }
    @Test fun readinessRejectsUnmeasuredAndInsufficientResults() {
        val report=ConversationBenchmarks(100,50,60,.98,.9,5000,1000)
        assertTrue(report.passed)
        assertFalse(report.copy(utterances=99).passed)
        assertFalse(report.copy(precision=.97).passed)
        assertFalse(report.copy(firstAudioP95Ms=0).passed)
        assertFalse(report.copy(interruptP95Ms=1001).passed)
    }
}
