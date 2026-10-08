package com.robotai.domain

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

enum class SocialGesture(val label: String) {
    FINGER_HEART("Thả tim"), TWO_HAND_HEART("Tim hai tay"), WAVE("Vẫy chào"),
    THUMBS_UP("Ngón cái"), VICTORY("Chữ V"), FIST_BUMP("Chạm nắm tay"),
    HIGH_FIVE("Đập tay"), THUMBS_DOWN("Không đồng ý"), POINT_UP("Có ý tưởng"),
    I_LOVE_YOU("Yêu bạn"), OK_SIGN("Đồng ý"), ROCK_ON("Rock on");
    val loving get() = this == FINGER_HEART || this == TWO_HAND_HEART || this == I_LOVE_YOU
}

data class HandPoint(val x: Float, val y: Float, val z: Float = 0f)
data class GestureHand(val points: List<HandPoint>, val category: String = "", val score: Float = 0f, val side: String = "",
    val frameAspect: Float = 1f, val learnedGesture:SocialGesture?=null,
    val learnedName:String?=null, val learnedScore:Float=0f, val learnedRejected:Boolean=false)
data class GestureOwner(val id: String?, val left: Float, val top: Float, val right: Float, val bottom: Float,
    val body: TrackBox? = null, val wrists: List<HandPoint> = emptyList())
data class GestureReaction(val kind: SocialGesture, val personId: String, val atMs: Long, val learnedName:String?=null) {
    fun active(now: Long) = now - atMs in 0 until DURATION_MS
    companion object { const val DURATION_MS = 2800L }
}

/** Require a steady pose, a neutral interval to rearm, and a global cooldown. */
class GestureTrigger {
    companion object { const val MAX_FRAME_GAP_MS=850L }
    private var candidate: Pair<String, SocialGesture>? = null
    private var person: String? = null
    private var since = 0L
    private var lastFrame = -1L
    private var lastPositive = -1L
    private var neutralSince: Long? = null
    private val votes=ArrayDeque<Pair<Long,SocialGesture?>>()
    var latchedGesture: SocialGesture? = null
        private set
    var progress=0f
        private set
    private var lastEmission: Long? = null
    val waitingForRelease get() = latchedGesture!=null

    fun reset() { candidate = null; person=null; lastFrame = -1;lastPositive=-1; neutralSince = null;latchedGesture=null;votes.clear();progress=0f }

    fun observe(personId: String?, kind: SocialGesture?, now: Long): GestureReaction? {
        if (lastFrame >= 0 && now - lastFrame !in 0..MAX_FRAME_GAP_MS) { candidate = null; neutralSince = null;votes.clear();progress=0f }
        if(person != personId) { reset();person=personId }
        lastFrame = now
        if(personId==null) { reset();return null }
        votes.addLast(now to kind)
        while(votes.size>5 || votes.isNotEmpty() && now-votes.first().first>900) votes.removeFirst()
        if (kind == null) {
            if(now-lastPositive>220) { candidate = null;votes.clear();progress=0f }
            if (neutralSince == null) neutralSince = now
            if (now - neutralSince!! >= 350) latchedGesture=null
            return null
        }
        neutralSince = null
        val matching=votes.filter { it.second==kind }
        if(matching.size<2 || matching.size.toFloat()/votes.size<.6f) return null
        val next = personId to kind
        if (next != candidate) { candidate = next; since = matching.first().first }
        lastPositive=now
        val holdMs=when(kind) { SocialGesture.HIGH_FIVE -> 750L;SocialGesture.WAVE -> 180L;else -> 350L }
        progress=((now-since).toFloat()/holdMs).coerceIn(0f,1f)
        if (latchedGesture==kind || now - since < holdMs || lastEmission?.let { now - it < 1000 } == true) return null
        latchedGesture=kind; lastEmission = now
        return GestureReaction(kind, personId, now)
    }
}

/** Geometry rules supplement the stock model; no additional model download is required. */
object SocialGestureGeometry {
    // Landmarks are predictions and may overshoot the image slightly. Keep the palm
    // visible and reject substantially clipped hands, rather than rejecting one tip.
    fun valid(hand: GestureHand) = hand.points.size == 21 && hand.points.all {
        it.x.isFinite() && it.y.isFinite() && it.z.isFinite() && it.x in -.05f..1.05f && it.y in -.05f..1.05f
    } && listOf(0,5,9,13,17).all { i -> hand.points[i].let { it.x in 0f..1f && it.y in 0f..1f } } &&
        hand.points.count { it.x in 0f..1f && it.y in 0f..1f } >= 17 &&
        hand.frameAspect.isFinite() && hand.frameAspect in .1f..10f && scale(hand) > .025f
    fun distance(a: HandPoint, b: HandPoint, aspect: Float = 1f) = hypot(a.x - b.x, (a.y - b.y) * aspect)
    fun scale(hand: GestureHand) = distance(hand.points[0], hand.points[9], hand.frameAspect)
    private fun folded(hand: GestureHand, pip: Int, tip: Int) =
        distance(hand.points[tip], hand.points[0],hand.frameAspect) < distance(hand.points[pip], hand.points[0],hand.frameAspect) * 1.12f

