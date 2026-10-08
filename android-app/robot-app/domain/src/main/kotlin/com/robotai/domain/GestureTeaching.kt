package com.robotai.domain

import kotlin.math.*

data class TaughtGesture(val id: String, val personId: String, val name: String, val action: SocialGesture?)
data class GestureFrame(val points: List<HandPoint>, val side: String, val aspect: Float, val elapsedMs: Long) {
    fun hand() = GestureHand(points, side=side, frameAspect=aspect)
}
data class GestureExample(val id: String, val gestureId: String, val sessionId: String,
    val frames: List<GestureFrame>, val createdAt: Long, val accepted: Boolean = true,
    val feedback: String = "taught")
data class GestureLessonData(val revision: Int = 0, val definitions: List<TaughtGesture> = emptyList(),
    val examples: List<GestureExample> = emptyList())
data class PersonalGesturePrediction(val definition: TaughtGesture, val confidence: Float, val distance: Float)

/** Same feature contract as tools/gesture-training/train.py. Origin/scale and handedness normalized;
 * image orientation is retained to distinguish thumb up/down. Never includes face data. */
object GestureFeatures {
    const val VERSION = "ken-hand69-v1"
    const val SIZE = 69
    fun extract(hand: GestureHand): FloatArray? {
        if (!SocialGestureGeometry.valid(hand) || hand.side !in listOf("Left", "Right")) return null
        val origin=hand.points[0]
        val scale=SocialGestureGeometry.scale(hand)
        val flip=if(hand.side=="Left") -1f else 1f
        val output=ArrayList<Float>(SIZE)
        hand.points.forEach { output.add((it.x-origin.x)/scale*flip)
            output.add((it.y-origin.y)*hand.frameAspect/scale);output.add((it.z-origin.z)/scale) }
        for(i in 0..4) {
            val angle=FingerPoseClassifier.curlAngle(hand,i)
            if(!angle.isFinite()) return null
            output.add(angle/180f)
        }
        output.add(SocialGestureGeometry.distance(hand.points[4],hand.points[8],hand.frameAspect)/scale)
        return output.toFloatArray().takeIf { values -> values.all { it.isFinite() && abs(it)<=8f } }
    }
    fun distance(a:FloatArray,b:FloatArray):Float {
        require(a.size==SIZE && b.size==SIZE)
        return sqrt(a.indices.sumOf { ((a[it]-b[it]).toDouble()).pow(2) }/SIZE).toFloat()
    }
    fun center(example:GestureExample):FloatArray? {
        val vectors=example.frames.mapNotNull { extract(it.hand()) }
        if(vectors.size!=example.frames.size || vectors.isEmpty()) return null
        return FloatArray(SIZE) { i -> vectors.map { it[i] }.average().toFloat() }
    }
}

object GestureOwnership {
    fun owns(owner:GestureOwner, hand:GestureHand):Boolean {
        if(!SocialGestureGeometry.valid(hand)) return false
        val wrist=hand.points[0]
        return if(owner.body!=null) owner.body.contains(wrist.x,wrist.y,.08f) &&
            (owner.wrists.size<2 || owner.wrists.any { hypot(it.x-wrist.x,(it.y-wrist.y)*hand.frameAspect)<.15f })
        else abs(wrist.x-(owner.left+owner.right)/2)<=max((owner.right-owner.left)*1.25f,.25f) &&
            wrist.y in owner.top-(owner.bottom-owner.top)*.8f..owner.bottom+(owner.bottom-owner.top)*3f
    }
}

/** Conservative local learning: three independent positive AND negative examples; unknown poses rejected. */
class PersonalGestureMatcher(data:GestureLessonData, private val personId:String) {
    private val definitions=data.definitions.filter { it.personId==personId }.associateBy { it.id }
    private val centers=data.examples.filter { it.accepted && it.gestureId in definitions }
        .mapNotNull { example -> GestureFeatures.center(example)?.let { example.gestureId to it } }
    fun rejects(hand:GestureHand):Boolean {
        val features=GestureFeatures.extract(hand) ?: return false
        val distances=centers.groupBy { it.first }.mapValues { (_,values) ->
            values.map { GestureFeatures.distance(features,it.second) }.sorted().take(3) }
            .filterValues { it.size>=3 }.mapValues { it.value.average().toFloat() }
        val negative=distances.filterKeys { definitions[it]?.action==null }.values.minOrNull() ?: return false
        val positive=distances.filterKeys { definitions[it]?.action!=null }.values.minOrNull() ?: return false
        return negative<=.15f && positive-negative>=.06f
    }
    fun predict(hand:GestureHand):PersonalGesturePrediction? {
        val features=GestureFeatures.extract(hand) ?: return null
        val distances=centers.groupBy { it.first }.mapValues { (_,values) ->
            values.map { GestureFeatures.distance(features,it.second) }.sorted().take(3) }
            .filterValues { it.size>=3 }.mapValues { it.value.average().toFloat() }
        val negative=distances.filterKeys { definitions[it]?.action==null }.values.minOrNull() ?: return null
        val positive=distances.filterKeys { definitions[it]?.action!=null }.entries.sortedBy { it.value }
        val best=positive.firstOrNull() ?: return null
        val competing=min(negative,positive.getOrNull(1)?.value ?: Float.MAX_VALUE)
        if(best.value>.20f || competing-best.value<.06f) return null
        val definition=definitions[best.key] ?: return null
        return PersonalGesturePrediction(definition,(1f-best.value).coerceIn(.75f,1f),best.value)
    }
}

