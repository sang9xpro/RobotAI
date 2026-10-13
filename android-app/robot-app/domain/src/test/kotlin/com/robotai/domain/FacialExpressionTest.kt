package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class FacialExpressionTest {
    private val owner = ExpressionOwner("known-person",1,11)
    private fun scores(kind:FacialExpression, score:Float=.92f) = FloatArray(7) {
        if (it == kind.ordinal) score else (1f-score)/6
    }
    private fun sample(engine:FacialExpressionEngine, kind:FacialExpression, at:Long, who:ExpressionOwner=owner, score:Float=.92f):KnownFaceExpression? {
        engine.present(who,at,.25f)
        return engine.observe(who,scores(kind,score),at,at)
    }
    private fun smile(engine:FacialExpressionEngine) { for(t in 0L..750L step 250) sample(engine,FacialExpression.HAPPY,t) }

    @Test fun presenceDoesNotInventAnExpressionAndSmileRequiresDuration() {
        val engine=FacialExpressionEngine()
        assertEquals(FacialExpression.UNKNOWN,engine.present(owner,0,.25f).current(0))
        for(t in listOf(0L,250L,500L)) assertEquals(FacialExpression.UNKNOWN,sample(engine,FacialExpression.HAPPY,t)?.current(t))
        val confirmed=sample(engine,FacialExpression.HAPPY,750)!!
        assertEquals(FacialExpression.HAPPY,confirmed.current(750));assertEquals(.25f,confirmed.x,0f)
    }
    @Test fun manyFastFramesCannotReplaceHoldingAnExpression() {
        val engine=FacialExpressionEngine()
        for(t in 0L..500L step 25) sample(engine,FacialExpression.HAPPY,t)
        assertEquals(FacialExpression.UNKNOWN,engine.state?.current(500))
    }
    @Test fun sadnessAndSurpriseRequireLongerConfirmation() {
        for((kind,hold) in listOf(FacialExpression.SAD to 1600L,FacialExpression.SURPRISED to 900L)) {
            val engine=FacialExpressionEngine()
            for(t in 0L until hold step 200) {
                sample(engine,kind,t)
                assertEquals(FacialExpression.UNKNOWN,engine.state?.current(t))
            }
            assertEquals(kind,sample(engine,kind,hold)?.current(hold))
        }
    }
    @Test fun switchingPeopleOrDetectorTracksCannotInheritSmileOrPendingEvidence() {
        for(other in listOf(owner.copy(personId="other"),owner.copy(trackId=2),owner.copy(faceTrack=12))) {
            val engine=FacialExpressionEngine();smile(engine)
            assertEquals(FacialExpression.UNKNOWN,engine.present(other,900,0f).current(900))
            engine.observe(owner,scores(FacialExpression.HAPPY),850,900)
            assertEquals(other,engine.state?.owner)
            assertEquals(FacialExpression.UNKNOWN,engine.state?.current(900))
        }
    }
    @Test fun missingFaceAndTimeRollbackClearConfirmation() {
        for(at in listOf(1600L,500L)) {
            val engine=FacialExpressionEngine();smile(engine)
            assertEquals(FacialExpression.UNKNOWN,engine.present(owner,at,0f).current(at))
        }
    }
    @Test fun staleDuplicateAndFutureResultsCannotConfirmAnExpression() {
        val engine=FacialExpressionEngine()
        engine.present(owner,1500,0f)
        assertEquals(FacialExpression.UNKNOWN,engine.observe(owner,scores(FacialExpression.HAPPY),900,1500)?.current(1500))
        assertEquals(FacialExpression.UNKNOWN,engine.observe(owner,scores(FacialExpression.HAPPY),1501,1500)?.current(1500))
        engine.observe(owner,scores(FacialExpression.HAPPY),1500,1500)
        repeat(100) { engine.observe(owner,scores(FacialExpression.HAPPY),1500,1500) }
        assertEquals(FacialExpression.UNKNOWN,engine.state?.current(1500))
    }
    @Test fun unsupportedNegativeClassesAndAmbiguousScoresDoNotTriggerNegativeReactions() {
        for(kind in listOf(FacialExpression.ANGRY,FacialExpression.DISGUST,FacialExpression.FEARFUL)) {
            val engine=FacialExpressionEngine()
            for(t in 0L..3000L step 250) sample(engine,kind,t)
            assertEquals(FacialExpression.UNKNOWN,engine.state?.current(3000))
        }
        val engine=FacialExpressionEngine()
        for(t in 0L..3000L step 250) {
            engine.present(owner,t,0f)
            engine.observe(owner,FloatArray(7) { 1f/7 },t,t)
        }
        assertEquals(FacialExpression.UNKNOWN,engine.state?.current(3000))
    }
    @Test fun invalidScoresAndGapsBreakPendingConfirmation() {
        val invalid=listOf<FloatArray?>(null,floatArrayOf(.9f),FloatArray(7){Float.NaN},FloatArray(7){1f},FloatArray(7){-.1f})
        for(bad in invalid) {
            val engine=FacialExpressionEngine()
            sample(engine,FacialExpression.HAPPY,0);sample(engine,FacialExpression.HAPPY,250)
            engine.present(owner,500,0f);engine.observe(owner,bad,500,500)
            assertEquals(FacialExpression.UNKNOWN,sample(engine,FacialExpression.HAPPY,750)?.current(750))
        }
        val engine=FacialExpressionEngine()
        sample(engine,FacialExpression.HAPPY,0);sample(engine,FacialExpression.HAPPY,250)
        engine.present(owner,750,0f)
        assertEquals(FacialExpression.UNKNOWN,sample(engine,FacialExpression.HAPPY,1100)?.current(1100))
    }
    @Test fun heldSmileMaintainsAtLowerScoreButExpiresWithoutSupportingEvidence() {
        val engine=FacialExpressionEngine();smile(engine)
        for(t in 1000L..2000L step 250) sample(engine,FacialExpression.HAPPY,t,score=.61f)
        assertEquals(FacialExpression.HAPPY,engine.state?.current(2000))
        for(t in 2250L..3500L step 250) engine.present(owner,t,0f)
        assertEquals(FacialExpression.HAPPY,engine.state?.current(3500))
        assertEquals(FacialExpression.UNKNOWN,engine.present(owner,3750,0f).current(3750))
    }
    @Test fun neutralReturnsKenToCalmAfterASmile() {
        val engine=FacialExpressionEngine();smile(engine)
        for(t in 1000L..2250L step 250) sample(engine,FacialExpression.NEUTRAL,t)
        assertEquals(FacialExpression.NEUTRAL,engine.state?.current(2250))
    }
    @Test fun resetCannotReplayPreviousExpression() {
        val engine=FacialExpressionEngine();smile(engine);engine.reset()
        assertNull(engine.state)
        assertNull(engine.observe(owner,scores(FacialExpression.HAPPY),900,900))
        assertEquals(FacialExpression.UNKNOWN,engine.present(owner,1000,Float.NaN).current(1000))
        assertEquals(0f,engine.state!!.x,0f)
    }
    @Test fun stableSoftmaxRejectsBadOutputsAndDoesNotOverflow() {
        assertNull(ExpressionScores.fromLogits(floatArrayOf(1f)))
        assertNull(ExpressionScores.fromLogits(FloatArray(7){Float.POSITIVE_INFINITY}))
        val result=ExpressionScores.fromLogits(floatArrayOf(-10000f,-10000f,-10000f,10000f,-10000f,-10000f,-10000f))!!
        assertEquals(1f,result.sum(),.0001f);assertEquals(1f,result[3],0f)
    }
}
