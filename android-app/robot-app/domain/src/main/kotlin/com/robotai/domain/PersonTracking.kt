package com.robotai.domain

import kotlin.math.*

/** Normalized image coordinates. No image or person's name is retained here. */
data class TrackBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right-left
    val height get() = bottom-top
    val cx get() = (left+right)/2
    val cy get() = (top+bottom)/2
    val valid get() = listOf(left,top,right,bottom).all { it.isFinite() } && width>.01f && height>.01f
    fun contains(x: Float,y: Float,pad: Float=0f) = x in left-pad..right+pad && y in top-pad..bottom+pad
    fun iou(other: TrackBox): Float {
        val area=max(0f,min(right,other.right)-max(left,other.left))*max(0f,min(bottom,other.bottom)-max(top,other.top))
        return area/(width*height+other.width*other.height-area).coerceAtLeast(.00001f)
    }
    fun moved(dx: Float,dy: Float) = TrackBox(left+dx,top+dy,right+dx,bottom+dy)
}

data class FaceEvidence(val detectorTrack: Int, val box: TrackBox, val match: IdentityMatch?,
    val strongMismatch: Boolean=false)
data class PersonObservation(val box: TrackBox, val score: Float, val appearance: FloatArray= floatArrayOf(),
    val face: FaceEvidence?=null, val wrists: List<HandPoint> = emptyList(),
    val objects: Set<String> = emptySet(), val bodyDetected: Boolean=true)
enum class TrackingSource { FACE, BODY, FACE_GRACE, UNCERTAIN, OCCLUDED }
data class TrackedPerson(val trackId: Int, val personId: String?, val box: TrackBox,
    val faceBox: TrackBox?, val wrists: List<HandPoint>, val visible: Boolean,
    val source: TrackingSource, val lastSeenMs: Long, val lastFaceMs: Long, val bodyDetected: Boolean=true) {
    val gestureEligible get() = visible && personId!=null && source in setOf(TrackingSource.FACE,TrackingSource.BODY,TrackingSource.FACE_GRACE)
}

/** Conservative local tracking: geometry, velocity, appearance bank, then object context.
 * Appearance and objects support continuity; only a face match can assign an enrolled id.
 * Ambiguous associations discard that assignment, rather than copying it to another body.
 */
