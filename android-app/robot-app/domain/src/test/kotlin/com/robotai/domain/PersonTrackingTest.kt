package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class PersonTrackingTest {
    private val box=TrackBox(.25f,.1f,.65f,.95f)
    private val head=TrackBox(.35f,.12f,.55f,.3f)
    private val shirt=floatArrayOf(1f,0f,0f)
    private val ids=setOf("sang","lan")
    private fun observation(id: String?="sang",body: Boolean=true,b: TrackBox=box,appearance: FloatArray=shirt)=
        PersonObservation(b,.9f,appearance,face=FaceEvidence(1,head,id?.let { IdentityMatch(it,.9f) }),bodyDetected=body)
    private fun confirm(tracker: PersonTracker,body: Boolean=true) {
        repeat(3) { tracker.update(listOf(observation(body=body)),it*250L,ids) }
    }
    @Test fun faceNeedsConfirmationBeforeBodyCanCarryIdentity() {
        val tracker=PersonTracker()
        assertNull(tracker.update(listOf(observation()),0,ids).single().personId)
        assertNull(tracker.update(listOf(observation()),250,ids).single().personId)
        assertEquals("sang",tracker.update(listOf(observation()),500,ids).single().personId)
    }
    @Test fun turnedBackStaysKnownAsLongAsBodyIsContinuouslyVisible() {
        val tracker=PersonTracker();confirm(tracker)
        for(time in 750L..60750L step 250L) {
            val p=tracker.update(listOf(observation(null).copy(face=null)),time,ids).single()
            assertEquals("sang",p.personId);assertEquals(TrackingSource.BODY,p.source);assertTrue(p.gestureEligible)
        }
    }
    @Test fun movingBodyWithAppearanceKeepsItsTrack() {
        val tracker=PersonTracker();confirm(tracker)
        val p=tracker.update(listOf(observation(null,b=box.moved(.05f,0f)).copy(face=null)),750,ids).single()
        assertEquals("sang",p.personId)
    }
    @Test fun faceOnlyGraceExpiresAndDoesNotBecomePermanentBodyTracking() {
        val tracker=PersonTracker();confirm(tracker,false)
        assertEquals(TrackingSource.FACE_GRACE,tracker.update(listOf(observation(null,false)),750,ids).single().source)
        for(time in 1000L..2250L step 250L) tracker.update(listOf(observation(null,false)),time,ids)
        assertNull(tracker.update(listOf(observation(null,false)),2500,ids).single().personId)
    }
    @Test fun lossKeepsNoGestureOwnerThenExpiresAndReentryNeedsFace() {
        val tracker=PersonTracker();confirm(tracker)
        val hidden=tracker.update(emptyList(),750,ids).single()
        assertFalse(hidden.visible);assertFalse(hidden.gestureEligible);assertEquals(TrackingSource.OCCLUDED,hidden.source)
        assertTrue(tracker.update(emptyList(),2250,ids).isEmpty())
        assertNull(tracker.update(listOf(observation(null).copy(face=null)),2500,ids).single().personId)
    }
    @Test fun replacementAfterOcclusionCannotInheritNameFromPositionOrCup() {
        val tracker=PersonTracker();confirm(tracker)
        tracker.update(emptyList(),750,ids)
        val next=tracker.update(listOf(observation(null,appearance=floatArrayOf(0f,1f,0f)).copy(face=null,objects=setOf("cup"))),1500,ids)
        assertNull(next.single { it.visible }.personId)
    }
    @Test fun conflictingFaceImmediatelyStopsOldNameUntilNewFaceConfirmed() {
        val tracker=PersonTracker();confirm(tracker)
        assertNull(tracker.update(listOf(observation("lan")),750,ids).single().personId)
        tracker.update(listOf(observation("lan")),1000,ids)
        assertEquals("lan",tracker.update(listOf(observation("lan")),1250,ids).single().personId)
    }
    @Test fun goodUnknownFaceRejectsCachedIdentity() {
        val tracker=PersonTracker();confirm(tracker)
        assertNull(tracker.update(listOf(observation(null).copy(face=FaceEvidence(1,head,null,true))),750,ids).single().personId)
    }
    @Test fun crossingBodiesDiscardAmbiguousAssociation() {
        val tracker=PersonTracker();confirm(tracker)
        val crossing=listOf(observation(null).copy(face=null),observation(null,b=box.moved(.1f,0f)).copy(face=null))
        assertTrue(tracker.update(crossing,750,ids).all { it.personId==null })
    }
    @Test fun cameraSceneChangePauseAndDeletionClearAssignments() {
        for(mode in 0..2) {
            val tracker=PersonTracker();confirm(tracker)
            val result=tracker.update(listOf(observation(null).copy(face=null)),if(mode==1) 4000 else 750,
                if(mode==2) emptySet() else ids,sceneChanged=mode==0)
            assertTrue(result.all { it.personId==null })
        }
    }
    @Test fun changedFaceTrackingIdDoesNotInheritFallbackName() {
        val tracker=PersonTracker();confirm(tracker,false)
        val result=tracker.update(listOf(observation(null,false).copy(face=FaceEvidence(99,head,null))),750,ids)
        assertNull(result.single { it.visible }.personId)
    }
    @Test fun temporaryBodyDetectorMissKeepsTheSameFaceIdentityThenRestoresBody() {
        val tracker=PersonTracker();confirm(tracker)
        val fallback=observation(null,false,b=head)
        val p=tracker.update(listOf(fallback),750,ids).single { it.visible }
        assertEquals("sang",p.personId);assertFalse(p.bodyDetected)
        val restored=tracker.update(listOf(observation(null).copy(face=null)),1000,ids).single { it.visible }
        assertEquals(p.trackId,restored.trackId);assertEquals("sang",restored.personId);assertTrue(restored.bodyDetected)
    }
}
