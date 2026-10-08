package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class GestureTeachingTest {
    private val owner=GestureOwner("fixture",.25f,.05f,.75f,.35f,body=TrackBox(.1f,.05f,.9f,.98f))
    private fun hand(straight:Set<Int>):GestureHand {
        val p=MutableList(21) { HandPoint(.5f,.6f) };p[0]=HandPoint(.5f,.8f)
        p[1]=HandPoint(.35f,.7f);p[2]=HandPoint(.35f,.6f);p[3]=HandPoint(.35f,.5f)
        p[4]=HandPoint(.35f,if(0 in straight) .3f else .65f)
        for(i in 1..4) { val x=.4f+i*.04f
            p[i*4+1]=HandPoint(x,.65f);p[i*4+2]=HandPoint(x,.5f);p[i*4+3]=HandPoint(x,.45f)
            p[i*4+4]=if(i in straight) HandPoint(2*x-.5f,.2f) else HandPoint(.5f,.72f)
        }
        return GestureHand(p,side="Right")
    }
    private fun example(id:String,label:String,h:GestureHand,accepted:Boolean=true)=GestureExample(id,label,id,
        listOf(0L,200L,400L).map { GestureFrame(h.points,h.side,h.frameAspect,it) },1000,accepted)

    @Test fun featuresMatchPythonContractAndMirrorAspectAndTranslation() {
        val h=hand(setOf(0));val x=GestureFeatures.extract(h)!!
        val expected=javaClass.getResourceAsStream("/gesture-features-like.csv")!!.bufferedReader().readText().trim().split(",").map { it.toFloat() }.toFloatArray()
        assertArrayEquals(expected,x,.00001f)
        for(changed in listOf(h.copy(side="Left",points=h.points.map { it.copy(x=1-it.x) }),
            h.copy(points=h.points.map { it.copy(y=it.y/2) },frameAspect=2f),
            h.copy(points=h.points.map { HandPoint(.1f+it.x*.7f,.1f+it.y*.7f,it.z*.7f) })))
            assertTrue(GestureFeatures.distance(x,GestureFeatures.extract(changed)!!)<.00001f)
        assertTrue(GestureFeatures.distance(x,GestureFeatures.extract(hand(setOf(1,4)))!!)>.2f)
        assertNull(GestureFeatures.extract(h.copy(side="")))
    }
    @Test fun lessonRequiresCountdownSteadyPoseAndReleaseBetweenCaptures() {
        val r=GestureLessonRecorder(owner.id!!);val h=hand(setOf(0));val samples=ArrayList<List<GestureFrame>>()
        for(t in 0L..3500 step 100) r.observe(listOf(owner),listOf(h),t)?.let(samples::add)
        assertEquals(1,samples.size);assertEquals(LessonPhase.RELEASE,r.phase)
        assertTrue(samples.single().size>=3)
        assertTrue(samples.single().zipWithNext().all { (a,b) -> a.elapsedMs<b.elapsedMs })
        for(t in 3600L..4000 step 100) assertNull(r.observe(listOf(owner),emptyList(),t))
        for(t in 4100L..7100 step 100) r.observe(listOf(owner),listOf(h),t)?.let(samples::add)
        assertEquals(2,samples.size)
    }
    @Test fun ownerChangeMultiplePeopleClippingAndInterruptedCameraCannotSupplySamples() {
        val h=hand(setOf(0))
        for(owners in listOf(emptyList(),listOf(owner.copy(id=null)),listOf(owner.copy(id="other")),listOf(owner,owner))) {
            val r=GestureLessonRecorder("fixture")
            for(t in 0L..5000 step 100) assertNull(r.observe(owners,listOf(h),t))
            assertEquals(LessonPhase.WAITING,r.phase)
        }
        val r=GestureLessonRecorder("fixture")
        for(t in 0L..2500 step 100) assertNull(r.observe(listOf(owner),listOf(h),t))
        assertNull(r.observe(listOf(owner),listOf(h),4000))
        assertEquals(LessonPhase.COUNTDOWN,r.phase)
        val clipped=h.copy(points=h.points.map { it.copy(x=1.2f) })
        for(t in 4100L..7500 step 100) assertNull(r.observe(listOf(owner),listOf(clipped),t))
    }
    @Test fun movingPoseRestartsHoldInsteadOfMixingLabels() {
        val r=GestureLessonRecorder("fixture")
        for(t in 0L..2400 step 100) assertNull(r.observe(listOf(owner),listOf(hand(setOf(0))),t))
        for(t in 2500L..3200 step 100) assertNull(r.observe(listOf(owner),listOf(hand(setOf(1,4))),t))
        assertNotNull(r.observe(listOf(owner),listOf(hand(setOf(1,4))),3400))
    }
    @Test fun localLearningRequiresPositiveNegativeAndNeverCrossesPerson() {
        val like=TaughtGesture("like","fixture","Like của tôi",SocialGesture.THUMBS_UP)
        val neutral=TaughtGesture("none","fixture","Tay bình thường",null)
        val h=hand(setOf(0));val normal=hand(setOf(0,1,2,3,4))
        val examples=(0..2).map { example("p$it",like.id,h) }+(0..2).map { example("n$it",neutral.id,normal) }
        val data=GestureLessonData(definitions=listOf(like,neutral),examples=examples)
        assertEquals(like,PersonalGestureMatcher(data,"fixture").predict(h)?.definition)
        assertNull(PersonalGestureMatcher(data,"fixture").predict(normal))
        assertTrue(PersonalGestureMatcher(data,"fixture").rejects(normal))
        assertFalse(PersonalGestureMatcher(data,"fixture").rejects(h))
        assertFalse(PersonalGestureMatcher(data,"other").rejects(normal))
        assertNull(PersonalGestureMatcher(data,"fixture").predict(hand(setOf(1,4))))
        assertNull(PersonalGestureMatcher(data,"other").predict(h))
        assertNull(PersonalGestureMatcher(data.copy(examples=examples.take(3)),"fixture").predict(h))
        assertNull(PersonalGestureMatcher(data.copy(examples=examples.map { if(it.id=="p0") it.copy(accepted=false) else it }),"fixture").predict(h))
    }
    @Test fun learnedGestureStillNeedsOwnershipAndCarriesCustomName() {
        val h=hand(setOf(0)).copy(learnedGesture=SocialGesture.VICTORY,learnedName="Siêu nhân",learnedScore=.9f)
        val engine=SocialGestureEngine()
        assertNull(engine.observe(listOf(owner),listOf(h),0))
        val event=engine.observe(listOf(owner),listOf(h),400)
        assertEquals(SocialGesture.VICTORY,event?.kind);assertEquals("Siêu nhân",event?.learnedName)
        val strangers=SocialGestureEngine()
        for(t in 0L..2000 step 100) assertNull(strangers.observe(listOf(owner.copy(id=null)),listOf(h),t))
        val wrongPlace=SocialGestureEngine()
        for(t in 0L..2000 step 100) assertNull(wrongPlace.observe(listOf(owner.copy(body=TrackBox(.7f,.05f,.95f,.4f))),listOf(h),t))
    }
    @Test fun explicitlyTaughtNeutralPoseSuppressesStockReactionOnlyForThatPose() {
        val engine=SocialGestureEngine()
        val neutral=hand(setOf(0,1,2,3,4)).copy(category="Open_Palm",score=.95f,learnedRejected=true)
        for(t in 0L..3000 step 100) assertNull(engine.observe(listOf(owner),listOf(neutral),t))
        val like=hand(setOf(0)).copy(category="Thumb_Up",score=.95f)
        assertNull(engine.observe(listOf(owner),listOf(like),3100))
        assertEquals(SocialGesture.THUMBS_UP,engine.observe(listOf(owner),listOf(like),3500)?.kind)
    }
    @Test fun learningStaticPalmDoesNotHideDynamicWave() {
        for(rejected in listOf(false,true)) {
            val e=SocialGestureEngine()
            val events=listOf(.45f,.53f,.45f,.53f,.45f,.53f,.45f).mapIndexedNotNull { i,x ->
                val h=hand(setOf(0,1,2,3,4)).let { original -> original.copy(points=original.points.map { it.copy(x=it.x+x-.5f) },
                    category="Open_Palm",score=.95f,learnedGesture=if(rejected) null else SocialGesture.HIGH_FIVE,
                    learnedScore=.9f,learnedRejected=rejected) }
                e.observe(listOf(owner),listOf(h),i*250L)
            }
            assertTrue(events.any { it.kind==SocialGesture.WAVE })
        }
    }
}
