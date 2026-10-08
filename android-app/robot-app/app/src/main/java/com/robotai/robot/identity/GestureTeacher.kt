package com.robotai.robot.identity

import android.content.Context
import android.net.Uri
import com.robotai.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

data class GestureTeachingState(val ready:Boolean=false, val data:GestureLessonData=GestureLessonData(),
    val lesson:GestureLessonUi=GestureLessonUi(), val models:List<GestureModelRecord> = emptyList(),
    val active:Map<String,String> = emptyMap(), val previous:Map<String,String> = emptyMap(),
    val notice:String="", val error:String?=null)

/** All mutations and camera observations serialize under one monitor, separately from face storage. */
class GestureTeacher(private val context:Context,scope:String):AutoCloseable {
    private val store=GestureLessonStore(context,scope)
    private val worker=java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val jobs=CoroutineScope(SupervisorJob()+worker)
    private var lessonEpoch=0L
    private val mutable=MutableStateFlow(GestureTeachingState())
    val state=mutable.asStateFlow()
    private var book=GestureLessonBook()
    private var recorder:GestureLessonRecorder?=null
    private var sessionId=""
    private val testTrigger=GestureTrigger()
    private var testCandidate:String?=null
    private var feedbackFrames:List<GestureFrame> = emptyList()
    private val testFrames=ArrayDeque<GestureFrame>()
    private var frozenPrediction:PersonalGesturePrediction?=null
    private var cachedPerson:String?=null
    private var cachedRevision=-1
    private var cachedMatcher:PersonalGestureMatcher?=null
    @Volatile private var closed=false
    init { jobs.launch { synchronized(this@GestureTeacher) {
        runCatching { store.read() }.onSuccess { book=it;publish() }
            .onFailure { mutable.value=mutable.value.copy(error="Không đọc được kho cử chỉ; chưa cho phép ghi đè: ${it.javaClass.simpleName}") }
    } } }
    private fun publish(notice:String=mutable.value.notice) {
        mutable.value=mutable.value.copy(ready=true,data=book.data,models=book.models,active=book.active,previous=book.previous,notice=notice,error=null)
    }
    private fun save(next:GestureLessonBook,notice:String="") { store.write(next);book=next;publish(notice) }
    private fun command(block:()->Unit) { jobs.launch { synchronized(this@GestureTeacher) {
        if(closed) return@synchronized
        runCatching { check(mutable.value.ready) { "Kho cử chỉ chưa sẵn sàng" };block() }
            .onFailure { mutable.value=mutable.value.copy(error=it.message ?: "Không thực hiện được thao tác") }
    } } }
    fun ensureBuiltins(personId:String) = command {
        val actions=SocialGesture.values().filter { it!=SocialGesture.WAVE && it!=SocialGesture.TWO_HAND_HEART }
        val additions=actions.filter { action -> book.data.definitions.none { it.personId==personId && it.id=="$personId-${action.name}" } }
            .map { TaughtGesture("$personId-${it.name}",personId,it.label,it) }+
            if(book.data.definitions.none { it.personId==personId && it.action==null }) listOf(TaughtGesture("$personId-none",personId,"Tay bình thường",null)) else emptyList()
        if(additions.isNotEmpty()) save(book.copy(data=book.data.copy(revision=book.data.revision+1,definitions=book.data.definitions+additions)))
    }
    fun addGesture(personId:String,name:String,action:SocialGesture) = command {
        require(name.trim().length in 1..60) { "Tên cử chỉ cần từ 1 đến 60 ký tự" }
        require(book.data.definitions.count { it.personId==personId }<30) { "Tối đa 30 cử chỉ mỗi người" }
        val d=TaughtGesture(UUID.randomUUID().toString(),personId,name.trim(),action)
        save(book.copy(data=book.data.copy(revision=book.data.revision+1,definitions=book.data.definitions+d)),"Đã thêm ${d.name}")
    }
    fun start(personId:String,gestureId:String,testing:Boolean=false) {
        val epoch=synchronized(this) { ++lessonEpoch }
        command {
        if(epoch!=lessonEpoch) return@command
        val d=book.data.definitions.single { it.id==gestureId && it.personId==personId }
        recorder=if(testing) null else GestureLessonRecorder(personId)
        sessionId=UUID.randomUUID().toString();testTrigger.reset();testCandidate=null;testFrames.clear();feedbackFrames=emptyList();frozenPrediction=null
        mutable.value=mutable.value.copy(lesson=GestureLessonUi(personId,d.id,
            if(testing) LessonPhase.TESTING else LessonPhase.WAITING,
            message=if(testing) "Làm cử chỉ để thử; Ken cần ít nhất 3 lượt đúng và 3 lượt tay bình thường" else "Nhìn camera để Ken nhận ra bạn, rồi đưa một bàn tay vào hình"),error=null)
        }
    }
    @Synchronized fun stop() {
        lessonEpoch++
        recorder=null;testTrigger.reset();testFrames.clear();feedbackFrames=emptyList();frozenPrediction=null
        mutable.value=mutable.value.copy(lesson=GestureLessonUi())
    }
    @Synchronized fun interrupt() {
        recorder?.interrupt();testTrigger.reset();testFrames.clear();feedbackFrames=emptyList();frozenPrediction=null
        mutable.value=mutable.value.copy(lesson=mutable.value.lesson.copy(hands=emptyList(),predictionId=null,
            predictionName=null,canConfirm=false,progress=0f,message="Camera tạm dừng; đưa đúng người và bàn tay vào hình để tiếp tục"))
    }
    @Synchronized fun observe(owners:List<GestureOwner>,hands:List<GestureHand>,now:Long):GestureReaction? {
        if(closed || !mutable.value.ready) return null
        var lesson=mutable.value.lesson
        if(lesson.phase in listOf(LessonPhase.IDLE,LessonPhase.COMPLETE)) return null
        val person=lesson.personId ?: return null
        val owner=owners.singleOrNull()?.takeIf { it.id==person }
        val safeHands=if(owner!=null) hands else emptyList()
        if(lesson.phase==LessonPhase.TESTING) {
            val hand=safeHands.singleOrNull()?.takeIf { GestureOwnership.owns(owner!!,it) && GestureFeatures.extract(it)!=null }
            if(hand==null) {
                testTrigger.observe(person,null,now);testFrames.clear()
                if(owner==null) { testTrigger.reset();feedbackFrames=emptyList() }
                mutable.value=mutable.value.copy(lesson=lesson.copy(hands=safeHands,
                    canConfirm=feedbackFrames.size>=3 && owner!=null,
                    message=if(owner==null) "Chờ đúng người đã chọn; chỉ một người trong hình" else "Đưa một bàn tay vào hình để thử"))
                return null
            }
            val predicted=predict(person,hand)
            if(testFrames.lastOrNull()?.let { now-it.elapsedMs>850 }==true || testCandidate!=predicted?.definition?.id) {
                testTrigger.reset();testFrames.clear();testCandidate=predicted?.definition?.id
            }
            testFrames.addLast(GestureFrame(hand.points,hand.side,hand.frameAspect,now))
            while(testFrames.size>8) testFrames.removeFirst()
            val stable=testFrames.size>=3 && now-testFrames.first().elapsedMs>=350 &&
                GestureFeatures.distance(GestureFeatures.extract(testFrames.first().hand())!!,GestureFeatures.extract(hand)!!) <= .15f
            if(stable && feedbackFrames.isEmpty()) { feedbackFrames=relative(testFrames.toList());frozenPrediction=predicted }
            val event=testTrigger.observe(person,predicted?.definition?.action,now)
            val shown=if(feedbackFrames.isNotEmpty()) frozenPrediction else predicted
            lesson=lesson.copy(hands=if(feedbackFrames.isNotEmpty()) listOf(feedbackFrames.last().hand()) else safeHands,progress=testTrigger.progress,
                predictionId=shown?.definition?.id,predictionName=shown?.definition?.name,canConfirm=feedbackFrames.size>=3,
                message=shown?.let { "Ken nhận: ${it.definition.name} · chọn Đúng hoặc Sai cho mẫu thử này" }
                    ?: "Chưa nhận được · giữ tư thế rồi chọn Không nhận được")
            mutable.value=mutable.value.copy(lesson=lesson)
            return event?.copy(learnedName=predicted?.definition?.name)
        }
        val r=recorder ?: return null
        val sample=r.observe(owners,hands,now)
        if(sample!=null) {
            val example=GestureExample(UUID.randomUUID().toString(),lesson.gestureId!!,sessionId,sample,System.currentTimeMillis())
            runCatching { save(book.copy(data=book.data.copy(revision=book.data.revision+1,examples=book.data.examples+example))) }
                .onFailure { stop();mutable.value=mutable.value.copy(error="Không lưu được lượt thu: ${it.message}");return null }
            lesson=lesson.copy(collected=lesson.collected+1)
        }
        val complete=lesson.collected>=lesson.target
        mutable.value=mutable.value.copy(lesson=lesson.copy(phase=if(complete) LessonPhase.COMPLETE else r.phase,
            progress=r.progress,hands=safeHands,message=if(complete) "Đã thu ${lesson.collected} lượt · thu tay bình thường và chọn Thử ngay" else r.message))
        return null
    }
    private fun relative(frames:List<GestureFrame>)=frames.map { it.copy(elapsedMs=it.elapsedMs-frames.first().elapsedMs) }
    @Synchronized fun predict(personId:String,hand:GestureHand):PersonalGesturePrediction? {
        if(!mutable.value.ready) return null
        val active=book.active[personId]?.let { id -> book.models.find { it.model.id==id && it.model.personId==personId } }?.model
        if(active!=null) {
            val features=GestureFeatures.extract(hand) ?: return null
            val scores=active.scores(features);val indices=scores.indices.sortedByDescending { scores[it] };val winner=indices.first()
            if(scores[winner]<active.threshold || scores[winner]-scores[indices[1]]<active.margin) return null
            val definition=book.data.definitions.find { it.id==active.labels[winner] && it.personId==personId && it.action!=null } ?: return null
            val closest=active.anchors[winner].minOfOrNull { GestureFeatures.distance(features,it) } ?: return null
            if(closest>active.radius) return null
            return PersonalGesturePrediction(definition,scores[winner],closest)
        }
        if(cachedPerson!=personId || cachedRevision!=book.data.revision) {
            cachedMatcher=PersonalGestureMatcher(book.data,personId);cachedPerson=personId;cachedRevision=book.data.revision
        }
        return cachedMatcher?.predict(hand)
    }
    @Synchronized fun rejects(personId:String,hand:GestureHand):Boolean {
        if(!mutable.value.ready) return false
        val model=book.active[personId]?.let { id -> book.models.find { it.model.id==id && it.model.personId==personId } }?.model
        if(model!=null) {
            val features=GestureFeatures.extract(hand) ?: return false
            val scores=model.scores(features);val sorted=scores.indices.sortedByDescending { scores[it] };val winner=sorted.first()
            val negative=book.data.definitions.any { it.id==model.labels[winner] && it.personId==personId && it.action==null }
            return negative && scores[winner]>=model.threshold && scores[winner]-scores[sorted[1]]>=model.margin &&
                model.anchors[winner].minOf { GestureFeatures.distance(features,it) }<=.15f
        }
        if(cachedPerson!=personId || cachedRevision!=book.data.revision) {
            cachedMatcher=PersonalGestureMatcher(book.data,personId);cachedPerson=personId;cachedRevision=book.data.revision
        }
        return cachedMatcher?.rejects(hand)==true
    }
    fun feedback(correct:Boolean,missed:Boolean=false) = command {
        val lesson=mutable.value.lesson
        require(lesson.phase==LessonPhase.TESTING && lesson.canConfirm && feedbackFrames.size>=3) { "Giữ tư thế rõ ít nhất ba khung trước khi xác nhận" }
        val target=if(correct) lesson.predictionId else lesson.gestureId
        require(target!=null && book.data.definitions.any { it.id==target && it.personId==lesson.personId }) { "Chọn nhãn đúng trước khi xác nhận" }
        if(!correct && !missed) require(target!=lesson.predictionId) { "Chọn cử chỉ bạn thực sự làm, rồi đánh dấu Sai" }
        val example=GestureExample(UUID.randomUUID().toString(),target,sessionId,feedbackFrames,System.currentTimeMillis(),
            feedback=if(correct) "correct" else if(missed) "missed" else "corrected")
        save(book.copy(data=book.data.copy(revision=book.data.revision+1,examples=book.data.examples+example)),"Đã lưu phản hồi đã xác nhận")
        feedbackFrames=emptyList();testFrames.clear();frozenPrediction=null
        mutable.value=mutable.value.copy(lesson=lesson.copy(canConfirm=false))
    }
    fun relabelTestTarget(id:String) = command {
        require(book.data.definitions.any { it.id==id && it.personId==mutable.value.lesson.personId })
        mutable.value=mutable.value.copy(lesson=mutable.value.lesson.copy(gestureId=id))
    }
    fun acceptExample(id:String,accepted:Boolean) = command {
        val example=book.data.examples.single { it.id==id };val person=book.data.definitions.single { it.id==example.gestureId }.personId
        save(book.copy(data=book.data.copy(revision=book.data.revision+1,examples=book.data.examples.map { if(it.id==id) it.copy(accepted=accepted) else it }),
            active=book.active-person),if(accepted) "Đã dùng lại lượt thu" else "Đã bỏ lượt thu sai")
    }
    fun deleteExample(id:String) = command {
        val definition=book.data.definitions.single { it.id==book.data.examples.single { e -> e.id==id }.gestureId }
        save(book.copy(data=book.data.copy(revision=book.data.revision+1,examples=book.data.examples.filterNot { it.id==id }),
            active=book.active-definition.personId),"Đã xóa lượt thu")
    }
    fun clearPerson(personId:String,keepDefinitions:Boolean=false) = command {
        stop();val ids=book.data.definitions.filter { it.personId==personId }.map { it.id }.toSet()
        save(book.copy(data=book.data.copy(revision=book.data.revision+1,examples=book.data.examples.filterNot { it.gestureId in ids },
            definitions=if(keepDefinitions) book.data.definitions else book.data.definitions.filterNot { it.personId==personId }),models=book.models.filterNot { it.model.personId==personId },
            active=book.active-personId,previous=book.previous-personId),"Đã xóa dữ liệu cử chỉ của người này")
    }
    fun export(personId:String,uri:Uri) = command {
        val definitions=book.data.definitions.filter { it.personId==personId };val ids=definitions.map { it.id }.toSet()
        val data=book.data.copy(definitions=definitions,examples=book.data.examples.filter { it.gestureId in ids })
        val bytes=GestureLessonJson.encodeData(data).toString(2).toByteArray(Charsets.UTF_8)
        context.contentResolver.openOutputStream(uri,"wt")?.use { it.write(bytes) } ?: error("Không mở được tệp xuất")
        publish("Đã xuất dữ liệu điểm bàn tay; tệp không chứa ảnh hoặc mẫu mặt/giọng")
    }
    fun importModel(personId:String,uri:Uri) = command {
        val bytes=context.contentResolver.openInputStream(uri)?.use { it.readNBytes(2_000_001) } ?: error("Không mở được model")
        require(bytes.size<=2_000_000) { "Model vượt giới hạn 2 MB" }
        val root=JSONObject(String(bytes,Charsets.UTF_8));require(root.getInt("version")==1)
        val model=GestureLessonJson.decodeModel(root.getJSONObject("model"));val report=root.getJSONObject("report")
        require(model.personId==personId) { "Model thuộc người khác" }
        val defs=book.data.definitions.filter { it.personId==personId }.associateBy { it.id }
        require(model.labels.all { it in defs } && model.labels.any { defs[it]?.action==null }) { "Nhãn model không khớp hồ sơ hoặc thiếu tay bình thường" }
        require(report.getBoolean("eligible") && report.getDouble("accuracy")>=.85 && report.getDouble("falsePositiveRate")<=.05 && report.getInt("testExamples")>=10) { "Model chưa đạt kiểm tra trên tập thử độc lập" }
        val support=report.getJSONObject("support");val recall=report.getJSONObject("recall")
        require(model.labels.all { label -> val key=if(defs[label]?.action==null) "none" else label
            support.getInt(key)>=5 && recall.getDouble(key)>=.8 }) { "Thiếu lượt kiểm tra hoặc nhận đúng quá thấp cho một cử chỉ" }
        val splits=report.getJSONObject("splitSessions")
        val groups=listOf("train","validation","test").map { name -> splits.getJSONArray(name).let { a ->
            (0 until a.length()).map { a.getString(it) }.toSet() } }
        require(groups.all { it.isNotEmpty() } && groups[0].intersect(groups[1]).isEmpty() &&
            groups[0].intersect(groups[2]).isEmpty() && groups[1].intersect(groups[2]).isEmpty()) { "Các phiên học và thử bị trộn; cần train lại" }
        require(book.models.none { it.model.id==model.id }) { "Phiên bản đã được nhập" }
        save(book.copy(models=(book.models+GestureModelRecord(model,report,System.currentTimeMillis())).takeLast(30)),"Đã nhập model; chọn Kích hoạt sau khi xem kết quả")
    }
    fun activate(personId:String,modelId:String?) = command {
        if(modelId!=null) {
            val record=book.models.single { it.model.id==modelId && it.model.personId==personId }
            require(record.model.labels.all { id -> book.data.definitions.any { it.id==id && it.personId==personId } }) { "Mẫu cử chỉ đã thay đổi; cần train lại" }
        }
        val old=book.active[personId]
        save(book.copy(active=if(modelId==null) book.active-personId else book.active+(personId to modelId),
            previous=if(old==null) book.previous-personId else book.previous+(personId to old)),if(modelId==null) "Đang dùng mẫu cá nhân" else "Đã kích hoạt model $modelId")
        testTrigger.reset()
    }
    fun rollback(personId:String) = activate(personId,mutable.value.previous[personId])
    override fun close() { synchronized(this) { closed=true;stop() };jobs.cancel();worker.close() }
}
