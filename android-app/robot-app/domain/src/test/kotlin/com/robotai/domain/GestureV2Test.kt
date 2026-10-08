package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class GestureV2Test {
    private fun pose(straight: Set<Int>): GestureHand {
        val p=MutableList(21) { HandPoint(.5f,.6f) }
        p[0]=HandPoint(.5f,.8f)
        p[1]=HandPoint(.35f,.7f);p[2]=HandPoint(.35f,.6f);p[3]=HandPoint(.35f,.5f)
        p[4]=HandPoint(.35f,if(0 in straight) .3f else .65f)
        for(i in 1..4) {
            val x=.4f+i*.04f
            p[i*4+1]=HandPoint(x,.65f)
            p[i*4+2]=HandPoint(x,.5f)
            p[i*4+3]=HandPoint(x,.45f)
            p[i*4+4]=if(i in straight) HandPoint(2*x-.5f,.2f) else HandPoint(.5f,.72f)
        }
        return GestureHand(p)
    }
    @Test fun curlRulesRecognizeNewShapesAcrossMirrorScaleAndAspect() {
        val ok=pose(setOf(2,3,4)).let { h -> h.copy(points=h.points.toMutableList().apply { this[4]=this[8] }) }
        for((hand,expected) in listOf(pose(setOf(1,4)) to SocialGesture.ROCK_ON,
            pose(setOf(0,1,4)) to SocialGesture.I_LOVE_YOU,pose(setOf(0)) to SocialGesture.THUMBS_UP,
            ok to SocialGesture.OK_SIGN)) {
            val variants=listOf(hand,hand.copy(points=hand.points.map { it.copy(x=1-it.x) }),
                hand.copy(points=hand.points.map { HandPoint(.1f+it.x*.7f,.1f+it.y*.7f,it.z*.7f) }),
                hand.copy(points=hand.points.map { it.copy(y=it.y/2) },frameAspect=2f))
            variants.forEach { assertEquals(expected,FingerPoseClassifier.classify(it)) }
        }
        assertNull(FingerPoseClassifier.classify(pose(setOf(0,1,2,3,4))))
        assertNull(FingerPoseClassifier.classify(pose(emptySet())))
        val bad=pose(setOf(0)).copy(points=List(21) { HandPoint(.5f,.5f,Float.NaN) })
        assertFalse(SocialGestureGeometry.valid(bad))
    }
    @Test fun curlUsesDepthAndRejectsDegenerateJoints() {
        val h=pose(setOf(0)).let { h -> h.copy(points=h.points.toMutableList().apply {
            this[1]=HandPoint(.5f,.5f,-.2f);this[3]=HandPoint(.5f,.5f,0f);this[4]=HandPoint(.5f,.5f,.2f)
        }) }
        assertEquals(180f,FingerPoseClassifier.curlAngle(h,0),.1f)
        assertTrue(FingerPoseClassifier.curlAngle(h.copy(points=List(21) { HandPoint(.5f,.5f) }),0).isNaN())
    }
    @Test fun secondaryNeutralClippedOrDistantHandDoesNotVetoOwnedLike() {
        val owner=GestureOwner("known",.3f,.1f,.7f,.35f)
        val like=pose(setOf(0)).copy(category="Thumb_Up",score=.9f)
        val neutral=pose(emptySet())
        for(other in listOf(neutral,neutral.copy(points=neutral.points.map { it.copy(x=1.2f) }),
            neutral.copy(points=neutral.points.map { it.copy(x=.99f) }))) {
            val e=SocialGestureEngine()
            assertNull(e.observe(listOf(owner),listOf(other,like),0))
            assertEquals(SocialGesture.THUMBS_UP,e.observe(listOf(owner),listOf(other,like),400)?.kind)
        }
        val ambiguous=SocialGestureEngine()
        for(t in 0L..1500 step 100) assertNull(ambiguous.observe(listOf(owner),
            listOf(like,neutral.copy(category="Victory",score=.9f)),t))
    }
    @Test fun switchGestureWithoutReleaseButNeverTransferVotesToAnotherPerson() {
        val t=GestureTrigger()
        t.observe("a",SocialGesture.THUMBS_UP,0)
        assertNotNull(t.observe("a",SocialGesture.THUMBS_UP,400))
        for(now in 500L..1300 step 100) t.observe("a",SocialGesture.VICTORY,now)
        assertEquals(SocialGesture.VICTORY,t.observe("a",SocialGesture.VICTORY,1400)?.kind)
        assertNull(t.observe("b",SocialGesture.VICTORY,1800))
        assertNull(t.observe("b",SocialGesture.VICTORY,1900))
    }
}
