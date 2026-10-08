package com.robotai.robot.identity

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable fun PeoplePanel(controller:IdentityController, canEnroll:Boolean, enrollVoice:(String)->Unit, cancelVoice:()->Unit) {
    val state by controller.state.collectAsState()
    val context=LocalContext.current
    var name by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }; var deleting by remember { mutableStateOf<String?>(null) }
    var voiceId by remember { mutableStateOf<String?>(null) }
    var teachingPerson by remember { mutableStateOf<String?>(null) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) voiceId?.let(enrollVoice) }
    var faceThreshold by remember { mutableStateOf(controller.prefs.getFloat("faceThreshold",.50f)) }
    var voiceThreshold by remember { mutableStateOf(controller.prefs.getFloat("voiceThreshold",.65f)) }
    var socialGestures by remember(controller) { mutableStateOf(controller.prefs.getBoolean("socialGestures",true)) }
    Text("Người quen",style=MaterialTheme.typography.titleLarge)
    Text("Mẫu mặt và giọng được mã hóa trên điện thoại theo tài khoản. Camera tự bật khi Ken thức. Đăng ký 8 ảnh mặt và ít nhất 3 mẫu giọng để xác định người đang nói.")
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Switch(socialGestures,{socialGestures=it;controller.enableSocialGestures(it)})
        Text("Phản ứng cử chỉ người quen")
    }
    Text("Đợi tên hiện, đưa cả bàn tay vào hình. Giữ khoảng nửa giây; đập tay giữ lâu hơn. Có thể đổi cử chỉ trực tiếp; hạ tay để lặp lại cùng cử chỉ. Chỉ tương tác khi có một người đã được nhận ra.")
    var showGestures by remember { mutableStateOf(false) }
    TextButton(onClick={showGestures=!showGestures}) { Text(if(showGestures) "Ẩn hướng dẫn" else "12 cử chỉ Ken hiểu") }
    if(showGestures) {
        Text("👍 Like → Ken khen bạn\n👎 Không đồng ý → Ken ghi nhận\n✌️ Chữ V → ăn mừng\n✊ Nắm tay → chạm tay\n✋ Xòe tay giữ yên → đập tay\n👋 Xòe tay vẫy qua lại → chào\n☝️ Trỏ lên → bóng đèn ý tưởng\n🤟 Duỗi ngón cái, trỏ, út → trái tim\n👌 Chạm ngón cái và trỏ, duỗi ba ngón → đồng ý\n🤘 Duỗi ngón trỏ và út, gập ngón cái → rock\n🫰 Chụm ngón cái và trỏ, gập ba ngón → tim nhỏ\n🫶 Chụm hai tay thành tim → tim lớn")
    }
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Switch(state.learningEnabled,controller::adaptiveLearning)
        Text("Tự cải thiện nhận diện người quen")
    }
    Text("Ken giữ tên khi còn theo dõi rõ cơ thể dù bạn quay mặt. Mẫu mặt bổ sung chỉ được lưu khi xác nhận chắc chắn; mẫu đăng ký gốc được giữ để đối chiếu. Khi người bị che hoặc đi cắt nhau, Ken có thể cần nhìn lại mặt.")
    if(state.learningStatus.isNotBlank()) Text(state.learningStatus)
    state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    OutlinedTextField(name,{name=it.take(60)},label={Text("Tên")})
    OutlinedTextField(address,{address=it.take(40)},label={Text("Cách xưng hô (ví dụ: anh Sang)")})
    Row {
        Button(enabled=name.isNotBlank() && !state.voiceBusy,onClick={
            if(editing==null) controller.add(name,address) else controller.edit(editing!!,name,address)
            name=""; address=""; editing=null
        }) { Text(if(editing==null) "Thêm người" else "Lưu thay đổi") }
        if(editing!=null) TextButton(onClick={editing=null;name="";address=""}) { Text("Hủy") }
    }
    if(state.enrollment!=null) {
        Text("Thu mặt: ${state.captured}/8 · chỉ một người trong hình, nhìn thẳng rồi nghiêng nhẹ. Mặt cần đủ sáng và gần camera.")
        TextButton(onClick=controller::cancelEnrollment) { Text("Hủy thu mặt") }
    }
    if(state.voiceBusy) {
        Text("Đang thu giọng trong 8 giây. Đọc một câu liền mạch, chỉ một người nói.")
        TextButton(onClick=cancelVoice) { Text("Hủy thu giọng") }
    }
    state.people.forEach { p ->
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("${p.name} · ${p.address}",style=MaterialTheme.typography.titleMedium)
            Text("Mặt: ${p.faces.size}/8 · Giọng: ${p.voices.size}/3 mẫu tối thiểu")
            Text("Mẫu mặt bổ sung: ${p.learnedFaces.size} · Lần cập nhật: ${p.learningRevision}")
            TextButton(enabled=p.faces.isNotEmpty() && !state.voiceBusy && state.enrollment==null,
                onClick={teachingPerson=if(teachingPerson==p.id) null else p.id}) {
                Text(if(teachingPerson==p.id) "Đóng Dạy Ken" else "Dạy Ken cử chỉ")
            }
            if(teachingPerson==p.id) GestureTeachingPanel(controller.teaching,p,
                canEnroll && !state.voiceBusy && state.enrollment==null)
            Row {
                TextButton(enabled=canEnroll && !state.voiceBusy,onClick={controller.enroll(p.id)}) { Text(if(p.faces.isEmpty()) "Đăng ký mặt" else "Thu lại mặt") }
                TextButton(enabled=canEnroll && !state.voiceBusy && state.enrollment==null,onClick={
                    voiceId=p.id
                    if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) enrollVoice(p.id)
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Thêm mẫu giọng") }
            }
            Row {
                TextButton(enabled=!state.voiceBusy,onClick={editing=p.id;name=p.name;address=p.address}) { Text("Sửa") }
                TextButton(enabled=!state.voiceBusy,onClick={controller.clearVoice(p.id)}) { Text("Xóa mẫu giọng") }
                TextButton(enabled=!state.voiceBusy,onClick={deleting=p.id}) { Text("Xóa người") }
            }
            if(p.learningRevision>0) Row {
                TextButton(enabled=p.canUndoLearning && !state.voiceBusy && state.enrollment==null,onClick={controller.rollbackLearning(p.id)}) { Text("Hoàn tác lần học gần nhất") }
                TextButton(enabled=p.learnedFaces.isNotEmpty() && !state.voiceBusy && state.enrollment==null,onClick={controller.clearLearnedFaces(p.id)}) { Text("Xóa mẫu tự học") }
            }
        } }
    }
    Text("Ngưỡng mặt: %.2f".format(faceThreshold))
    Slider(faceThreshold,{faceThreshold=it},onValueChangeFinished={controller.prefs.edit().putFloat("faceThreshold",faceThreshold).apply()},valueRange=.4f.. .8f)
    Text("Ngưỡng giọng: %.2f".format(voiceThreshold))
    Slider(voiceThreshold,{voiceThreshold=it},onValueChangeFinished={controller.prefs.edit().putFloat("voiceThreshold",voiceThreshold).apply()},valueRange=.5f.. .85f)
    Text("Ngưỡng là điểm tương đồng, không phải xác suất. Tăng ngưỡng giảm nhận nhầm nhưng tăng số lượt chưa xác định. Người lạ luôn được tiếp chuyện bằng cách gọi trung tính.")
    if(deleting!=null) AlertDialog(onDismissRequest={deleting=null},title={Text("Xóa người quen?")},text={Text("Xóa hồ sơ và toàn bộ mẫu mặt, giọng trên máy. Lịch sử hội thoại cũ không bị xóa.")},
        confirmButton={TextButton(onClick={controller.delete(deleting!!);deleting=null}) { Text("Xóa") }},dismissButton={TextButton(onClick={deleting=null}) {Text("Hủy")}})
}
