package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class VisualMoodPolicyTest {
    private val observed=KnownFaceExpression(ExpressionOwner("person",1,1),FacialExpression.HAPPY,.9f,1000,1000)
    @Test fun quietListeningAndOfflineIdleCanReactWithoutASpeechTurn() {
        for(phase in listOf(Phase.IDLE,Phase.LISTENING,Phase.INTERRUPTED)) {
            val result=VisualMoodPolicy.select(Mood.CURIOUS,phase,observed,1100,true,true,false)
            assertEquals(Mood.HAPPY,result.mood);assertTrue(result.fromCamera)
        }
    }
    @Test fun presenceSadnessSurpriseAndNeutralHaveDistinctScreenResponses() {
        for((expression,mood) in listOf(FacialExpression.UNKNOWN to Mood.CURIOUS,FacialExpression.SAD to Mood.CARING,
            FacialExpression.SURPRISED to Mood.CURIOUS,FacialExpression.NEUTRAL to Mood.CALM)) {
            assertEquals(mood,VisualMoodPolicy.select(Mood.HAPPY,Phase.IDLE,observed.copy(expression=expression),1100,true,true,false).mood)
        }
    }
    @Test fun sleepErrorsSpeakingAndProcessingKeepTheirOwnMood() {
        for(phase in listOf(Phase.SLEEPING,Phase.ERROR,Phase.SPEAKING,Phase.THINKING,Phase.FINALIZING)) {
            val result=VisualMoodPolicy.select(Mood.CARING,phase,observed,1100,true,true,false)
            assertEquals(Mood.CARING,result.mood);assertFalse(result.fromCamera)
        }
    }
    @Test fun speechGesturesTouchesDisabledAndAbsentFacesBlockCameraMood() {
        assertFalse(VisualMoodPolicy.select(Mood.CURIOUS,Phase.LISTENING,observed,1100,true,true,true).fromCamera)
        assertFalse(VisualMoodPolicy.select(Mood.CURIOUS,Phase.IDLE,observed,1100,true,true,false,true).fromCamera)
        assertFalse(VisualMoodPolicy.select(Mood.CURIOUS,Phase.IDLE,observed,1100,false,true,false).fromCamera)
        assertFalse(VisualMoodPolicy.select(Mood.CURIOUS,Phase.IDLE,observed,1100,true,false,false).fromCamera)
        assertFalse(VisualMoodPolicy.select(Mood.CURIOUS,Phase.IDLE,null,1100,true,true,false).fromCamera)
        assertFalse(VisualMoodPolicy.select(Mood.CURIOUS,Phase.IDLE,observed,1800,true,true,false).fromCamera)
    }
    @Test fun listeningRendererPreservesSmileAndConcernWhenCameraMoodIsSelected() {
        val engine=KenMotionEngine()
        val camera=engine.frame(KenMotionInput(Phase.LISTENING,Mood.HAPPY,visualMood=true),1000,0f)
        val ordinary=KenMotionEngine().frame(KenMotionInput(Phase.LISTENING,Mood.HAPPY),1000,0f)
        assertTrue(camera.happy>ordinary.happy);assertTrue(camera.smile>ordinary.smile)
        val caring=KenMotionEngine().frame(KenMotionInput(Phase.LISTENING,Mood.CARING,visualMood=true),1000,0f)
        assertTrue(caring.open<camera.open);assertTrue(caring.tilt>0)
        val speaking=KenMotionEngine().frame(KenMotionInput(Phase.SPEAKING,Mood.HAPPY,speechLevel=.8f,visualMood=true),1000,0f)
        assertEquals(.68f,speaking.mouth,.0001f)
    }
}
