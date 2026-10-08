package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class GestureInteractionTest {
    private val owner = GestureOwner("sang", .35f, .05f, .65f, .3f)
    private fun hand(category: String = "", score: Float = .95f, x: Float = .5f, side: String = "Left"): GestureHand {
        val p = MutableList(21) { HandPoint(x, .6f) }
        p[0] = HandPoint(x, .7f); p[9] = HandPoint(x, .55f)
        return GestureHand(p, category, score, side)
    }
    private fun heart(): GestureHand {
        val p = hand().points.toMutableList()
        p[5] = HandPoint(.47f, .56f); p[6] = HandPoint(.45f, .47f)
        p[4] = HandPoint(.48f, .43f); p[8] = HandPoint(.50f, .43f)
        for ((pip, tip) in listOf(10 to 12, 14 to 16, 18 to 20)) {
            p[pip] = HandPoint(.5f, .54f); p[tip] = HandPoint(.5f, .62f)
        }
        return hand().copy(points = p)
    }
    private fun twoHearts(): List<GestureHand> {
        fun half(left: Boolean): GestureHand {
            val p = hand(x = if (left) .35f else .65f).points.toMutableList()
            p[4] = HandPoint(if (left) .49f else .51f, .53f)
            p[8] = HandPoint(if (left) .49f else .51f, .42f)
            p[6] = HandPoint(if (left) .41f else .59f, .43f)
            p[10] = HandPoint(p[0].x, .55f); p[12] = HandPoint(p[0].x, .63f)
            p[14] = p[10]; p[16] = p[12]; p[18] = p[10]; p[20] = p[12]
            return hand().copy(points = p, side = if (left) "Left" else "Right")
        }
        return listOf(half(true), half(false))
    }

    @Test fun trackedBodyAndPoseWristCanOwnGestureWithoutFace() {
        val bodyOwner=owner.copy(body=TrackBox(.2f,.1f,.8f,.95f),wrists=listOf(HandPoint(.5f,.7f)))
        val engine=SocialGestureEngine()
        for(t in 0L..200L step 100) assertNull(engine.observe(listOf(bodyOwner),listOf(hand("Thumb_Up")),t))
        assertEquals(SocialGesture.THUMBS_UP,engine.observe(listOf(bodyOwner),listOf(hand("Thumb_Up")),750)?.kind)
        val wrongArm=SocialGestureEngine()
        for(t in 0L..1500L step 250) assertNull(wrongArm.observe(listOf(bodyOwner.copy(wrists=listOf(HandPoint(.25f,.4f),HandPoint(.3f,.4f)))),listOf(hand("Thumb_Up")),t))
        val multipleBodies=SocialGestureEngine()
        for(t in 0L..1500L step 250) assertNull(multipleBodies.observe(listOf(bodyOwner,bodyOwner.copy(id=null)),listOf(hand("Thumb_Up")),t))
    }

    @Test fun missingOtherPoseWristDoesNotVetoHandInsideKnownBody() {
        val bodyOwner=owner.copy(body=TrackBox(.129f,.358f,.869f,.982f),wrists=listOf(HandPoint(.430f,.847f)))
        val sample=hand("Thumb_Up",.737f,x=.310f).let { h -> h.copy(points=h.points.map { HandPoint(it.x,it.y+.245f) }) }
        assertTrue(SocialGestureGeometry.valid(sample))
        val engine=SocialGestureEngine()
        assertNull(engine.observe(listOf(bodyOwner),listOf(sample),0))
        assertEquals(SocialGesture.THUMBS_UP,engine.observe(listOf(bodyOwner),listOf(sample),650)?.kind)
        val outside=SocialGestureEngine()
        val moved=sample.copy(points=sample.points.map { HandPoint(.99f,it.y) })
        assertNull(outside.observe(listOf(bodyOwner),listOf(moved),0))
        assertNull(outside.observe(listOf(bodyOwner),listOf(moved),650))
        assertEquals(GestureDecision.OUTSIDE_OWNER,outside.decision)
    }

    @Test fun requiresSteadyPoseReleaseAndCooldown() {
        val gate = GestureTrigger()
        assertNull(gate.observe("sang", SocialGesture.VICTORY, 0))
        assertNull(gate.observe("sang", SocialGesture.VICTORY, 250))
        assertEquals(SocialGesture.VICTORY, gate.observe("sang", SocialGesture.VICTORY, 500)?.kind)
        assertNull(gate.observe("sang", SocialGesture.VICTORY, 750))
        for (time in 1000L..4500L step 250) assertNull(gate.observe("sang", SocialGesture.VICTORY, time))
        assertNull(gate.observe("sang", null, 4750))
        assertNull(gate.observe("sang", null, 5000))
        assertNull(gate.observe("sang", null, 5250))
        assertNull(gate.observe("sang", SocialGesture.VICTORY, 5500))
        assertNull(gate.observe("sang", SocialGesture.VICTORY, 5750))
        assertNotNull(gate.observe("sang", SocialGesture.VICTORY, 6000))
        assertNull(gate.observe("sang", SocialGesture.VICTORY, 6250))
    }

    @Test fun unstablePosesAndMissingFramesCannotTrigger() {
        val gate = GestureTrigger()
        assertNull(gate.observe("sang", SocialGesture.VICTORY, 0))
        assertNull(gate.observe("sang", SocialGesture.THUMBS_UP, 250))
        assertEquals(SocialGesture.VICTORY, gate.observe("sang", SocialGesture.VICTORY, 500)?.kind)
        assertNull(gate.observe("sang", SocialGesture.VICTORY, 1500))
        assertNull(gate.observe(null, SocialGesture.VICTORY, 1750))
    }
    @Test fun slowerDeviceFramesCanTriggerButStaleGapCannot() {
        val engine=SocialGestureEngine()
        assertNull(engine.observe(listOf(owner),listOf(hand("Thumb_Up")),0))
        assertEquals(SocialGesture.THUMBS_UP,engine.observe(listOf(owner),listOf(hand("Thumb_Up")),650)?.kind)
        val stale=GestureTrigger()
        assertNull(stale.observe("sang",SocialGesture.THUMBS_UP,0))
        assertNull(stale.observe("sang",SocialGesture.THUMBS_UP,900))
        val wave=SocialGestureEngine()
        val xs=listOf(.45f,.53f,.45f,.53f,.45f,.53f)
        assertTrue(xs.mapIndexedNotNull { i,x -> wave.observe(listOf(owner),listOf(hand("Open_Palm",x=x)),i*650L) }.isNotEmpty())
    }

    @Test fun cooldownSurvivesResetAndIdentityChanges() {
        val gate = GestureTrigger()
        listOf(0L,100L,200L).forEach { gate.observe("sang",SocialGesture.VICTORY,it) }
        assertNotNull(gate.observe("sang",SocialGesture.VICTORY,750))
        gate.reset()
        for (t in 1000L..1500L step 250) assertNull(gate.observe("lan",SocialGesture.VICTORY,t))
        assertNotNull(gate.observe("lan",SocialGesture.VICTORY,1750))
    }

    @Test fun rejectsStrangersMultipleFacesDistantHandsAndDisabledMode() {
        for (people in listOf(emptyList(), listOf(owner.copy(id=null)), listOf(owner,owner.copy(id="lan")))) {
            val engine = SocialGestureEngine()
            for (t in 0L..1500L step 250) assertNull(engine.observe(people,listOf(hand("Thumb_Up")),t))
        }
        val distant = SocialGestureEngine()
        val disabled = SocialGestureEngine()
        for (t in 0L..1500L step 250) {
            assertNull(distant.observe(listOf(owner),listOf(hand("Thumb_Up",x=.99f)),t))
            assertNull(disabled.observe(listOf(owner),listOf(hand("Thumb_Up")),t,enabled=false))
        }
    }

    @Test fun recognizesStockPosesButRejectsWeakScoresAndUnknownLabels() {
        for ((category, kind) in listOf("Thumb_Up" to SocialGesture.THUMBS_UP,"Victory" to SocialGesture.VICTORY,"Closed_Fist" to SocialGesture.FIST_BUMP, "Thumb_Down" to SocialGesture.THUMBS_DOWN, "Pointing_Up" to SocialGesture.POINT_UP, "ILoveYou" to SocialGesture.I_LOVE_YOU, "Open_Palm" to SocialGesture.HIGH_FIVE)) {
            val engine = SocialGestureEngine()
            listOf(0L,100L,200L).forEach { assertNull(engine.observe(listOf(owner),listOf(hand(category)),it)) }
            assertEquals(kind,engine.observe(listOf(owner),listOf(hand(category)),750)?.kind)
        }
        for (sample in listOf(hand("Thumb_Up",score=.5f),hand("Unknown"),hand("Pointing_Up",.5f),hand("ILoveYou",.5f))) {
            val engine = SocialGestureEngine()
            for (t in 0L..1500L step 250) assertNull(engine.observe(listOf(owner),listOf(sample),t))
        }
    }

    @Test fun stockScoreJitterDoesNotRestartAnAlreadyConfidentLike() {
        val engine=SocialGestureEngine()
        val scores=listOf(.653f,.642f,.648f,.651f,.640f)
        val reactions=scores.mapIndexedNotNull { i,score -> engine.observe(listOf(owner),listOf(hand("Thumb_Up",score)),i*430L) }
        assertEquals(1,reactions.size)
        assertEquals(SocialGesture.THUMBS_UP,reactions.single().kind)
        val neverConfident=SocialGestureEngine()
        for(t in 0L..2500L step 400) assertNull(neverConfident.observe(listOf(owner),listOf(hand("Victory",.64f)),t))
        val interrupted=SocialGestureEngine()
        assertNull(interrupted.observe(listOf(owner),listOf(hand("Thumb_Up",.66f)),0))
        assertNull(interrupted.observe(listOf(owner),listOf(hand("Thumb_Up",.54f)),430))
        assertNull(interrupted.observe(listOf(owner),listOf(hand("Thumb_Up",.59f)),860))
        assertEquals(GestureDecision.LOW_SCORE,interrupted.decision)
        val stale=SocialGestureEngine()
        assertNull(stale.observe(listOf(owner),listOf(hand("Thumb_Up",.66f)),0))
        assertNull(stale.observe(listOf(owner),listOf(hand("Thumb_Up",.64f)),900))
        val changed=SocialGestureEngine()
        assertNull(changed.observe(listOf(owner),listOf(hand("Thumb_Up",.66f)),0))
        assertNull(changed.observe(listOf(owner.copy(id="lan")),listOf(hand("Thumb_Up",.59f)),430))
        assertEquals(GestureDecision.LOW_SCORE,changed.decision)
    }

    @Test fun likeUsesCalibratedConfidenceButStillRequiresStableKnownOwner() {
        val engine=SocialGestureEngine()
        assertNull(engine.observe(listOf(owner),listOf(hand("Thumb_Up",.61f)),0))
        assertNull(engine.observe(listOf(owner),listOf(hand("Thumb_Up",.58f)),300))
        assertEquals(SocialGesture.THUMBS_UP,engine.observe(listOf(owner),listOf(hand("Thumb_Up",.57f)),650)?.kind)
        for(people in listOf(listOf(owner),listOf(owner.copy(id=null)),listOf(owner,owner.copy(id="lan")))) {
            val weak=SocialGestureEngine()
            for(t in 0L..1800L step 300) assertNull(weak.observe(people,listOf(hand("Thumb_Up",.59f)),t))
        }
    }

    @Test fun likeTriggersOnTwoValidFramesAtObservedPhoneCadence() {
        for((first,second,gap) in listOf(Triple(.659f,.604f,506L),Triple(.632f,.645f,514L))) {
            val engine=SocialGestureEngine()
            assertNull(engine.observe(listOf(owner),listOf(hand("Thumb_Up",first)),0))
            assertEquals(SocialGesture.THUMBS_UP,engine.observe(listOf(owner),listOf(hand("Thumb_Up",second)),gap)?.kind)
        }
        val short=GestureTrigger()
        assertNull(short.observe("sang",SocialGesture.THUMBS_UP,0))
        assertNull(short.observe("sang",SocialGesture.THUMBS_UP,300))
        assertNull(short.observe("sang",null,350))
        assertNotNull(short.observe("sang",SocialGesture.THUMBS_UP,500))
    }

    @Test fun minorTipOvershootIsAllowedButClippedPalmAndInvalidHandsAreRejected() {
        val points=hand("Thumb_Up").points.toMutableList().apply { this[4]=HandPoint(.5f,-.015f) }
        val sample=hand("Thumb_Up").copy(points=points)
        assertTrue(SocialGestureGeometry.valid(sample))
        val engine=SocialGestureEngine()
        assertNull(engine.observe(listOf(owner),listOf(sample),0))
        assertNotNull(engine.observe(listOf(owner),listOf(sample),650))
        val wristOutside=sample.copy(points=points.toMutableList().apply { this[0]=HandPoint(.5f,1.026f) })
        assertFalse(SocialGestureGeometry.valid(wristOutside))
        val clipped=SocialGestureEngine()
        assertNull(clipped.observe(listOf(owner),listOf(wristOutside),0))
        assertEquals(GestureDecision.CLIPPED_HAND,clipped.decision)
        assertFalse(SocialGestureGeometry.valid(sample.copy(points=points.mapIndexed { i,p -> if(i in 1..6) HandPoint(p.x,1.02f) else p })))
        assertFalse(SocialGestureGeometry.valid(sample.copy(points=points.toMutableList().apply { this[4]=HandPoint(.5f,-.10f) })))
        val distant=SocialGestureEngine()
        distant.observe(listOf(owner),listOf(hand("Thumb_Up",x=.99f)),0)
        assertEquals(GestureDecision.OUTSIDE_OWNER,distant.decision)
    }

    @Test fun fingerHeartGeometryIsScaleInvariantAndRejectsOpenOkAndFist() {
        assertTrue(SocialGestureGeometry.fingerHeart(heart()))
        val mirrored = heart().copy(points=heart().points.map { HandPoint(1f-it.x,it.y) })
        assertTrue(SocialGestureGeometry.fingerHeart(mirrored))
        val smaller = heart().copy(points=heart().points.map { HandPoint(.25f+it.x*.5f,.1f+it.y*.5f) })
        assertTrue(SocialGestureGeometry.fingerHeart(smaller))
        assertTrue(SocialGestureGeometry.fingerHeart(heart().copy(points=heart().points.map { HandPoint(it.x,it.y/2) },frameAspect=2f)))
        assertFalse(SocialGestureGeometry.fingerHeart(hand("Closed_Fist")))
        val ok = heart().points.toMutableList().apply { this[12]=HandPoint(.5f,.35f) }
        assertFalse(SocialGestureGeometry.fingerHeart(heart().copy(points=ok)))
        assertFalse(SocialGestureGeometry.fingerHeart(heart().copy(category="Open_Palm")))
        assertFalse(SocialGestureGeometry.valid(heart().copy(points=heart().points.drop(1))))
        assertFalse(SocialGestureGeometry.valid(heart().copy(points=heart().points.map { HandPoint(Float.NaN,it.y) })))
    }

    @Test fun recognizesHeartShapesAndRejectsTwoUnrelatedHands() {
        for ((hands,kind) in listOf(listOf(heart()) to SocialGesture.FINGER_HEART,twoHearts() to SocialGesture.TWO_HAND_HEART)) {
            val engine=SocialGestureEngine()
            listOf(0L,100L,200L).forEach { engine.observe(listOf(owner),hands,it) }
            assertEquals(kind,engine.observe(listOf(owner),hands,750)?.kind)
        }
        assertFalse(SocialGestureGeometry.twoHandHeart(listOf(hand("Victory",x=.35f),hand("Victory",x=.65f,side="Right"))))
        assertFalse(SocialGestureGeometry.twoHandHeart(twoHearts().map { it.copy(side="Left") }))
    }

    @Test fun waveRequiresDirectionReversalsNotStationaryPalmOrOneSweep() {
        val stationary=SocialGestureEngine()
        for(t in 0L..2500L step 250) assertNotEquals(SocialGesture.WAVE,stationary.observe(listOf(owner),listOf(hand("Open_Palm")),t)?.kind)
        val sweep=SocialGestureEngine()
        for(i in 0..8) assertNotEquals(SocialGesture.WAVE,sweep.observe(listOf(owner),listOf(hand("Open_Palm",x=.35f+i*.03f)),i*250L)?.kind)
        val waving=SocialGestureEngine()
        val xs=listOf(.45f,.53f,.45f,.53f,.45f,.53f,.45f)
        val events=xs.mapIndexedNotNull { i,x -> waving.observe(listOf(owner),listOf(hand("Open_Palm",x=x)),i*250L) }
        assertEquals(1,events.count { it.kind==SocialGesture.WAVE })
    }

    @Test fun staleEffectsExpire() {
        val event=GestureReaction(SocialGesture.FINGER_HEART,"sang",1000)
        assertFalse(event.active(999));assertTrue(event.active(1000));assertTrue(event.active(3799));assertFalse(event.active(3800))
    }
}
