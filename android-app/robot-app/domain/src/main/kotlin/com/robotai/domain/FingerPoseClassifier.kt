package com.robotai.domain

import kotlin.math.*

/** Curl-angle calculation adapted from andypotato/fingerpose (MIT).
 * Copyright (c) 2020 Andreas Schallwig. License: app/src/main/assets/licenses/fingerpose-MIT.txt.
 * Gesture rules and MediaPipe coordinate conversion are RobotAI additions.
 */
object FingerPoseClassifier {
    fun curlAngle(hand: GestureHand, finger: Int): Float {
        if(hand.points.size!=21 || finger !in 0..4) return Float.NaN
        val ids=if(finger==0) intArrayOf(1,3,4) else intArrayOf(0,2+finger*4,4+finger*4)
        val (a,b,c)=ids.map { hand.points[it] }
        fun length(p: HandPoint,q: HandPoint)=sqrt((p.x-q.x).pow(2)+((p.y-q.y)*hand.frameAspect).pow(2)+(p.z-q.z).pow(2))
        val ab=length(a,b);val bc=length(b,c);val ac=length(a,c)
        if(ab<.0001f || bc<.0001f) return Float.NaN
        return acos(((ab*ab+bc*bc-ac*ac)/(2*ab*bc)).coerceIn(-1f,1f))*180f/PI.toFloat()
    }
    fun classify(hand: GestureHand): SocialGesture? {
        if(!SocialGestureGeometry.valid(hand) || hand.points.any { !it.z.isFinite() }) return null
        val angles=(0..4).map { curlAngle(hand,it) }
        if(angles.any { !it.isFinite() }) return null
        fun straight(i:Int)=angles[i]>150
        fun bent(i:Int)=angles[i]<100
        val p=hand.points;val s=SocialGestureGeometry.scale(hand)
        val touch=SocialGestureGeometry.distance(p[4],p[8],hand.frameAspect)<s*.3f
        return when {
            touch && (2..4).all(::straight) && !straight(1) -> SocialGesture.OK_SIGN
            straight(1) && straight(4) && bent(2) && bent(3) && bent(0) -> SocialGesture.ROCK_ON
            straight(0) && straight(1) && straight(4) && bent(2) && bent(3) -> SocialGesture.I_LOVE_YOU
            straight(0) && (1..4).all(::bent) && (p[2].y-p[4].y)*hand.frameAspect>s*.45f -> SocialGesture.THUMBS_UP
            straight(0) && (1..4).all(::bent) && (p[4].y-p[2].y)*hand.frameAspect>s*.45f -> SocialGesture.THUMBS_DOWN
            else -> null
        }
    }
}