enum class LessonPhase { IDLE, WAITING, COUNTDOWN, HOLDING, RELEASE, COMPLETE, TESTING }
data class GestureLessonUi(val personId:String?=null, val gestureId:String?=null,
    val phase:LessonPhase=LessonPhase.IDLE, val collected:Int=0, val target:Int=5,
    val progress:Float=0f, val message:String="Chọn một người quen và cử chỉ để bắt đầu",
    val hands:List<GestureHand> = emptyList(), val predictionId:String?=null,
    val predictionName:String?=null, val canConfirm:Boolean=false)

/** A capture is a stable pose after countdown, followed by a verified release, never adjacent-frame duplicates. */
class GestureLessonRecorder(private val personId:String) {
    var phase=LessonPhase.WAITING
        private set
    var progress=0f
        private set
    var message="Nhìn camera để Ken nhận ra bạn; đưa một bàn tay vào hình"
        private set
    private var since=-1L
    private var releaseSince=-1L
    private var lastFrame=-1L
    private val frames=ArrayList<GestureFrame>()
    fun interrupt() { phase=LessonPhase.WAITING;since=-1;releaseSince=-1;lastFrame=-1;frames.clear();progress=0f }
    fun observe(owners:List<GestureOwner>,hands:List<GestureHand>,now:Long):List<GestureFrame>? {
        val owner=owners.singleOrNull()?.takeIf { it.id==personId }
        if(owner==null) { interrupt();message="Chờ đúng người đã chọn; chỉ một người trong hình";return null }
        if(lastFrame>=0 && now-lastFrame !in 0..GestureTrigger.MAX_FRAME_GAP_MS) interrupt()
        lastFrame=now
        if(phase==LessonPhase.RELEASE) {
            if(hands.isEmpty()) {
                if(releaseSince<0) releaseSince=now
                if(now-releaseSince>=350) { interrupt();message="Đưa tay lên cho lượt tiếp theo" }
            } else releaseSince=-1
            return null
        }
        val hand=hands.singleOrNull()?.takeIf { GestureOwnership.owns(owner,it) && GestureFeatures.extract(it)!=null }
        if(hand==null) { interrupt();message=if(hands.size>1) "Chỉ đưa một bàn tay vào hình" else "Đưa cả bàn tay vào hình, gần người";return null }
        if(phase==LessonPhase.WAITING) { phase=LessonPhase.COUNTDOWN;since=now }
        if(phase==LessonPhase.COUNTDOWN) {
            progress=((now-since)/2000f).coerceIn(0f,1f)
            message="Chuẩn bị · ${ceil((2000-(now-since)).coerceAtLeast(0)/1000.0).toInt()}"
            if(now-since<2000) return null
            phase=LessonPhase.HOLDING;since=now;frames.clear()
        }
        val current=GestureFeatures.extract(hand)!!
        val first=frames.firstOrNull()?.let { GestureFeatures.extract(it.hand()) }
        if(first!=null && GestureFeatures.distance(first,current)>.15f) {
            frames.clear();since=now;message="Tư thế thay đổi · giữ rõ lại";progress=0f
        }
        if(frames.isEmpty() || now-since-frames.last().elapsedMs>=100)
            frames.add(GestureFrame(hand.points,hand.side,hand.frameAspect,now-since))
        progress=((now-since)/900f).coerceIn(0f,1f);message="Giữ tư thế ổn định"
        if(now-since<900 || frames.size<3) return null
        val sample=frames.takeLast(12).toList()
        frames.clear();phase=LessonPhase.RELEASE;progress=1f;releaseSince=-1
        message="Đã thu một lượt · hạ tay khỏi hình để tiếp tục"
        return sample
    }
}

/** Small trained softmax head; weights are also exported as TFLite for interoperability. */
data class GestureLinearModel(val id:String, val personId:String, val labels:List<String>,
    val weights:List<FloatArray>, val bias:FloatArray, val threshold:Float=.75f,
    val margin:Float=.20f, val radius:Float=.20f, val featureVersion:String=GestureFeatures.VERSION,
    val anchors:List<List<FloatArray>> = emptyList()) {
    fun validate() {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && featureVersion==GestureFeatures.VERSION)
        require(labels.size in 2..40 && labels.distinct().size==labels.size && weights.size==labels.size && bias.size==labels.size)
        require(weights.all { it.size==GestureFeatures.SIZE && it.all { f -> f.isFinite() && abs(f)<=100 } } && bias.all { it.isFinite() && abs(it)<=100 })
        require(threshold in .75f.. .99f && margin in .10f.. .9f && radius in .05f.. .30f)
        require(anchors.size==labels.size && anchors.all { examples -> examples.size in 3..100 &&
            examples.all { it.size==GestureFeatures.SIZE && it.all { f -> f.isFinite() && abs(f)<=8f } } })
    }
    fun scores(features:FloatArray):FloatArray {
        require(features.size==GestureFeatures.SIZE && features.all { it.isFinite() })
        val logits=FloatArray(labels.size) { i -> bias[i]+features.indices.sumOf { (features[it]*weights[i][it]).toDouble() }.toFloat() }
        val peak=logits.maxOrNull()!!;val exps=logits.map { exp((it-peak).toDouble()) };val total=exps.sum()
        return FloatArray(labels.size) { (exps[it]/total).toFloat() }
    }
}
