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
    val voiceBusy: Boolean = false, val speaker: String = "Chưa xác định", val gesture: String = "", val error: String? = null,
    val gestureReaction: GestureReaction? = null, val socialGesturesEnabled: Boolean = true,
    val trackedPeople: List<TrackedPerson> = emptyList(), val sceneObjects: List<SceneObject> = emptyList(),
    val learningEnabled: Boolean = true, val learningStatus: String = "",
    val gestureDecision: GestureDecision = GestureDecision.IDLE,
    val gestureCandidate: SocialGesture? = null, val gestureProgress: Float = 0f)

class IdentityController(private val context: Context, scopeKey: String) : AutoCloseable {
    private val store=PeopleStore(context,scopeKey)
    val teaching=GestureTeacher(context,scopeKey)
    val prefs=context.getSharedPreferences("identity-$scopeKey",0)
    private val worker=Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val jobs=CoroutineScope(SupervisorJob()+worker)
    private val mutable=MutableStateFlow(IdentityUi(socialGesturesEnabled=prefs.getBoolean("socialGestures",true),
        learningEnabled=prefs.getBoolean("adaptiveLearning",true)))
    val state=mutable.asStateFlow()
    private var engine: FaceEmbedding?=null
    private var voice: VoiceEmbedding?=null
    private var gestures: SocialGestureDetector?=null
    private var vision: SceneVision?=null
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
    fun enroll(id:String) { teaching.stop();jobs.launch { pending.clear(); learner.reset();tracker.reset();lastCapture=0; mutable.value=mutable.value.copy(enrollment=id,captured=0,error=null) } }
    fun cancelEnrollment() { jobs.launch { pending.clear(); mutable.value=mutable.value.copy(enrollment=null,captured=0) } }
    private var frame:Bitmap?=null
    @Synchronized fun rememberFrame(bitmap:Bitmap,epoch:Long) { if(!closed && cameraEpoch.get()==epoch) { frame?.recycle(); frame=bitmap.copy(Bitmap.Config.ARGB_8888,false) } }
    @Synchronized fun clearFrame() { frame?.recycle(); frame=null }
    @Synchronized fun snapshot():ByteArray? = frame?.let { bitmap -> java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG,80,it) }.toByteArray() }
    fun cameraStatus(message:String) { jobs.launch { if(!closed) mutable.value=mutable.value.copy(status=message) } }
    fun clearFaces() { teaching.interrupt();jobs.launch { lastIdentityAt=-1;lastFaceKeys=emptyList();tracker.reset();learner.reset();vision?.reset();gestures?.reset();socialGestures.reset();
        mutable.value=mutable.value.copy(faces=emptyList(),trackedPeople=emptyList(),sceneObjects=emptyList(),gesture="",gestureReaction=null,gestureDecision=GestureDecision.IDLE,gestureCandidate=null,gestureProgress=0f) } }
    fun adaptiveLearning(enabled: Boolean) {
        prefs.edit().putBoolean("adaptiveLearning",enabled).apply()
        jobs.launch { learner.reset();mutable.value=mutable.value.copy(learningEnabled=enabled) }
    }
    fun clearLearnedFaces(id: String) { learnerReset();changePeople { all -> all.map { if(it.id==id)
        it.copy(learnedFaces=emptyList(),previousLearnedFaces=it.learnedFaces,learningRevision=it.learningRevision+1,learnedAt=0,canUndoLearning=true) else it } } }
    fun rollbackLearning(id: String) { learnerReset();changePeople { all -> all.map { if(it.id==id && it.canUndoLearning)
        it.copy(learnedFaces=it.previousLearnedFaces,previousLearnedFaces=emptyList(),learningRevision=it.learningRevision+1,learnedAt=0,canUndoLearning=false) else it } } }
    private fun learnerReset() { jobs.launch { learner.reset();tracker.reset();socialGestures.reset();mutable.value=mutable.value.copy(
        trackedPeople=emptyList(),faces=emptyList(),gesture="",gestureReaction=null,learningStatus="") } }
    suspend fun analyze(bitmap:Bitmap,faces:List<Face>,epoch:Long) = withContext(worker) {
        if(closed || cameraEpoch.get()!=epoch) return@withContext
        try {
            val now=SystemClock.elapsedRealtime()
            val frameGap = if(lastAnalysisAt < 0) 0 else now-lastAnalysisAt
            lastAnalysisAt = now
            val faceKeys=faces.mapIndexed { i,f -> f.trackingId ?: -i-1 }
            val deep=lastIdentityAt<0 || now-lastIdentityAt>=500 || faceKeys!=lastFaceKeys || mutable.value.enrollment!=null
            if(!deep) {
                processGestures(bitmap,faces,mutable.value.faces,mutable.value.trackedPeople,
                    SceneFrame(emptyList(),mutable.value.sceneObjects,false),now,frameGap,org.json.JSONArray(),false,epoch)
                return@withContext
            }
            lastIdentityAt=now;lastFaceKeys=faceKeys
            val model=engine ?: FaceEmbedding(context).also { engine=it }
            val scene = runCatching { (vision ?: SceneVision(context).also { vision=it }).detect(bitmap,now) }
                .getOrElse { tracker.reset();learner.reset();mutable.value=mutable.value.copy(error="Theo dõi cơ thể chưa sẵn sàng: ${it.message}")
                    SceneFrame(emptyList(),emptyList(),false) }
            if(scene.changed) { socialGestures.reset();gestures?.reset();learner.reset();teaching.interrupt() }
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
            processGestures(bitmap,faces,seen,tracked,scene,now,frameGap,faceDiagnostics,true,epoch)
        } catch(e:Exception) { tracker.reset();learner.reset();socialGestures.reset(); mutable.value=mutable.value.copy(faces=emptyList(),trackedPeople=emptyList(),gesture="",gestureReaction=null,status="Nhận diện chưa sẵn sàng",error=e.message) }
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
        synchronized(pcm) { pcm.clear(); collecting=true }; observed.clear()
        mutable.value=mutable.value.copy(speaker="Đang xác định…")
    }
    fun audio(samples:ShortArray) { synchronized(pcm) { if(collecting && pcm.size+samples.size<=480000) samples.forEach { pcm.add(it/32768f) } } }
    suspend fun finishTurn(): JSONObject = withContext(worker) {
        val epoch=turnEpoch.get()
        collecting=false
        val samples=synchronized(pcm) { pcm.toFloatArray().also { pcm.clear() } }
        val turn=SpeakerTurn(); observed.forEach { turn.observeFace(it) }; observed.clear()
        val profiles=mutable.value.people
        val matched=runCatching {
            val model=voice ?: VoiceEmbedding(context.assets).also { voice=it }
            model.windows(samples).forEach { window -> turn.observeVoice(IdentityMath.match(window,
                profiles.filter { it.voices.size>=3 }.map { PersonTemplate(it.id,it.voices) },prefs.getFloat("voiceThreshold",.65f),.10f)?.personId) }
            turn.result()
        }.getOrNull()
        samples.fill(0f)
        if(closed || turnEpoch.get()!=epoch) return@withContext JSONObject().put("status","unknown")
        val person=profiles.find { it.id==matched?.first }
        // A single unambiguous tracked body can be supported by its matching enrolled voice.
        // Voice never assigns a visual identity by itself when several people are present.
        val sameBody=mutable.value.trackedPeople.filter { it.visible }.singleOrNull()?.takeIf { it.personId==person?.id && person!=null }
        mutable.value=mutable.value.copy(speaker=person?.name ?: "Chưa xác định",
            learningStatus=if(sameBody!=null) "Giọng và người đang theo dõi trùng khớp" else mutable.value.learningStatus)
        JSONObject().put("status",if(person==null) "unknown" else "recognized")
            .put("person_id",person?.id ?: "").put("name",person?.name ?: "").put("address",person?.address ?: "")
            .put("source",if(sameBody!=null) "tracked_voice" else matched?.second ?: "none")
    }
    suspend fun saveVoice(id:String,samples:FloatArray) = withContext(worker) {
        try {
            check(!loadFailed)
            val model=voice ?: VoiceEmbedding(context.assets).also { voice=it }
            val windows=model.windows(samples,true)
            require(windows.all { IdentityMath.cosine(windows.first(),it)>=.65f }) { "Mẫu giọng không nhất quán; hãy thu lại chỉ một người nói" }
            val previous=mutable.value.people.find { it.id==id }?.voices.orEmpty()
            require(previous.isEmpty() || windows.all { IdentityMath.cosine(previous.first(),it)>=.65f }) { "Giọng khác mẫu trước; hãy thu lại hoặc xóa mẫu giọng cũ" }
            val center=IdentityMath.normalize(FloatArray(windows.first().size) { i -> windows.map { it[i] }.average().toFloat() })
            val next=mutable.value.people.map { if(it.id==id) it.copy(voices=(it.voices+listOf(center)).takeLast(5)) else it }
            store.write(next); mutable.value=mutable.value.copy(people=next,error=null)
        } catch(e:Exception) { mutable.value=mutable.value.copy(error=e.message) }
    }
    fun voiceBusy(value:Boolean) { if(value) teaching.stop();jobs.launch { mutable.value=mutable.value.copy(voiceBusy=value) } }
    fun error(message:String) { jobs.launch { mutable.value=mutable.value.copy(error=message) } }
    fun cancelTurn() { turnEpoch.incrementAndGet(); collecting=false; synchronized(pcm) { pcm.clear() }; jobs.launch { observed.clear(); mutable.value=mutable.value.copy(speaker="Chưa xác định") } }
    override fun close() {
        closed=true;teaching.close(); clearFrame(); cancelTurn(); jobs.launch { engine?.close(); voice?.close(); gestures?.close(); vision?.close();jobs.cancel(); worker.close() }
    }
}
