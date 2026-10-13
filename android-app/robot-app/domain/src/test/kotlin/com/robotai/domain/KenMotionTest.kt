package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class KenMotionTest {
    private fun settle(input:KenMotionInput):KenPose {
        val engine=KenMotionEngine()
        var pose=engine.frame(input,1000,0f)
        repeat(60){pose=engine.frame(input,1000L+it*16,.016f)}
        return pose
    }
    @Test fun sleepOverridesMoodSpeechMusicAndReactionAndDoesNotReplayOnWake() {
        val engine=KenMotionEngine()
        val asleep=KenMotionInput(Phase.SLEEPING,Mood.HAPPY,musicPlaying=true,speechLevel=1f,
            reaction=KenReaction.FINGER_HEART,reactionAtMs=1000)
        val p=engine.frame(asleep,1300,0f)
        assertEquals(.035f,p.open,.0001f);assertEquals(.3f,p.brightness,.0001f)
        assertEquals(0f,p.mouth,0f);assertNull(p.reaction);assertFalse(p.musicPlaying)
        assertNull(engine.frame(asleep.copy(phase=Phase.IDLE),1400,.016f).reaction)
    }
    @Test fun errorRemainsReadableWhileLovingGestureAndMusicArePresent() {
        val p=settle(KenMotionInput(Phase.ERROR,Mood.HAPPY,musicPlaying=true,
            reaction=KenReaction.TWO_HAND_HEART,reactionAtMs=1000))
        assertNull(p.reaction);assertFalse(p.musicPlaying);assertEquals(0f,p.happy,.001f)
        assertEquals(255f,p.red,.01f);assertTrue(p.smile<0)
    }
    @Test fun hidingGestureDuringSleepDoesNotLoseItsRejection() {
        val engine=KenMotionEngine()
        val gesture=KenMotionInput(reaction=KenReaction.WAVE,reactionAtMs=1000)
        assertNotNull(engine.frame(gesture,1200,0f).reaction)
        repeat(3){engine.frame(KenMotionInput(Phase.SLEEPING),1300L+it*16,.016f)}
        assertNull(engine.frame(gesture,1500,.016f).reaction)
    }
    @Test fun speakingUsesPresentedLevelIncludingSilenceEvenDuringHeartReaction() {
        val speaking=KenMotionInput(Phase.SPEAKING,Mood.CARING,speechLevel=.8f,
            reaction=KenReaction.FINGER_HEART,reactionAtMs=1000)
        val p=settle(speaking)
        assertEquals(.68f,p.mouth,.001f);assertTrue(p.heart>.8f)
        assertEquals(0f,settle(speaking.copy(speechLevel=0f)).mouth,.001f)
        assertEquals(0f,settle(speaking.copy(phase=Phase.INTERRUPTED)).mouth,.001f)
    }
    @Test fun reactionsExpireAndReducedMotionKeepsExpressiveStaticFace() {
        val input=KenMotionInput(reaction=KenReaction.WAVE,reactionAtMs=1000,reducedMotion=true)
        val engine=KenMotionEngine();val a=engine.frame(input,1500,0f);val b=engine.frame(input,1600,.016f)
        assertEquals(1f,a.blink,0f);assertEquals(0f,a.tilt,.0001f);assertEquals(0f,a.y,.0001f)
        assertTrue(a.happy>0);assertEquals(a.y,b.y,0f)
        assertNull(engine.frame(input,3800,.016f).reaction)
        assertNull(engine.frame(input.copy(reactionAtMs=9000),4000,.016f).reaction)
    }
    @Test fun gazeLeadsFaceAndSwitchingTargetsPreservesContinuity() {
        val engine=KenMotionEngine();engine.frame(KenMotionInput(),0,0f)
        val next=engine.frame(KenMotionInput(gaze=1f),16,.016f)
        assertTrue(next.eyeX/25>next.faceX/8)
        assertTrue(next.eyeX in 0f..25f)
        val before=engine.frame(KenMotionInput(reaction=KenReaction.FINGER_HEART,reactionAtMs=0),500,.016f)
        val changed=engine.frame(KenMotionInput(reaction=KenReaction.THUMBS_DOWN,reactionAtMs=500),500,0f)
        assertEquals(before.open,changed.open,0f);assertEquals(before.heart,changed.heart,0f)
    }
    @Test fun springIsIndependentOfFrameRateAndRejectsNonFiniteInput() {
        fun run(step:Float,count:Int):KenPose {
            val engine=KenMotionEngine();engine.frame(KenMotionInput(),0,0f)
            var p=engine.frame(KenMotionInput(gaze=1f,reducedMotion=true),0,0f)
            repeat(count){p=engine.frame(KenMotionInput(gaze=1f,reducedMotion=true),(it*step*1000).toLong(),step)}
            return p
        }
        val a=run(.01f,50);val b=run(.05f,10)
        assertEquals(a.eyeX,b.eyeX,.001f);assertEquals(a.faceX,b.faceX,.001f)
        val p=settle(KenMotionInput(Phase.SPEAKING,gaze=Float.NaN,speechLevel=Float.POSITIVE_INFINITY,energy=Float.NaN))
        assertTrue(p.eyeX.isFinite());assertTrue(p.mouth.isFinite());assertTrue(p.y.isFinite())
    }
    @Test fun everyGestureHasFinitePoseAndFixedDuration() {
        assertEquals(GestureReaction.DURATION_MS,KenMotionTiming.REACTION_MS)
        for(kind in KenReaction.values()) {
            val input=KenMotionInput(reaction=kind,reactionAtMs=1000)
            val p=settle(input)
            assertEquals(kind,p.reaction);assertTrue(p.tilt.isFinite());assertTrue(p.open.isFinite());assertTrue(p.reactionWeight>0)
        }
    }
    @Test fun pcmEnvelopeDistinguishesSilenceFromSoundAndHonorsWindowBounds() {
        assertEquals(0f,PcmSpeechEnvelope.level(ShortArray(480),0,480),0f)
        val pcm=ShortArray(960){if(it<480)0 else 4000}
        assertEquals(0f,PcmSpeechEnvelope.level(pcm,0,480),0f)
        assertTrue(PcmSpeechEnvelope.level(pcm,480,480)>.9f)
        assertEquals(1f,PcmSpeechEnvelope.level(ShortArray(480){Short.MIN_VALUE},0,480),0f)
    }
}