    fun fingerHeart(hand: GestureHand): Boolean {
        if (!valid(hand) || hand.category in listOf("Thumb_Up", "Victory", "Open_Palm")) return false
        val p = hand.points; val s = scale(hand); val sy = s / hand.frameAspect
        return distance(p[4], p[8],hand.frameAspect) < s * .28f &&
            p[4].y < p[5].y - sy * .15f && p[8].y < p[5].y - sy * .15f &&
            p[6].y < p[5].y - sy * .12f && distance(p[4], p[5],hand.frameAspect) > s * .5f &&
            listOf(10 to 12, 14 to 16, 18 to 20).all { (pip, tip) -> folded(hand, pip, tip) }
    }

    fun twoHandHeart(hands: List<GestureHand>): Boolean {
        if (hands.size != 2 || !hands.all(::valid)) return false
        val (a, b) = hands.sortedBy { it.points[0].x }
        if (a.side.isNotBlank() && a.side == b.side) return false
        val s = (scale(a) + scale(b)) / 2
        val aspect = (a.frameAspect + b.frameAspect) / 2
        val upperY = (a.points[8].y + b.points[8].y) / 2
        val lowerY = (a.points[4].y + b.points[4].y) / 2
        val centerX = (a.points[8].x + b.points[8].x) / 2
        return distance(a.points[8], b.points[8],aspect) < s * .4f &&
            distance(a.points[4], b.points[4],aspect) < s * .4f &&
            (lowerY - upperY) * aspect in s * .35f..s * 1.6f &&
            abs((a.points[4].x + b.points[4].x) / 2 - centerX) < s * .35f &&
            a.points[6].x < centerX - s * .25f && b.points[6].x > centerX + s * .25f &&
            a.points[6].y < lowerY && b.points[6].y < lowerY &&
            distance(a.points[0], b.points[0],aspect) > s * .9f &&
            hands.all { h -> listOf(10 to 12, 14 to 16, 18 to 20).count { (pip, tip) -> folded(h, pip, tip) } >= 2 }
    }
}

enum class GestureDecision { IDLE, DISABLED, NO_OWNER, MULTIPLE_PEOPLE, CLIPPED_HAND, OUTSIDE_OWNER, LOW_SCORE, HOLDING, WAIT_RELEASE, TRIGGERED }

/** Only a single visible registered person is eligible. */
class SocialGestureEngine {
    companion object {
        const val MIN_STOCK_SCORE = .65f; const val CONTINUE_STOCK_SCORE = .60f
        const val MIN_THUMB_SCORE = .60f; const val CONTINUE_THUMB_SCORE = .55f
    }
    private val trigger = GestureTrigger()
    private var ownerId: String? = null
    private var waveSide = ""
    private val wave = ArrayDeque<Pair<Long, Float>>()
    private var wavingUntil = -1L
    private var stockCategory = ""
    private var stockAt = -1L
    var decision = GestureDecision.IDLE
        private set
    var candidateKind: SocialGesture? = null
        private set
    val progress get() = trigger.progress
    fun reset() { ownerId = null; wave.clear(); wavingUntil = -1; trigger.reset(); stockCategory="";stockAt=-1;decision=GestureDecision.IDLE;candidateKind=null }

