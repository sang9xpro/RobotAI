package com.robotai.robot.identity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotai.domain.*

@Composable fun GestureTeachingPanel(teacher:GestureTeacher,person:Person,available:Boolean) {
    val state by teacher.state.collectAsState()
    val definitions=remember(state.data.definitions,person.id) { state.data.definitions.filter { it.personId==person.id } }
    var selected by remember(person.id) { mutableStateOf<String?>(null) }
    val definition=definitions.find { it.id==selected } ?: definitions.firstOrNull()
    var menu by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(SocialGesture.THUMBS_UP) }
    var actionMenu by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var examplesVisible by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { teacher.export(person.id,it) } }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { teacher.importModel(person.id,it) } }
    LaunchedEffect(person.id,state.ready) { if(state.ready) teacher.ensureBuiltins(person.id) }
    DisposableEffect(teacher,person.id) { onDispose { teacher.stop() } }
    val lesson=state.lesson
    val active=lesson.personId==person.id && lesson.phase !in listOf(LessonPhase.IDLE,LessonPhase.COMPLETE)
    val examples=remember(state.data.examples,definition?.id) { state.data.examples.filter { it.gestureId==definition?.id } }
    Card(Modifier.fillMaxWidth().testTag("teach-ken-panel")) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Dạy Ken cử chỉ · ${person.name}",style=MaterialTheme.typography.titleMedium)
        Text("Dạy tư thế một tay. Mỗi lượt: chuẩn bị 2 giây, giữ gần 1 giây, rồi hạ tay khỏi hình. Thu cả tay trái/phải và Tay bình thường. Vẫy chào và tim hai tay dùng nhận diện hiện có.")
        if(!available) Text("Đánh thức Ken và kết thúc thu mặt/giọng trước khi dạy",color=MaterialTheme.colorScheme.error)
        Box {
            OutlinedButton(onClick={menu=true},enabled=state.ready && (!active || lesson.phase==LessonPhase.TESTING),modifier=Modifier.testTag("lesson-label")) { Text(definition?.name ?: "Đang tải cử chỉ…") }
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                definitions.forEach { d -> DropdownMenuItem(text={Text(d.name)},onClick={
                    selected=d.id;menu=false;if(lesson.phase==LessonPhase.TESTING) teacher.relabelTestTarget(d.id)
                }) }
            }
        }
        definition?.let { d ->
            Text(d.action?.let(::gestureTeachingHint) ?: "Để tay tự nhiên, cầm đồ vật hoặc làm tư thế không muốn Ken phản ứng. Mỗi lượt giữ một tư thế rồi hạ tay.")
            Text("${examples.count { it.accepted }} lượt dùng để học · ${examples.map { it.sessionId }.distinct().size} phiên thu")
        }
        Row {
            Button(onClick={definition?.let { teacher.start(person.id,it.id) }},enabled=available && state.ready && !active && definition!=null,
                modifier=Modifier.testTag("lesson-start")) { Text("Thu 5 lượt") }
            TextButton(onClick={definition?.let { teacher.start(person.id,it.id,true) }},enabled=available && state.ready && !active && definition!=null,
                modifier=Modifier.testTag("lesson-test")) { Text("Thử ngay") }
            if(active) TextButton(onClick=teacher::stop) { Text("Dừng") }
        }
        if(lesson.personId==person.id && lesson.phase!=LessonPhase.IDLE) {
            GestureLessonStatus(lesson)
            if(lesson.phase==LessonPhase.TESTING) {
                Text("Nếu Ken nhận sai, chọn nhãn bạn thực sự làm ở phía trên rồi bấm Sai. Tay bình thường cũng là một nhãn.")
                Row {
                    TextButton(onClick={teacher.feedback(true)},enabled=lesson.canConfirm && lesson.predictionId!=null) { Text("Đúng") }
                    TextButton(onClick={teacher.feedback(false)},enabled=lesson.canConfirm && definition?.id!=lesson.predictionId) { Text("Sai") }
                    TextButton(onClick={teacher.feedback(false,true)},enabled=lesson.canConfirm && lesson.predictionId==null) { Text("Không nhận được") }
                }
            }
        }
        TextButton(onClick={custom=!custom},enabled=!active) { Text(if(custom) "Đóng cử chỉ mới" else "Thêm cử chỉ mới") }
        if(custom) {
            OutlinedTextField(name,{name=it.take(60)},label={Text("Tên cử chỉ")},modifier=Modifier.fillMaxWidth())
            Box {
                OutlinedButton(onClick={actionMenu=true}) { Text("Hiệu ứng: ${action.label}") }
                DropdownMenu(actionMenu,{actionMenu=false}) { SocialGesture.values().forEach { effect ->
                    DropdownMenuItem(text={Text(effect.label)},onClick={action=effect;actionMenu=false})
                } }
            }
            Button(onClick={teacher.addGesture(person.id,name,action);name="";custom=false},enabled=name.isNotBlank()) { Text("Tạo cử chỉ") }
        }
        TextButton(onClick={examplesVisible=!examplesVisible}) { Text(if(examplesVisible) "Ẩn các lượt thu" else "Xem và sửa lượt thu") }
        if(examplesVisible) {
            examples.takeLast(20).reversed().forEach { e ->
                Column {
                    Text("${if(e.frames.first().side=="Left") "Tay trái" else "Tay phải"} · ${e.frames.size} khung · ${if(e.accepted) "Đang dùng" else "Đã bỏ"}")
                    HandLandmarkPreview(listOf(e.frames.last().hand()),Modifier.fillMaxWidth().height(90.dp))
                    Row {
                        TextButton(onClick={teacher.acceptExample(e.id,!e.accepted)}) { Text(if(e.accepted) "Bỏ lượt sai" else "Dùng lại") }
                        TextButton(onClick={teacher.deleteExample(e.id)}) { Text("Xóa lượt") }
                    }
                }
            }
            if(examples.size>20) Text("Hiển thị 20 lượt mới nhất; xuất tệp để xem toàn bộ.")
        }
        HorizontalDividerCompat()
        Text("Học cá nhân cần ít nhất 3 lượt cử chỉ và 3 lượt Tay bình thường. Thu các phiên khác nhau để kiểm tra model trên dữ liệu mới.")
        Row {
            TextButton(onClick={export.launch("ken-gestures-${System.currentTimeMillis()}.json")},enabled=!active && examples.isNotEmpty()) { Text("Xuất dữ liệu") }
            TextButton(onClick={import.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!active && state.ready) { Text("Nhập model") }
        }
        Text("Đang dùng: ${state.active[person.id] ?: "Mẫu cá nhân trên máy"}")
        state.models.filter { it.model.personId==person.id }.takeLast(5).reversed().forEach { record ->
            Text("${record.model.id} · tập thử ${record.report.optInt("testExamples")} lượt · nhận đúng %.1f%%".format(record.report.optDouble("accuracy")*100))
            TextButton(onClick={teacher.activate(person.id,record.model.id)},enabled=!active && state.active[person.id]!=record.model.id) { Text("Kích hoạt ${record.model.id}") }
        }
        Row {
            TextButton(onClick={teacher.activate(person.id,null)},enabled=!active && state.active[person.id]!=null) { Text("Dùng mẫu cá nhân") }
            TextButton(onClick={teacher.rollback(person.id)},enabled=!active && state.previous[person.id]!=null) { Text("Khôi phục model trước") }
        }
        if(state.notice.isNotBlank()) Text(state.notice)
        state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        TextButton(onClick={clear=true},enabled=!active) { Text("Xóa dữ liệu đã dạy") }
    } }
    if(clear) AlertDialog(onDismissRequest={clear=false},title={Text("Xóa cử chỉ đã dạy?")},
        text={Text("Xóa mẫu và model cử chỉ của ${person.name} trên máy.")},
        confirmButton={TextButton(onClick={teacher.clearPerson(person.id,true);clear=false}) { Text("Xóa") }},
        dismissButton={TextButton(onClick={clear=false}) { Text("Hủy") }})
}

