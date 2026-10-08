package com.robotai.robot.companion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotai.robot.auth.UserSession
import com.robotai.robot.identity.IdentityController
import kotlinx.coroutines.*

@Composable fun VisionPanel(session:UserSession,identity:IdentityController,front:Boolean,onFront:(Boolean)->Unit) {
    val state by identity.state.collectAsState()
    val scope=rememberCoroutineScope()
    var captured by remember { mutableStateOf<ByteArray?>(null) }
    var consent by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("Bạn nhìn thấy gì trước mặt?") }
    var reply by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var upload by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { captured=null; upload?.cancel() } }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(state.status)
        Text("Camera tự bật khi robot thức, tiếp tục chạy khi chuyển tab. Ngủ để tắt camera.")
        TextButton(onClick={onFront(!front);captured=null}) { Text(if(front) "Đang dùng camera trước" else "Đang dùng camera sau") }
        state.faces.forEach { Text("${it.label} · vị trí %.2f".format(it.x)) }
        com.robotai.robot.identity.GestureReadinessStatus(state,true)
        val known=state.trackedPeople.filter { it.visible && it.personId!=null }
        known.forEach { tracked ->
            val name=state.people.firstOrNull { it.id==tracked.personId }?.name ?: return@forEach
            Text("Đang theo dõi $name · ${if(tracked.source==com.robotai.domain.TrackingSource.FACE) "đã xác nhận mặt" else "theo chuyển động cơ thể"}")
        }
        if(state.sceneObjects.isNotEmpty()) Text("Vật thể trong cảnh: ${state.sceneObjects.map { it.label }.distinct().joinToString(", ")}")
        Text("Người nói lượt gần nhất: ${state.speaker}")
        Text("Nhận diện chạy trên máy. Chỉ gửi ảnh khi bạn chụp và nhấn gửi bên dưới.")
        Button(modifier=Modifier.testTag("camera-capture"),onClick={captured=identity.snapshot();reply=if(captured==null) "Camera chưa có hình" else "Đã chụp trong bộ nhớ"}) { Text("Chụp ảnh") }
        Row { Switch(consent,{consent=it;if(!it) upload?.cancel()});Text("Cho phép gửi ảnh này đến AI") }
        OutlinedTextField(question,{question=it.take(2000)},label={Text("Hỏi về cảnh")})
        Button(modifier=Modifier.testTag("vision-send"),enabled=consent && captured!=null && !busy && question.isNotBlank(),onClick={
            val bytes=captured ?: return@Button
            upload=scope.launch { busy=true;try { reply=CompanionApi().vision(session,bytes,question,consent) }
                catch(e:CancellationException) { throw e } catch(e:Exception) { reply=e.message ?: "Lỗi thị giác" } finally { busy=false } }
        }) { Text(if(busy) "Đang hỏi…" else "Gửi ảnh và hỏi") }
        Text(reply)
    }
}