    fun observe(faces: List<GestureOwner>, detectedHands: List<GestureHand>, now: Long, enabled: Boolean = true): GestureReaction? {
        val owner = faces.singleOrNull()?.takeIf { it.id != null && it.right > it.left && it.bottom > it.top }
        if (!enabled || owner == null) {
            reset();decision=if(!enabled) GestureDecision.DISABLED else if(faces.size>1) GestureDecision.MULTIPLE_PEOPLE else GestureDecision.NO_OWNER
            return null
        }
        if (ownerId != owner.id) { reset(); ownerId = owner.id }
        val validHands = detectedHands.filter(SocialGestureGeometry::valid)
        val ownedHands = validHands.filter { GestureOwnership.owns(owner,it) }
        if (ownedHands.isEmpty() || detectedHands.size>2) {
            candidateKind=null
            wave.clear(); wavingUntil = -1;stockCategory="";stockAt=-1
            decision=when { detectedHands.isEmpty() -> GestureDecision.IDLE;validHands.isEmpty() -> GestureDecision.CLIPPED_HAND;else -> GestureDecision.OUTSIDE_OWNER }
            return trigger.observe(owner.id, null, now)
        }
        val hands = if (SocialGestureGeometry.twoHandHeart(ownedHands)) ownedHands else {
            val expressive=ownedHands.filter { !it.learnedRejected && (it.category !in listOf("", "None") && it.score>=.55f ||
                SocialGestureGeometry.fingerHeart(it) || FingerPoseClassifier.classify(it)!=null || it.learnedGesture!=null) }
            when {
                ownedHands.size==1 -> ownedHands
                expressive.size==1 -> expressive
                expressive.size==2 && expressive[0].category.isNotBlank() &&
                    expressive[0].category!="None" && expressive[0].category==expressive[1].category ->
                    listOf(expressive.maxBy { it.score })
                else -> emptyList()
            }
        }
        val hand=hands.singleOrNull()
        val supported=hand?.category in setOf("Thumb_Up","Thumb_Down","Victory","Closed_Fist","Open_Palm","Pointing_Up","ILoveYou")
        val continuing=hand!=null && stockCategory==hand.category && now-stockAt in 0..GestureTrigger.MAX_FRAME_GAP_MS
        val requiredScore=if(hand?.category=="Thumb_Up") {
            if(continuing) CONTINUE_THUMB_SCORE else MIN_THUMB_SCORE
        } else if(continuing) CONTINUE_STOCK_SCORE else MIN_STOCK_SCORE
        val stockAccepted=supported && hand!=null && hand.score.isFinite() &&
            hand.score >= requiredScore
        if(stockAccepted) { stockCategory=hand!!.category;stockAt=now } else { stockCategory="";stockAt=-1 }
        val geometric=hand?.let(FingerPoseClassifier::classify)
        val waving=hand?.category=="Open_Palm" && stockAccepted && isWaving(hand,now)
        val kind = when {
            SocialGestureGeometry.twoHandHeart(hands) -> SocialGesture.TWO_HAND_HEART
            hands.size != 1 -> null
            waving -> SocialGesture.WAVE
            hand?.learnedRejected==true -> null
            hand?.learnedGesture!=null && hand.learnedScore.isFinite() && hand.learnedScore>=.75f -> hand.learnedGesture
            SocialGestureGeometry.fingerHeart(hands[0]) -> SocialGesture.FINGER_HEART
            stockAccepted -> when (hands[0].category) {
                "Thumb_Up" -> SocialGesture.THUMBS_UP
                "Thumb_Down" -> SocialGesture.THUMBS_DOWN
                "Pointing_Up" -> SocialGesture.POINT_UP
                "ILoveYou" -> SocialGesture.I_LOVE_YOU
                "Victory" -> SocialGesture.VICTORY
                "Closed_Fist" -> SocialGesture.FIST_BUMP
                "Open_Palm" -> SocialGesture.HIGH_FIVE
                else -> null
            }
            hand?.category in listOf("None","") -> geometric
            geometric==SocialGesture.THUMBS_UP && hand?.category=="Thumb_Up" && hand.score>=.4f -> geometric
            else -> null
        }
        if (hands.size != 1 || hands[0].category != "Open_Palm" || !stockAccepted) { wave.clear(); wavingUntil = -1 }
        val reaction=trigger.observe(owner.id, kind, now)
        candidateKind=kind
        decision=when { reaction!=null -> GestureDecision.TRIGGERED;kind!=null && trigger.latchedGesture==kind -> GestureDecision.WAIT_RELEASE;kind!=null -> GestureDecision.HOLDING;
            supported && !stockAccepted -> GestureDecision.LOW_SCORE;else -> GestureDecision.IDLE }
        return reaction?.copy(learnedName=if(hand?.learnedGesture==kind) hand?.learnedName else null)
    }

    private fun isWaving(hand: GestureHand, now: Long): Boolean {
        if (waveSide != hand.side || wave.lastOrNull()?.let { now - it.first > GestureTrigger.MAX_FRAME_GAP_MS } == true) {
            wave.clear(); wavingUntil = -1; waveSide = hand.side
        }
        val x = hand.points[0].x
        wave.addLast(now to x)
        while (wave.isNotEmpty() && now - wave.first().first > 2400) wave.removeFirst()
        val excursion = SocialGestureGeometry.scale(hand) * .3f
        var extreme = wave.first().second; var direction = 0; var reversals = 0
        for ((_, value) in wave) {
            if (direction == 0 && abs(value - extreme) >= excursion) { direction = if (value > extreme) 1 else -1; extreme = value }
            else if (direction > 0) {
                if (value > extreme) extreme = value
                else if (extreme - value >= excursion) { reversals++; direction = -1; extreme = value }
            } else if (direction < 0) {
                if (value < extreme) extreme = value
                else if (value - extreme >= excursion) { reversals++; direction = 1; extreme = value }
            }
        }
        if (reversals >= 2) wavingUntil = now + 900
        return now <= wavingUntil
    }
}