@Composable private fun HorizontalDividerCompat() { Divider() }

@Composable fun GestureLessonStatus(lesson:GestureLessonUi) {
    Column(Modifier.testTag("lesson-status"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(lesson.message,modifier=Modifier.testTag("lesson-message"))
        if(lesson.phase!=LessonPhase.TESTING) Text("${lesson.collected}/${lesson.target} lượt")
        LinearProgressIndicator(progress=lesson.progress,modifier=Modifier.fillMaxWidth())
        HandLandmarkPreview(lesson.hands,Modifier.fillMaxWidth().height(150.dp))
        Text("Điểm bàn tay; ảnh camera ở phía trên. Chỉ lưu điểm, không lưu ảnh/video.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable fun HandLandmarkPreview(hands:List<GestureHand>,modifier:Modifier=Modifier) {
    val edges=remember { listOf(0 to 1,1 to 2,2 to 3,3 to 4,0 to 5,5 to 6,6 to 7,7 to 8,5 to 9,
        9 to 10,10 to 11,11 to 12,9 to 13,13 to 14,14 to 15,15 to 16,13 to 17,0 to 17,17 to 18,18 to 19,19 to 20) }
    Canvas(modifier.background(Color(0xff08111b)).testTag("lesson-landmarks")) {
        hands.filter { it.points.size==21 }.forEach { hand ->
            // Match front-camera preview's horizontal mirror.
            fun point(i:Int)=Offset((1-hand.points[i].x)*size.width,hand.points[i].y*size.height)
            edges.forEach { (a,b) -> drawLine(Color.Cyan,point(a),point(b),3.dp.toPx()) }
            hand.points.indices.forEach { drawCircle(Color.Yellow,3.dp.toPx(),point(it)) }
        }
    }
}

fun gestureTeachingHint(action:SocialGesture):String=when(action) {
    SocialGesture.THUMBS_UP -> "👍 Duỗi ngón cái hướng lên; gập bốn ngón còn lại."
    SocialGesture.THUMBS_DOWN -> "👎 Duỗi ngón cái hướng xuống; gập bốn ngón còn lại."
    SocialGesture.VICTORY -> "✌️ Duỗi ngón trỏ và giữa; gập các ngón còn lại."
    SocialGesture.FIST_BUMP -> "✊ Nắm tay rõ, hướng bàn tay về camera."
    SocialGesture.HIGH_FIVE -> "✋ Xòe năm ngón, giữ bàn tay đứng yên."
    SocialGesture.POINT_UP -> "☝️ Duỗi ngón trỏ hướng lên, gập các ngón còn lại."
    SocialGesture.I_LOVE_YOU -> "🤟 Duỗi ngón cái, trỏ và út; gập ngón giữa và áp út."
    SocialGesture.OK_SIGN -> "👌 Chạm đầu ngón cái và trỏ; duỗi ba ngón còn lại."
    SocialGesture.ROCK_ON -> "🤘 Duỗi ngón trỏ và út; gập ngón cái, giữa và áp út."
    SocialGesture.FINGER_HEART -> "🫰 Tạo tim bằng ngón cái và trỏ; gập ba ngón còn lại."
    else -> "Tư thế một tay bạn chọn sẽ phát hiệu ứng ${action.label}."
}
