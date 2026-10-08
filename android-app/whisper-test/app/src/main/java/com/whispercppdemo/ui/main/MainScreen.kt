package com.whispercppdemo.ui.main

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whispercppdemo.tts.TtsViewModel
import com.whispercppdemo.tts.TtsPanel

@Composable
fun MainScreen(viewModel: MainScreenViewModel) {
    val state by viewModel.state.collectAsState()
    val ttsViewModel: TtsViewModel = viewModel()
    val tts by ttsViewModel.state.collectAsState()
    val sttBlocked = tts.busy || tts.playing
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.stopOnBackground()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val importModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::importLarge) }
    var requestEnrollment by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionDenied = !it
        if (it) viewModel.toggleRecord(enroll = requestEnrollment)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("RobotAI · Voice Test", style = MaterialTheme.typography.headlineSmall)
        Text(viewModel.device)
        TtsPanel(ttsViewModel, state.busy || state.recording)
        Divider()
        Text("STT offline · tiếng Việt · model multilingual")
        OutlinedButton(onClick = { importModel.launch(arrayOf("*/*")) }, enabled = !sttBlocked && !state.busy && !state.recording) { Text("Nhập file PhoWhisper-large GGML") }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = state.useGpu, onCheckedChange = viewModel::setGpu, enabled = !sttBlocked && !state.busy && !state.recording)
            Text("Dùng GPU Vulkan")
        }
        Text(state.gpuInfo)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = state.live, onCheckedChange = viewModel::setLive, enabled = !state.busy && !state.recording)
            Text("Nhận dạng trong lúc nói")
        }
        Text("Chia theo khoảng nghỉ bằng mức âm thử nghiệm. Có thể bỏ sót lời nói nhỏ hoặc chia giữa câu; tắt để so sánh cả WAV.")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.models.forEach { model ->
                OutlinedButton(onClick = { viewModel.loadModel(model) }, enabled = !sttBlocked && !state.busy && !state.recording) {
                    Text(model.removePrefix("ggml-").removeSuffix(".bin"))
                }
            }
        }
        Text(state.status)
        if (state.pending > 0) Text("Đang xử lý / chờ: ${state.pending} đoạn")
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Đăng ký giọng: ${state.enrollmentCount}/3 mẫu", style = MaterialTheme.typography.titleMedium)
        Text("Nói một mình trong phòng yên tĩnh, mỗi mẫu 5–10 giây với một câu khác nhau. Mẫu giọng chỉ giữ trong phiên app.")
        OutlinedButton(enabled = !sttBlocked && !state.busy && !state.recording && state.enrollmentCount < 3, onClick = {
            requestEnrollment = true
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }) { Text("Ghi mẫu đăng ký ${minOf(state.enrollmentCount + 1, 3)}/3") }
        OutlinedButton(enabled = !state.busy && !state.recording && state.enrollmentCount > 0,
            onClick = { viewModel.clearVoice() }) { Text("Xóa mẫu giọng") }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = state.gateEnabled, enabled = !state.busy && !state.recording,
                onCheckedChange = viewModel::setGateEnabled)
            Text("Lọc theo giọng đã đăng ký")
        }
        Text("Ngưỡng tương đồng: %.2f — tăng để lọc chặt hơn".format(state.threshold))
        Slider(value = state.threshold, onValueChange = viewModel::setThreshold,
            valueRange = 0.3f..0.9f, enabled = !state.busy && !state.recording)
        Text("0.60 là ngưỡng thử, chưa hiệu chỉnh cho tiếng Việt. Điểm tương đồng không phải phần trăm chính xác.")
        Button(enabled = !sttBlocked && !state.busy && (state.recording || (state.selected.isNotEmpty() && (!state.gateEnabled || state.enrollmentCount == 3))), onClick = {
            if (state.recording) viewModel.toggleRecord() else {
                requestEnrollment = false
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }) { Text(if (state.recording) "Dừng và xử lý" else "Ghi âm kiểm tra") }
        if (permissionDenied) Text("Chưa có quyền microphone. Cấp quyền trong cài đặt ứng dụng rồi thử lại.")
        SelectionContainer { Text(state.speakerResult) }
        Text(state.metrics)
        SelectionContainer { Text(state.text.ifEmpty { "Văn bản nhận dạng sẽ xuất hiện ở đây." }) }
        Text("Thử chính bạn và người khác nói lần lượt. Có thể tắt lọc để so sánh với Whisper thuần. Bộ lọc này chưa tách được giọng nói chồng nhau và chưa biết người nói có đang hướng lời nói tới bot hay không.")
    }
}
