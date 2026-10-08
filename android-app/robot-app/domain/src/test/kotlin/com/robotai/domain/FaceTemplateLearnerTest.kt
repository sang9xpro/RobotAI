package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class FaceTemplateLearnerTest {
    private val anchors=listOf(floatArrayOf(1f,0f))
    private val diverse=floatArrayOf(.90f,.43f)
    private fun feed(learner: FaceTemplateLearner,time: Long,score: Float=.9f,quality: Boolean=true,unambiguous: Boolean=true,
        id: String?="sang",track: Int=1,vector: FloatArray?=diverse,learned: List<FloatArray> = emptyList())=
        learner.observe(id,track,vector,anchors,learned,score,.65f,quality,unambiguous,time)
    @Test fun diverseAnchoredSampleRequiresStableSequenceThenCooldown() {
        val learner=FaceTemplateLearner()
        for(t in 0L..1500L step 500L) assertNull(feed(learner,t))
        assertArrayEquals(diverse,feed(learner,2000),.0001f)
        for(t in 2500L..10000L step 500L) assertNull(feed(learner,t))
        for(t in 33000L..34500L step 500L) assertNull(feed(learner,t))
        assertNotNull(feed(learner,35000))
    }
    @Test fun unknownBodyOnlyLowQualityAndAmbiguousSamplesNeverLearn() {
        for(mode in 0..4) {
            val learner=FaceTemplateLearner()
            for(t in 0L..5000L step 500L) assertNull(feed(learner,t,score=if(mode==0) .6f else .9f,
                quality=mode!=1,unambiguous=mode!=2,id=if(mode==3) null else "sang",vector=if(mode==4) null else diverse))
        }
    }
    @Test fun originalAnchorIsRequiredEvenIfLearnedMatchIsStrong() {
        val learner=FaceTemplateLearner()
        for(t in 0L..5000L step 500L) assertNull(feed(learner,t,vector=floatArrayOf(0f,1f)))
    }
    @Test fun duplicateTemplatesTrackSwitchAndFrameGapPreventPromotion() {
        val learner=FaceTemplateLearner()
        for(t in 0L..5000L step 500L) assertNull(feed(learner,t,learned=listOf(diverse)))
        for(t in 6000L..7500L step 500L) assertNull(feed(learner,t))
        assertNull(feed(learner,8000,track=2));assertNull(feed(learner,9000,track=2))
    }
}
