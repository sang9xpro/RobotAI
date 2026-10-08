package com.whispercppdemo.tts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*

@Composable
fun TtsPanel(viewModel: TtsViewModel, sttBusy: Boolean) {
    val state by viewModel.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.stop() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { it?.let(viewModel::export) }
    Text("TTS · Giọng Hải Đăng", style = MaterialTheme.typography.titleLarge)
    Text("VieNeu-TTS-v3-Turbo · chạy trên điện thoại. Lần đầu cần sao chép và nạp model. Phát từng phần ngay khi có audio, đồng thời tiếp tục tạo phần sau.")
    OutlinedTextField(value = state.text, onValueChange = viewModel::setText, enabled = !state.busy,
        label = { Text("Văn bản tiếng Việt · tối đa 200 ký tự") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::speak, enabled = !sttBusy && !state.busy && state.text.isNotBlank()) { Text("Tạo và phát từng phần") }
        OutlinedButton(onClick = viewModel::stop, enabled = state.busy || state.playing) { Text("Dừng") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::replay, enabled = !state.busy && !sttBusy && state.hasAudio) { Text("Phát lại") }
        OutlinedButton(onClick = { export.launch("vieneu-haidang.wav") }, enabled = !state.busy && state.hasAudio) { Text("Lưu WAV") }
    }
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(state.status); Text(state.metrics)
}
