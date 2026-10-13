package com.robotai.robot.identity

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mlkit.vision.face.Face
import com.robotai.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.Executors

data class FaceSeen(val track: Int, val personId: String?, val label: String, val x: Float)
data class IdentityUi(val people: List<Person> = emptyList(), val faces: List<FaceSeen> = emptyList(),
    val status: String = "Camera chưa sẵn sàng", val enrollment: String? = null, val captured: Int = 0,
    val voiceBusy: Boolean = false, val voiceSeconds: Float = 0f, val voiceProcessing: Boolean = false,
    val speaker: String = "Chưa xác định", val gesture: String = "", val error: String? = null,
    val gestureReaction: GestureReaction? = null, val socialGesturesEnabled: Boolean = true,
    val trackedPeople: List<TrackedPerson> = emptyList(), val sceneObjects: List<SceneObject> = emptyList(),
    val learningEnabled: Boolean = true, val learningStatus: String = "",
    val gestureDecision: GestureDecision = GestureDecision.IDLE,
    val gestureCandidate: SocialGesture? = null, val gestureProgress: Float = 0f,
    val expressionsEnabled: Boolean = true, val facialExpression: KnownFaceExpression? = null,
    val expressionError: String? = null)

class IdentityController(private val context: Context, scopeKey: String) : AutoCloseable {
    private val store=PeopleStore(context,scopeKey)
    val teaching=GestureTeacher(context,scopeKey)
    val prefs=context.getSharedPreferences("identity-$scopeKey",0)
    private val worker=Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val jobs=CoroutineScope(SupervisorJob()+worker)
    private val mutable=MutableStateFlow(IdentityUi(socialGesturesEnabled=prefs.getBoolean("socialGestures",true),
        learningEnabled=prefs.getBoolean("adaptiveLearning",true), expressionsEnabled=prefs.getBoolean("facialExpressions",true)))
    val state=mutable.asStateFlow()
    private val expressionWorker=Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val expressionEngine=FacialExpressionEngine()
    private var expressionModel:FacialExpressionDetector?=null
    private var expressionTask:Job?=null
    private var expressionVersion=0L
    private var expressionOwner:ExpressionOwner?=null
    private var lastExpressionAt=-1L
    private var expressionRetryAt=-1L
    private var engine: FaceEmbedding?=null
    private var voice: VoiceEmbedding?=null
    private var gestures: SocialGestureDetector?=null
    private var vision: SceneVision?=null
    private var activeSpeaker: TalkNetActiveSpeaker?=null
    private var activeSpeakerAttempted=false
    private data class SpeakerFrame(val at:Long,val personId:String,val gray:FloatArray,val facing:Boolean)
    private val speakerFrames=java.util.ArrayDeque<SpeakerFrame>()
    private var verifiedSpeaker:String?=null
    private var verifiedAt=-1L
    private var temporaryTrack:Int?=null
    private var temporaryPerson:String?=null
    @Volatile private var lastPcmAt=-1L
    @Volatile private var lastSpeechAt=-1L
    private var currentFacing=false
    private var poseAt=-1L
    fun speechActivity() { lastSpeechAt=SystemClock.elapsedRealtime() }
    fun hasRecentSpeech(now:Long) = lastSpeechAt>=0 && now-lastSpeechAt in 0..1500
    fun enableFacialExpressions(enabled:Boolean) {
        prefs.edit().putBoolean("facialExpressions",enabled).apply()
        jobs.launch { resetExpressions(); mutable.value=mutable.value.copy(expressionsEnabled=enabled,expressionError=null) }
    }
    private fun resetExpressions() {
        expressionVersion++;expressionOwner=null;expressionEngine.reset();lastExpressionAt=-1L
        mutable.value=mutable.value.copy(facialExpression=null)
    }
    val cameraIntervalMs:Long get() = if(activeSpeaker?.calibrated==true) 25L else 80L
    private fun clearSpeakerFrames() { speakerFrames.forEach { it.gray.fill(0f) };speakerFrames.clear();verifiedSpeaker=null;verifiedAt=-1 }
    private fun observeSpeakerFrame(bitmap:Bitmap,faces:List<Face>,now:Long) {
        if(!activeSpeakerAttempted) { activeSpeakerAttempted=true;activeSpeaker=runCatching { TalkNetActiveSpeaker(context) }.getOrNull() }
        currentFacing=faces.singleOrNull()?.let { kotlin.math.abs(it.headEulerAngleY)<=20 && kotlin.math.abs(it.headEulerAngleX)<=15 }==true
        poseAt=now
        if(activeSpeaker?.calibrated!=true || !collecting) return
        val face=faces.singleOrNull()
        val known=mutable.value.faces.singleOrNull()?.takeIf { it.track==face?.trackingId }
        if(face==null || face.trackingId==null || known==null) { clearSpeakerFrames();temporaryTrack=null;temporaryPerson=null;return }
        if(temporaryTrack!=null && temporaryTrack!=face.trackingId) { temporaryTrack=null;temporaryPerson=null }
        val visualKey=known.personId ?: temporaryPerson?.takeIf { temporaryTrack==face.trackingId } ?: "track:${face.trackingId}"
        if(speakerFrames.lastOrNull()?.let { it.personId!=visualKey || now-it.at>70 }==true) clearSpeakerFrames()
        val box=face.boundingBox;val half=maxOf(box.width(),box.height())/2f
        val cx=box.exactCenterX();val cy=box.exactCenterY()
        val gray=FloatArray(112*112) { index ->
            val x=(cx-half*.7f+(index%112+.5f)*half*1.4f/112).toInt()
            val y=(cy-half*.3f+(index/112+.5f)*half*1.4f/112).toInt()
            if(x !in 0 until bitmap.width || y !in 0 until bitmap.height) 110f else {
                val rgb=bitmap.getPixel(x,y);.299f*android.graphics.Color.red(rgb)+.587f*android.graphics.Color.green(rgb)+.114f*android.graphics.Color.blue(rgb)
            }
        }
        speakerFrames.addLast(SpeakerFrame(now,visualKey,gray,kotlin.math.abs(face.headEulerAngleY)<=20 && kotlin.math.abs(face.headEulerAngleX)<=15))
        while(speakerFrames.size>60) speakerFrames.removeFirst().gray.fill(0f)
    }
    private val tracker=PersonTracker()
    private val learner=FaceTemplateLearner()
    private val socialGestures = SocialGestureEngine()
    private var lastDiagnosticAt = -1L
    private var lastAnalysisAt = -1L
    private var lastIdentityAt = -1L
    private var lastFaceKeys = emptyList<Int>()
    @Volatile private var closed=false
    private val cameraEpoch=java.util.concurrent.atomic.AtomicLong()
    private val turnEpoch=java.util.concurrent.atomic.AtomicLong()
    fun startCamera():Long = cameraEpoch.incrementAndGet()
    fun stopCamera(epoch:Long) { if(cameraEpoch.compareAndSet(epoch,epoch+1)) { clearFrame(); clearFaces() } }
    private var pending=mutableListOf<FloatArray>()
    private var lastCapture=0L
    private val observed=mutableSetOf<String>()
    private val temporarySpeaker = TemporarySpeaker()
    private var turnWindows = emptyList<FloatArray>()
    private var turnSpeechMs = 0L
    private var turnUncertain = false
    @Volatile private var collecting=false
    private val pcm=ArrayList<Float>(480000)
    @Volatile private var loadFailed=false
    init { jobs.launch { runCatching { store.read() }.onSuccess { mutable.value=mutable.value.copy(people=it) }
        .onFailure { loadFailed=true; mutable.value=mutable.value.copy(error="Không đọc được hồ sơ mã hóa: ${it.javaClass.simpleName}") } } }
    private fun changePeople(transform: (List<Person>)->List<Person>) { jobs.launch {
        runCatching { check(!loadFailed) { "Không thể sửa kho hồ sơ chưa đọc được" }; val next=transform(mutable.value.people); store.write(next); mutable.value=mutable.value.copy(people=next,error=null) }
            .onFailure { mutable.value=mutable.value.copy(error=it.message) }
    } }
    fun add(name:String,address:String) { if(name.isNotBlank()) changePeople { it+PeopleStore.create(name,address) } }
    fun edit(id:String,name:String,address:String) { if(name.isNotBlank()) changePeople { all -> all.map { if(it.id==id) it.copy(name=name.trim().take(60),address=address.trim().take(40)) else it } } }
    fun delete(id:String) { teaching.clearPerson(id);cancelEnrollment(); changePeople { it.filterNot { p -> p.id==id } }; clearFaces() }
    fun clearVoice(id:String) = changePeople { all -> all.map { if(it.id==id) it.copy(voices=emptyList()) else it } }
    fun enroll(id:String) { teaching.stop();jobs.launch { resetExpressions();pending.clear(); learner.reset();tracker.reset();lastCapture=0; mutable.value=mutable.value.copy(enrollment=id,captured=0,error=null) } }
    fun cancelEnrollment() { jobs.launch { pending.clear(); mutable.value=mutable.value.copy(enrollment=null,captured=0) } }
    private var frame:Bitmap?=null
    @Synchronized fun rememberFrame(bitmap:Bitmap,epoch:Long) { if(!closed && cameraEpoch.get()==epoch) { frame?.recycle(); frame=bitmap.copy(Bitmap.Config.ARGB_8888,false) } }
    @Synchronized fun clearFrame() { frame?.recycle(); frame=null }
    @Synchronized fun snapshot():ByteArray? = frame?.let { bitmap -> java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG,80,it) }.toByteArray() }
    fun cameraStatus(message:String) { jobs.launch { if(!closed) mutable.value=mutable.value.copy(status=message) } }
    fun clearFaces() { teaching.interrupt();jobs.launch { lastIdentityAt=-1;lastFaceKeys=emptyList();temporaryTrack=null;temporaryPerson=null;clearSpeakerFrames();tracker.reset();learner.reset();vision?.reset();gestures?.reset();socialGestures.reset();
        resetExpressions()
        mutable.value=mutable.value.copy(faces=emptyList(),trackedPeople=emptyList(),sceneObjects=emptyList(),gesture="",gestureReaction=null,gestureDecision=GestureDecision.IDLE,gestureCandidate=null,gestureProgress=0f) } }
    fun adaptiveLearning(enabled: Boolean) {
        prefs.edit().putBoolean("adaptiveLearning",enabled).apply()
        jobs.launch { learner.reset();mutable.value=mutable.value.copy(learningEnabled=enabled) }
    }
    fun clearLearnedFaces(id: String) { learnerReset();changePeople { all -> all.map { if(it.id==id)
        it.copy(learnedFaces=emptyList(),previousLearnedFaces=it.learnedFaces,learningRevision=it.learningRevision+1,learnedAt=0,canUndoLearning=true) else it } } }
    fun rollbackLearning(id: String) { learnerReset();changePeople { all -> all.map { if(it.id==id && it.canUndoLearning)
        it.copy(learnedFaces=it.previousLearnedFaces,previousLearnedFaces=emptyList(),learningRevision=it.learningRevision+1,learnedAt=0,canUndoLearning=false) else it } } }
    private fun learnerReset() { jobs.launch { resetExpressions();learner.reset();tracker.reset();socialGestures.reset();mutable.value=mutable.value.copy(
        trackedPeople=emptyList(),faces=emptyList(),gesture="",gestureReaction=null,learningStatus="") } }
    suspend fun analyze(bitmap:Bitmap,faces:List<Face>,epoch:Long,capturedAt:Long=SystemClock.elapsedRealtime()) = withContext(worker) {
        if(closed || cameraEpoch.get()!=epoch) return@withContext
        try {
            val now=SystemClock.elapsedRealtime()
            if(now-capturedAt in 0..250) observeSpeakerFrame(bitmap,faces,capturedAt) else clearSpeakerFrames()
            val frameGap = if(lastAnalysisAt < 0) 0 else now-lastAnalysisAt
            lastAnalysisAt = now
            val faceKeys=faces.mapIndexed { i,f -> f.trackingId ?: -i-1 }
            val deep=lastIdentityAt<0 || now-lastIdentityAt>=500 || faceKeys!=lastFaceKeys || mutable.value.enrollment!=null
            if(!deep) {
                processExpressions(bitmap,faces,mutable.value.faces,mutable.value.trackedPeople,capturedAt,epoch)
                processGestures(bitmap,faces,mutable.value.faces,mutable.value.trackedPeople,
                    SceneFrame(emptyList(),mutable.value.sceneObjects,false),now,frameGap,org.json.JSONArray(),false,epoch)
                return@withContext
            }
            lastIdentityAt=now;lastFaceKeys=faceKeys
            val model=engine ?: FaceEmbedding(context).also { engine=it }
            val scene = runCatching { (vision ?: SceneVision(context).also { vision=it }).detect(bitmap,now) }
                .getOrElse { tracker.reset();learner.reset();mutable.value=mutable.value.copy(error="Theo dõi cơ thể chưa sẵn sàng: ${it.message}")
                    SceneFrame(emptyList(),emptyList(),false) }
            if(scene.changed) { resetExpressions();temporaryTrack=null;temporaryPerson=null;clearSpeakerFrames();socialGestures.reset();gestures?.reset();learner.reset();teaching.interrupt() }
            val faceDiagnostics = org.json.JSONArray()
            val vectors=mutableMapOf<Int,FloatArray>()
            val threshold=prefs.getFloat("faceThreshold",.50f)
            val templates=mutable.value.people.map { PersonTemplate(it.id,it.faces+it.learnedFaces) }
            val evidence=faces.take(5).mapIndexed { index,face ->
                val track=face.trackingId ?: -index-1
                val embedding=model.extract(bitmap,face)
                if(embedding!=null) vectors[index]=embedding
                val match=embedding?.let { IdentityMath.match(it,templates,threshold) }
                val best=embedding?.let { IdentityMath.match(it,templates,0f,0f)?.score }
                if (com.robotai.robot.BuildConfig.DEBUG) faceDiagnostics.put(JSONObject()
                    .put("embedding",embedding!=null).put("pitch",face.headEulerAngleX).put("yaw",face.headEulerAngleY)
                    .put("bestScore",best).put("threshold",threshold).put("matched",match!=null))
                val enrolling=mutable.value.enrollment
                if(enrolling!=null && faces.size==1 && embedding!=null && now-lastCapture>=500) {
                    if(pending.isNotEmpty() && IdentityMath.cosine(pending.first(),embedding)<.45f) {
                        pending.clear(); mutable.value=mutable.value.copy(captured=0,error="Mặt đã thay đổi; thu lại mẫu")
                    } else {
                        pending.add(embedding); lastCapture=now; mutable.value=mutable.value.copy(captured=pending.size)
                        if(pending.size>=8) {
                            val next=mutable.value.people.map { if(it.id==enrolling) it.copy(faces=pending.toList(),learnedFaces=emptyList(),previousLearnedFaces=emptyList(),learningRevision=0,learnedAt=0,canUndoLearning=false) else it }
                            check(!loadFailed); store.write(next)
                            mutable.value=mutable.value.copy(people=next,enrollment=null,error=null); pending.clear()
                        }
                    }
                }
                val b=face.boundingBox
                FaceEvidence(track,TrackBox(b.left.toFloat()/bitmap.width,b.top.toFloat()/bitmap.height,
                    b.right.toFloat()/bitmap.width,b.bottom.toFloat()/bitmap.height),match,
                    embedding!=null && kotlin.math.abs(face.headEulerAngleY)<15 && kotlin.math.abs(face.headEulerAngleX)<15 &&
                        (best ?: -1f)<kotlin.math.max(.20f,threshold-.20f))
            }
            if(closed || cameraEpoch.get()!=epoch) return@withContext
            val bodyForFace=evidence.map { face -> scene.people.indices.filter { i ->
                val b=scene.people[i].box;b.contains(face.box.cx,face.box.cy,.07f) && face.box.cy<b.top+b.height*.60f
            }.singleOrNull() }
            val observations=scene.people.mapIndexed { i,body -> body.copy(face=evidence.filterIndexed { index,_ -> bodyForFace[index]==i }.singleOrNull()) }+
                evidence.filterIndexed { i,_ -> bodyForFace[i]==null }.map { face ->
                    PersonObservation(face.box,1f,SceneVision.appearance(bitmap,face.box),face=face,bodyDetected=false) }
            val tracked=tracker.update(observations,now,mutable.value.people.filter { it.faces.isNotEmpty() }.map { it.id }.toSet(),scene.changed)
            val visible=tracked.filter { it.visible }
            val seen=evidence.map { face ->
                val id=visible.singleOrNull { it.faceBox==face.box }?.personId
                FaceSeen(face.detectorTrack,id,mutable.value.people.find { it.id==id }?.name ?: "Khách / Chưa xác định",face.box.cx*2-1)
            }
            if(collecting) visible.mapNotNull { it.personId }.forEach { observed.add(it) }
            val single=visible.singleOrNull()
            val faceIndex=evidence.indexOfFirst { it.box==single?.faceBox }
            val person=mutable.value.people.find { it.id==single?.personId }
            if(mutable.value.learningEnabled && mutable.value.enrollment==null && !mutable.value.voiceBusy && person!=null && single!=null) {
                val f=faces.getOrNull(faceIndex)
                val learned=learner.observe(person.id,single.trackId,vectors[faceIndex],person.faces,person.learnedFaces,
                    evidence.getOrNull(faceIndex)?.match?.score ?: 0f,threshold,
                    single.source==TrackingSource.FACE && f!=null && f.boundingBox.width()>=120 &&
                        kotlin.math.abs(f.headEulerAngleY)<=20 && kotlin.math.abs(f.headEulerAngleX)<=15,
                    true,now)
                if(learned!=null) {
                    val next=mutable.value.people.map { if(it.id==person.id) it.copy(previousLearnedFaces=it.learnedFaces,
                        learnedFaces=(it.learnedFaces+listOf(learned)).takeLast(24),learningRevision=it.learningRevision+1,learnedAt=System.currentTimeMillis(),canUndoLearning=true) else it }
                    runCatching { check(!loadFailed);store.write(next) }.onSuccess {
                        mutable.value=mutable.value.copy(people=next,learningStatus="Đã bổ sung mẫu mặt đã xác nhận")
                    }.onFailure { mutable.value=mutable.value.copy(error="Không lưu được mẫu tự học: ${it.javaClass.simpleName}") }
                }
            } else learner.reset()
            processExpressions(bitmap,faces,seen,tracked,capturedAt,epoch)
            processGestures(bitmap,faces,seen,tracked,scene,now,frameGap,faceDiagnostics,true,epoch)
        } catch(e:Exception) { resetExpressions();tracker.reset();learner.reset();socialGestures.reset(); mutable.value=mutable.value.copy(faces=emptyList(),trackedPeople=emptyList(),gesture="",gestureReaction=null,status="Nhận diện chưa sẵn sàng",error=e.message) }
    }

    /** Only one independent, owned crop may be in flight; camera/voice work never waits for FER. */
    private fun processExpressions(bitmap:Bitmap,faces:List<Face>,seen:List<FaceSeen>,tracked:List<TrackedPerson>,capturedAt:Long,epoch:Long) {
        val now=SystemClock.elapsedRealtime()
        val face=faces.singleOrNull()
        val known=seen.singleOrNull()
        val person=tracked.filter { it.visible }.singleOrNull()
        val faceTrack=face?.trackingId
        val lessonActive=teaching.state.value.lesson.phase !in listOf(LessonPhase.IDLE,LessonPhase.COMPLETE)
        val eligible=prefs.getBoolean("facialExpressions",true) && mutable.value.enrollment==null && !mutable.value.voiceBusy && !lessonActive &&
            now-capturedAt in 0..500 && lastIdentityAt>=0 && now-lastIdentityAt in 0..1200 &&
            face!=null && faceTrack!=null && known!=null && known.track==faceTrack && known.personId!=null &&
            person!=null && person.personId==known.personId && person.source==TrackingSource.FACE &&
            mutable.value.people.any { it.id==known.personId && it.faces.isNotEmpty() }
        if(!eligible) { if(expressionOwner!=null) resetExpressions();return }
        face!!;known!!;person!!
        val box=face.boundingBox
        val currentBox=TrackBox(box.left.toFloat()/bitmap.width,box.top.toFloat()/bitmap.height,
            box.right.toFloat()/bitmap.width,box.bottom.toFloat()/bitmap.height)
        if(person.faceBox?.iou(currentBox)?.let { it>=.3f }!=true) { resetExpressions();return }
        val owner=ExpressionOwner(known.personId!!,person.trackId,faceTrack!!)
        if(expressionOwner!=owner) { resetExpressions();expressionOwner=owner }
        val observed=expressionEngine.present(owner,capturedAt,currentBox.cx*2-1)
        mutable.value=mutable.value.copy(facialExpression=observed)
        if(expressionTask?.isActive==true || now<expressionRetryAt || lastExpressionAt>=0 && now-lastExpressionAt<200) return
        lastExpressionAt=now
        val crop=FacialExpressionDetector.crop(bitmap,face)
        if(crop==null) {
            mutable.value=mutable.value.copy(facialExpression=expressionEngine.observe(owner,null,capturedAt,now))
            return
        }
        val version=expressionVersion
        expressionTask=jobs.launch {
            try {
                if(closed) return@launch
                val started=SystemClock.elapsedRealtime()
                val scores=withContext(expressionWorker) {
                    val model=expressionModel ?: FacialExpressionDetector(context).also { expressionModel=it }
                    model.infer(crop)
                }
                val finished=SystemClock.elapsedRealtime()
                if(closed || cameraEpoch.get()!=epoch || expressionVersion!=version || expressionOwner!=owner ||
                    !prefs.getBoolean("facialExpressions",true)) return@launch
                val previous=expressionEngine.state?.expression
                val result=expressionEngine.observe(owner,scores,capturedAt,finished)
                mutable.value=mutable.value.copy(facialExpression=result,expressionError=null)
                if(com.robotai.robot.BuildConfig.DEBUG && previous!=result?.expression) android.util.Log.d("RobotExpression",
                    JSONObject().put("expression",result?.expression?.name).put("score",result?.score)
                        .put("inferenceMs",finished-started).put("frameAgeMs",finished-capturedAt).toString())
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                if(!closed && cameraEpoch.get()==epoch && expressionVersion==version && expressionOwner==owner) {
                    resetExpressions();expressionRetryAt=SystemClock.elapsedRealtime()+10000
                    mutable.value=mutable.value.copy(expressionError="Quan sát biểu cảm tạm thời chưa sẵn sàng")
                    if(com.robotai.robot.BuildConfig.DEBUG) android.util.Log.d("RobotExpression","Model unavailable: ${e.javaClass.simpleName}")
                }
            }
        }.also { task -> task.invokeOnCompletion { crop.recycle() } }
    }

    private fun processGestures(bitmap:Bitmap,faces:List<Face>,seen:List<FaceSeen>,tracked:List<TrackedPerson>,
        scene:SceneFrame,now:Long,frameGap:Long,faceDiagnostics:org.json.JSONArray,deepAnalysis:Boolean,epoch:Long) {
        val visible=tracked.filter { it.visible }
            val enabled = prefs.getBoolean("socialGestures", true) && mutable.value.enrollment == null && !mutable.value.voiceBusy
            val owners = visible.map { trackedPerson ->
                val b=trackedPerson.box
                val head=trackedPerson.faceBox ?: TrackBox(b.left,b.top,b.right,b.top+b.height*.25f)
                GestureOwner(trackedPerson.personId?.takeIf { trackedPerson.gestureEligible },head.left,head.top,head.right,head.bottom,
                    if(trackedPerson.bodyDetected) b else null,if(deepAnalysis) trackedPerson.wrists else emptyList())
            }
            var hands = emptyList<GestureHand>()
            val lessonActive=teaching.state.value.lesson.phase !in listOf(LessonPhase.IDLE,LessonPhase.COMPLETE)
            val newReaction = if ((enabled || lessonActive) && owners.size == 1 && (owners[0].id != null || com.robotai.robot.BuildConfig.DEBUG)) {
                runCatching {
                    hands = gestureHands(bitmap)
                    if(lessonActive) {
                        socialGestures.reset()
                        teaching.observe(owners,hands,now).takeIf { enabled }
                    } else {
                        val personId=owners.single().id
                        val personalized=hands.map { hand ->
                            val prediction=personId?.let { teaching.predict(it,hand) }
                            if(prediction==null) hand.copy(learnedRejected=personId?.let { teaching.rejects(it,hand) }==true) else hand.copy(learnedGesture=prediction.definition.action,
                                learnedName=prediction.definition.name,learnedScore=prediction.confidence)
                        }
                        socialGestures.observe(owners, personalized, now)
                    }
                }.getOrElse {
                    socialGestures.reset()
                    mutable.value = mutable.value.copy(error = "Cử chỉ chưa sẵn sàng: ${it.message}")
                    null
                }
            } else { if(lessonActive) teaching.observe(owners,emptyList(),now)
                socialGestures.observe(owners,emptyList(),now,enabled); null }
            if(closed || cameraEpoch.get()!=epoch) return
            // An already verified greeting should finish when the hand is lowered or
            // a body/face detection flickers. This does not authorize another gesture.
            val reaction = newReaction?.copy(atMs=SystemClock.elapsedRealtime()) ?: mutable.value.gestureReaction?.takeIf {
                enabled && (!lessonActive || teaching.state.value.lesson.phase==LessonPhase.TESTING) && !scene.changed && visible.size<=1 &&
                    visible.none { p -> p.personId!=null && p.personId!=it.personId } && it.active(now)
            }
            mutable.value=mutable.value.copy(faces=seen,trackedPeople=tracked,sceneObjects=scene.objects,gesture=reaction?.kind?.label.orEmpty(),gestureReaction=reaction,
                gestureDecision=socialGestures.decision,gestureCandidate=socialGestures.candidateKind,gestureProgress=socialGestures.progress,
                status=if(visible.isEmpty()) "Camera bật · chưa thấy người" else "Camera bật · ${visible.size} người · ${faces.size} mặt")
            if (com.robotai.robot.BuildConfig.DEBUG && (newReaction != null || hands.any { it.category=="Thumb_Up" } || now-lastDiagnosticAt >= 1000)) {
                lastDiagnosticAt = now
                val diagnostic = JSONObject().put("enabled",enabled)
                    .put("registeredFaceProfiles",mutable.value.people.count { it.faces.isNotEmpty() })
                    .put("visibleFaces",faces.size).put("recognizedFaces",seen.count { it.personId!=null })
                    .put("visiblePeople",visible.size).put("trackedKnown",tracked.count { it.personId!=null })
                    .put("trackingSources",org.json.JSONArray(tracked.map { it.source.name }))
                    .put("sceneChanged",scene.changed).put("sceneObjectCount",scene.objects.size)
                    .put("learnedTemplates",mutable.value.people.sumOf { it.learnedFaces.size })
                    .put("faceWidthsPx",org.json.JSONArray(faces.map { it.boundingBox.width() }))
                    .put("faceDetails",faceDiagnostics)
                    .put("deepAnalysis",deepAnalysis).put("frameGapMs",frameGap).put("analysisMs",SystemClock.elapsedRealtime()-now)
                    .put("decision",socialGestures.decision.name)
                    .put("hands",org.json.JSONArray(hands.map { h -> JSONObject()
                        .put("category",h.category).put("score",h.score)
                        .put("valid",SocialGestureGeometry.valid(h))
                        .put("wristX",h.points.firstOrNull()?.x).put("wristY",h.points.firstOrNull()?.y)
                        .put("outsidePoints",h.points.count { it.x !in 0f..1f || it.y !in 0f..1f }) }))
                    .put("ownerBox",owners.singleOrNull()?.let { org.json.JSONArray(listOf(it.left,it.top,it.right,it.bottom)) })
                    .put("bodyBox",owners.singleOrNull()?.body?.let { org.json.JSONArray(listOf(it.left,it.top,it.right,it.bottom)) })
                    .put("poseWrists",org.json.JSONArray(owners.singleOrNull()?.wrists.orEmpty().map { org.json.JSONArray(listOf(it.x,it.y)) }))
                    .put("reactionActive",reaction!=null)
                    .put("emitted",newReaction?.kind?.name.orEmpty())
                android.util.Log.d("RobotGesture",diagnostic.toString())
            }
    }

    private fun gestureHands(bitmap: Bitmap): List<GestureHand> {
        val model = gestures ?: SocialGestureDetector(context).also { gestures = it }
        return model.detect(bitmap)
    }

    fun enableSocialGestures(enabled: Boolean) {
        prefs.edit().putBoolean("socialGestures", enabled).apply()
        jobs.launch {
            if (!enabled) socialGestures.reset()
            mutable.value = mutable.value.copy(socialGesturesEnabled=enabled,
                gesture=if(enabled) mutable.value.gesture else "",
                gestureReaction=if(enabled) mutable.value.gestureReaction else null)
        }
    }
    suspend fun beginTurn() = withContext(worker) {
        turnEpoch.incrementAndGet()
        synchronized(pcm) { pcm.clear(); collecting=true }; observed.clear();clearSpeakerFrames();lastSpeechAt=-1;lastPcmAt=-1
        mutable.value=mutable.value.copy(speaker="Đang xác định…")
    }
    fun audio(samples:ShortArray) { synchronized(pcm) { if(collecting && pcm.size+samples.size<=480000) samples.forEach { pcm.add(it/32768f) }.also { lastPcmAt=SystemClock.elapsedRealtime() } } }
    suspend fun finishTurn(): JSONObject = withContext(worker) {
        val epoch=turnEpoch.get()
        collecting=false
        val samples=synchronized(pcm) { pcm.toFloatArray().also { pcm.clear() } }
        val turn=SpeakerTurn(); observed.forEach { turn.observeFace(it) }; observed.clear()
        val profiles=mutable.value.people
        turnWindows.forEach { it.fill(0f) }; turnWindows = emptyList(); turnUncertain = false
        val matched=runCatching {
            val model=voice ?: VoiceEmbedding(context.assets).also { voice=it }
            turnWindows = model.windows(samples)
            turnSpeechMs = model.lastSpeechMs
            turnUncertain = turnWindows.size > 1 && turnWindows.any { IdentityMath.cosine(turnWindows.first(),it) < .65f }
            turnWindows.forEach { window -> turn.observeVoice(IdentityMath.match(window,
                profiles.filter { it.voices.size>=3 }.map { PersonTemplate(it.id,it.voices) },prefs.getFloat("voiceThreshold",.65f),.10f)?.personId) }
            turn.result()
        }.getOrNull()
        val now=SystemClock.elapsedRealtime()
        val matchedGuest=if(matched==null && !turnUncertain) temporarySpeaker.match(turnWindows,now,.70f) else null
        val voiceOwner=matched?.first ?: matchedGuest ?: mutable.value.faces.singleOrNull()?.let { "track:${it.track}" }
        val frames=speakerFrames.toList()
        val end=frames.indexOfLast { it.facing && it.at<=lastSpeechAt-40 }
        val clip=if(end>=24) (0 until 25).mapNotNull { index ->
            val target=frames[end].at-960+index*40
            frames.take(end+1).minByOrNull { kotlin.math.abs(it.at-target) }?.takeIf { kotlin.math.abs(it.at-target)<=20 }
        }.distinctBy { it.at } else emptyList()
        if(clip.size==25 && clip.all { it.facing && it.personId==voiceOwner } && clip.last().at-clip.first().at in 920..1000 && clip.zipWithNext().all { (a,b)->b.at-a.at in 20..70 }) {
            val audioEnd=samples.size-((lastPcmAt-clip.last().at)*16).toInt()
            if(audioEnd>=16000 && audioEnd<=samples.size) {
                val audio=samples.copyOfRange(audioEnd-16000,audioEnd)
                if(runCatching { activeSpeaker?.speaking(audio,clip.map { it.gray }) }.getOrDefault(false)==true) { verifiedSpeaker=voiceOwner;verifiedAt=SystemClock.elapsedRealtime() }
                audio.fill(0f)
            }
        }
        samples.fill(0f)
        if(closed || turnEpoch.get()!=epoch) return@withContext JSONObject().put("status","unknown")
        val person=profiles.find { it.id==matched?.first }
        // A single unambiguous tracked body can be supported by its matching enrolled voice.
        // Voice never assigns a visual identity by itself when several people are present.
        val sameBody=mutable.value.trackedPeople.filter { it.visible }.singleOrNull()?.takeIf { it.personId==person?.id && person!=null }
        mutable.value=mutable.value.copy(speaker=person?.name ?: "Chưa xác định",
            learningStatus=if(sameBody!=null) "Giọng và người đang theo dõi trùng khớp" else mutable.value.learningStatus)
        val guest = matchedGuest
        JSONObject().put("status",if(person!=null) "recognized" else if(guest!=null) "temporary" else "unknown")
            .put("person_id",person?.id ?: guest ?: "").put("name",person?.name ?: if(guest!=null) "Khách" else "").put("address",person?.address ?: "bạn")
            .put("source",if(guest!=null) "session_voice" else if(sameBody!=null) "tracked_voice" else matched?.second ?: "none")
    }
    suspend fun interruptionSpeaker(): JSONObject? = withContext(worker) {
        if(!collecting || closed) return@withContext null
        val epoch=turnEpoch.get()
        val samples=synchronized(pcm) { pcm.takeLast(19200).toFloatArray() }
        try {
            val model=voice ?: VoiceEmbedding(context.assets).also { voice=it }
            val vector=model.shortWindow(samples) ?: return@withContext null
            try {
                if(!collecting || closed || epoch!=turnEpoch.get()) return@withContext null
                val id=IdentityMath.match(vector,mutable.value.people.filter { it.voices.size>=3 }.map { PersonTemplate(it.id,it.voices) },.78f,.15f)?.personId
                val person=mutable.value.people.find { it.id==id }
                val guest=if(person==null) temporarySpeaker.match(listOf(vector),SystemClock.elapsedRealtime(),.82f) else null
                if(person==null && guest==null) return@withContext null
                JSONObject().put("status",if(person!=null) "recognized" else "temporary").put("person_id",person?.id ?: guest)
                    .put("name",person?.name ?: "Khách").put("address",person?.address ?: "bạn").put("source",if(person!=null) "voice" else "session_voice")
            } finally { vector.fill(0f) }
        } finally { samples.fill(0f) }
    }
    suspend fun acceptSpeaker(speaker: JSONObject, wake: Boolean): JSONObject = withContext(worker) {
        if (speaker.optString("status") == "temporary") temporarySpeaker.touch(SystemClock.elapsedRealtime())
        if (speaker.optString("status") == "unknown" && wake && !turnUncertain) {
            temporarySpeaker.remember(turnWindows, turnSpeechMs, SystemClock.elapsedRealtime(), .70f)?.let { id ->
                temporaryTrack=null;temporaryPerson=null
                val face=mutable.value.faces.singleOrNull()
                if(face!=null && verifiedSpeaker=="track:${face.track}" && SystemClock.elapsedRealtime()-verifiedAt in 0..500) {
                    temporaryTrack=face.track;temporaryPerson=id;verifiedSpeaker=id
                }
                speaker.put("status", "temporary").put("person_id", id).put("name", "Khách")
                    .put("address", "bạn").put("source", "session_voice")
            }
        }
        turnWindows.forEach { it.fill(0f) }; turnWindows = emptyList()
        speaker
    }
    fun listeningEvidence(speaker: JSONObject): ListeningEvidence {
        val now = SystemClock.elapsedRealtime()
        val people = mutable.value.trackedPeople.filter { it.visible && now-it.lastSeenMs in 0..500 }
        val owner = people.singleOrNull()?.takeIf { it.personId == speaker.optString("person_id") ||
            speaker.optString("status")=="temporary" && temporaryPerson==speaker.optString("person_id") &&
                mutable.value.faces.singleOrNull()?.track==temporaryTrack && temporaryTrack!=null }
        // Head pose and identity alone do not verify that this person is speaking.
        return ListeningEvidence(visible=owner!=null, cameraAgeMs=owner?.let { now-it.lastFaceMs } ?: -1,
            people=people.size, uncertain=turnUncertain, overlapping=turnUncertain,
            facing=currentFacing && now-poseAt in 0..500,
            activeSpeaker=owner!=null && verifiedSpeaker==speaker.optString("person_id") && now-verifiedAt in 0..500)
    }
    fun clearTemporarySpeaker() { jobs.launch { temporaryTrack=null;temporaryPerson=null;temporarySpeaker.clear(); turnWindows.forEach { it.fill(0f) }; turnWindows=emptyList() } }
    suspend fun saveVoice(id:String,samples:FloatArray) = withContext(worker) {
        try {
            check(!loadFailed)
            val model=voice ?: VoiceEmbedding(context.assets).also { voice=it }
            val windows=model.windows(samples,true)
            coroutineContext.ensureActive()
            require(windows.all { IdentityMath.cosine(windows.first(),it)>=.65f }) { "Mẫu giọng không nhất quán; hãy thu lại chỉ một người nói" }
            val previous=mutable.value.people.find { it.id==id }?.voices.orEmpty()
            require(previous.isEmpty() || windows.all { IdentityMath.cosine(previous.first(),it)>=.65f }) { "Giọng khác mẫu trước; hãy thu lại hoặc xóa mẫu giọng cũ" }
            val center=IdentityMath.normalize(FloatArray(windows.first().size) { i -> windows.map { it[i] }.average().toFloat() })
            val next=mutable.value.people.map { if(it.id==id) it.copy(voices=(it.voices+listOf(center)).takeLast(5)) else it }
            store.write(next); mutable.value=mutable.value.copy(people=next,error=null)
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) { mutable.value=mutable.value.copy(error=e.message) }
    }
    fun voiceBusy(value:Boolean) { if(value) teaching.stop();jobs.launch {
        if(value) resetExpressions()
        mutable.value=mutable.value.copy(voiceBusy=value,voiceSeconds=0f,voiceProcessing=false,
            error=if(value)null else mutable.value.error)
    } }
    fun voiceProgress(seconds:Float,processing:Boolean=false) { jobs.launch {
        mutable.value=mutable.value.copy(voiceSeconds=seconds.coerceIn(0f,8f),voiceProcessing=processing)
    } }
    fun error(message:String) { jobs.launch { mutable.value=mutable.value.copy(error=message) } }
    fun cancelTurn() { turnEpoch.incrementAndGet(); collecting=false; synchronized(pcm) { pcm.clear() }; jobs.launch { observed.clear(); mutable.value=mutable.value.copy(speaker="Chưa xác định") } }
    override fun close() {
        closed=true;temporarySpeaker.clear();teaching.close(); clearFrame(); cancelTurn(); jobs.launch {
            resetExpressions();expressionTask?.join()
            withContext(expressionWorker) { expressionModel?.close() };expressionWorker.close()
            clearSpeakerFrames();activeSpeaker?.close();engine?.close(); voice?.close(); gestures?.close(); vision?.close();jobs.cancel(); worker.close()
        }
    }
}