class PersonTracker {
    private data class Track(val id: Int, var box: TrackBox, var lastSeen: Long,
        var personId: String?=null, var candidate: String?=null, var count: Int=0,
        var lastMatch: Long=-1, var lastFace: Long=-1, var vx: Float=0f,var vy: Float=0f,
        var faceBox: TrackBox?=null, var visible: Boolean=false,
        var source: TrackingSource=TrackingSource.UNCERTAIN,
        val appearance: MutableList<FloatArray> = mutableListOf(),var objects: Set<String> = emptySet(),
        var wrists: List<HandPoint> = emptyList(), var body: Boolean=true,var faceTrack: Int?=null,var lastWasBody: Boolean=true)
    private val tracks=mutableListOf<Track>()
    private var nextId=1
    private var lastFrame=-1L
    fun reset() { tracks.clear();lastFrame=-1 }
    fun update(observations: List<PersonObservation>, now: Long, enrolledIds: Set<String>, sceneChanged: Boolean=false): List<TrackedPerson> {
        if(sceneChanged || lastFrame>=0 && (now<lastFrame || now-lastFrame>2000)) reset()
        lastFrame=now
        tracks.removeAll { now-it.lastSeen>1500 }
        tracks.forEach { it.visible=false;it.wrists=emptyList();if(it.personId !in enrolledIds) it.personId=null }
        val detections=observations.filter { it.box.valid && it.score>=.30f }.take(8)
        val scores=tracks.map { track -> detections.map { association(track,it,now) } }
        val used=mutableSetOf<Int>()
        val assigned=mutableSetOf<Int>()
        val pairs=scores.flatMapIndexed { t,row -> row.mapIndexed { d,s -> Triple(t,d,s) } }.sortedByDescending { it.third }
        for((t,d,score) in pairs) {
            if(score<.34f || t in assigned || d in used) continue
            val rowOther=scores[t].filterIndexed { i,_ -> i!=d }.maxOrNull() ?: -1f
            val colOther=scores.filterIndexed { i,_ -> i!=t }.maxOfOrNull { it[d] } ?: -1f
            val overlap=detections.filterIndexed { i,_ -> i!=d }.any { it.box.iou(detections[d].box)>.32f }
            val ambiguous=overlap || score-rowOther<.10f || score-colOther<.10f
            val track=tracks[t]
            if(ambiguous) invalidate(track)
            accept(track,detections[d],now,!ambiguous)
            assigned.add(t);used.add(d)
        }
        detections.forEachIndexed { index,observation -> if(index !in used) {
            val track=Track(nextId++,observation.box,now,body=observation.bodyDetected)
            tracks.add(track)
            val isolated=detections.filterIndexed { i,_ -> i!=index }.none { it.box.iou(observation.box)>.32f }
            accept(track,observation,now,isolated)
        } }
        // Never publish the same enrolled person on two bodies.
        tracks.filter { it.visible && it.personId!=null }.groupBy { it.personId }.values.filter { it.size>1 }
            .flatten().forEach(::invalidate)
        return tracks.map { t -> TrackedPerson(t.id,t.personId,t.box,t.faceBox,t.wrists,t.visible,
            if(t.visible) t.source else TrackingSource.OCCLUDED,t.lastSeen,t.lastFace,t.lastWasBody) }
    }
    private fun invalidate(t: Track) { t.personId=null;t.candidate=null;t.count=0;t.lastFace=-1;t.source=TrackingSource.UNCERTAIN }
    private fun association(t: Track,o: PersonObservation,now: Long): Float {
        if(t.body!=o.bodyDetected) {
            val face=o.face ?: return -1f
            val oldHead=t.faceBox ?: TrackBox(t.box.left,t.box.top,t.box.right,t.box.top+t.box.height*.25f)
            val sameFace=t.faceTrack==face.detectorTrack || t.personId!=null && t.personId==face.match?.personId
            return if(sameFace && oldHead.iou(face.box)>.25f && now-t.lastSeen<=550) .9f else -1f
        }
        val dt=(now-t.lastSeen).coerceAtMost(700)/1000f
        val predicted=t.box.moved(t.vx*dt,t.vy*dt)
        val overlap=predicted.iou(o.box)
        val distance=hypot(predicted.cx-o.box.cx,predicted.cy-o.box.cy)
        val radius=max(predicted.width,predicted.height)*.6f+.05f
        if(distance>radius) return -1f
        if(!o.bodyDetected && t.faceTrack!=o.face?.detectorTrack) return -1f
        val appearance=t.appearance.maxOfOrNull { IdentityMath.cosine(it,o.appearance) } ?: 0f
        // A positional guess cannot reacquire a hidden person without matching appearance.
        if(now-t.lastSeen>550 && appearance<.80f) return -1f
        if(overlap<.25f && t.appearance.isNotEmpty() && appearance<.65f) return -1f
        val objectAgreement=if(t.objects.isNotEmpty()) t.objects.intersect(o.objects).size.toFloat()/t.objects.size else 0f
        return .60f*overlap+.25f*(1-distance/radius)+.12f*max(0f,appearance)+.03f*objectAgreement
    }
    private fun accept(t: Track,o: PersonObservation,now: Long,unambiguous: Boolean) {
        val dt=(now-t.lastSeen).coerceAtLeast(100)/1000f
        if(t.body==o.bodyDetected) {
            t.vx=(.5f*t.vx+.5f*(o.box.cx-t.box.cx)/dt).coerceIn(-.6f,.6f)
            t.vy=(.5f*t.vy+.5f*(o.box.cy-t.box.cy)/dt).coerceIn(-.6f,.6f)
        } else { t.vx=0f;t.vy=0f }
        // If the body detector briefly misses, retain its geometry and fall back to this
        // same face track. A body can also replace a face-only track without losing its id.
        if(!t.body || o.bodyDetected) t.box=o.box
        t.lastSeen=now;t.visible=true;t.faceBox=o.face?.box
        if(o.face!=null) t.faceTrack=o.face.detectorTrack
        t.body=t.body || o.bodyDetected;t.lastWasBody=o.bodyDetected;t.wrists=o.wrists
        if(!unambiguous) { t.source=TrackingSource.UNCERTAIN;return }
        if(o.face?.strongMismatch==true) invalidate(t)
        val match=o.face?.match
        if(match!=null) {
            if(t.personId!=null && t.personId!=match.personId) invalidate(t)
            if(t.candidate!=match.personId || now-t.lastMatch>1500) { t.candidate=match.personId;t.count=0 }
            t.count++;t.lastMatch=now
            if(t.count>=3 || t.personId==match.personId) { t.personId=match.personId;t.lastFace=now }
        }
        if(!o.bodyDetected && now-t.lastFace>1500) t.personId=null
        t.source=when {
            t.personId==null -> TrackingSource.UNCERTAIN
            match?.personId==t.personId -> TrackingSource.FACE
            o.bodyDetected -> TrackingSource.BODY
            else -> TrackingSource.FACE_GRACE
        }
        if((o.bodyDetected || !t.body) && o.appearance.isNotEmpty() && t.appearance.none { IdentityMath.cosine(it,o.appearance)>.98f }) {
            t.appearance.add(o.appearance.copyOf());if(t.appearance.size>8) t.appearance.removeAt(0)
        }
        t.objects=o.objects
    }
}

/** Adds diverse, anchored face templates after a consistent sequence of verified observations.
 * Original enrollment is never changed. Body-only estimates never enter durable learning.
 */
class FaceTemplateLearner {
    private data class Candidate(val personId: String,val trackId: Int,val vector: FloatArray,
        val since: Long,var last: Long,var count: Int)
    private var pending: Candidate?=null
    private val lastSaved=mutableMapOf<String,Long>()
    fun reset() { pending=null }
    fun observe(personId: String?,trackId: Int,vector: FloatArray?,anchors: List<FloatArray>,learned: List<FloatArray>,
        score: Float,threshold: Float,quality: Boolean,unambiguous: Boolean,now: Long): FloatArray? {
        val anchorScore=if(vector==null) -1f else anchors.map { IdentityMath.cosine(it,vector) }.sortedDescending().take(3).average().toFloat()
        if(personId==null || vector==null || !quality || !unambiguous || anchors.isEmpty() ||
            score<max(.65f,threshold+.05f) || anchorScore<max(.60f,threshold) ||
            lastSaved[personId]?.let { now-it<30000 }==true || (anchors+learned).any { IdentityMath.cosine(it,vector)>.97f }) {
            reset();return null
        }
        val previous=pending
        if(previous==null || previous.personId!=personId || previous.trackId!=trackId || now-previous.last !in 0..800 || IdentityMath.cosine(previous.vector,vector)<.85f) {
            pending=Candidate(personId,trackId,vector.copyOf(),now,now,1);return null
        }
        previous.last=now;previous.count++
        if(previous.count<5 || now-previous.since<2000) return null
        lastSaved[personId]=now;reset()
        return vector.copyOf()
    }
}
