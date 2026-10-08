package com.whispercppdemo.ui.main

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whispercpp.whisper.WhisperContext
import com.whispercppdemo.media.decodeWaveFile
import com.whispercppdemo.recorder.Recorder
import com.whispercppdemo.recorder.VoiceSegmenter
import com.whispercppdemo.speaker.SpeakerGate
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class TestState(
    val models: List<String> = emptyList(), val selected: String = "",
    val busy: Boolean = false, val recording: Boolean = false,
    val status: String = "Chọn model để bắt đầu", val text: String = "", val metrics: String = "",
    val enrollmentCount: Int = 0, val enrolling: Boolean = false,
    val gateEnabled: Boolean = true, val threshold: Float = 0.6f,
    val speakerResult: String = "Chưa đăng ký giọng",
    val useGpu: Boolean = true, val gpuInfo: String = "Vulkan: kiểm tra khi nạp model",
    val live: Boolean = true, val pending: Int = 0, val failed: Int = 0
)
class MainScreenViewModel(private val app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(TestState())
    val state = mutable.asStateFlow()
    private var context: WhisperContext? = null
    private val recorder = Recorder()
    private val recordingFile = File(app.cacheDir, "whisper-test.wav")
    private val modelDir = File(app.filesDir, "models").apply { mkdirs() }
    private val nativeDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val speakerGate = SpeakerGate(app.assets)
    private var segments: Channel<FloatArray>? = null
    private var segmenter: VoiceSegmenter? = null
    private var processing: Job? = null
    private var generation = 0
    private var secondsTotal = 0.0
    private var inferenceTotal = 0L
    private var stopTime = 0L
    val device = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · ${Build.SUPPORTED_ABIS.first()}"
    init { refreshModels() }
    private fun refreshModels() {
        val names = (app.assets.list("models").orEmpty().filter { it == "ggml-phowhisper-small.bin" } +
            modelDir.list().orEmpty().filter { it.startsWith("ggml-phowhisper-") && it.endsWith(".bin") }).distinct()
        mutable.update { it.copy(models = names) }
    }
    fun importLarge(uri: Uri) {
        if (state.value.busy || state.value.recording) return
        mutable.update { it.copy(busy = true, status = "Đang nhập PhoWhisper-large…") }
        viewModelScope.launch {
            val temp = File(modelDir, "import.part")
            try {
                val name = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)!!.use { src -> temp.outputStream().use { src.copyTo(it) } }
                    val header = ByteArray(48)
                    temp.inputStream().use { java.io.DataInputStream(it).readFully(header) }
                    val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
                    require(h[0] == 0x67676d6c && h[3] == 1280 && h[5] == 32 && h[9] == 32 && h[10] == 80) {
                        "Cần file GGML PhoWhisper-large (không phải large-v3/turbo)."
                    }
                    require(temp.length() in 500_000_000L..4_000_000_000L) { "Kích thước model không hợp lệ" }
                    val type = h[11] % 1000
                    val suffix = when (type) { 1 -> "f16"; 7 -> "q8_0"; 8 -> "q5_0"; else -> "q$type" }
                    val name = "ggml-phowhisper-large-$suffix.bin"
                    require(temp.renameTo(File(modelDir, name))) { "Không lưu được model" }; name
                }
                refreshModels(); mutable.update { it.copy(status = "Đã nhập $name. Chọn model để nạp.") }
            } catch (e: Exception) { mutable.update { it.copy(status = "Lỗi nhập: ${e.message}") }
            } finally { temp.delete(); mutable.update { it.copy(busy = false) } }
        }
    }
    fun setGpu(value: Boolean) {
        if (state.value.busy || state.value.recording) return
        mutable.update { it.copy(useGpu = value) }
        if (state.value.selected.isNotEmpty()) loadModel(state.value.selected)
    }
    fun setLive(value: Boolean) { if (!state.value.busy && !state.value.recording) mutable.update { it.copy(live = value) } }
    fun loadModel(name: String) {
        if (state.value.busy || state.value.recording || name !in state.value.models) return
        val gpu = state.value.useGpu
        mutable.update { it.copy(busy = true, status = "Đang nạp $name · ${if (gpu) "Vulkan" else "CPU"}…") }
        viewModelScope.launch {
            try {
                val start = SystemClock.elapsedRealtime()
                val info = withContext(nativeDispatcher) {
                    context?.release(); context = null
                    val file = File(modelDir, name)
                    if (!file.exists()) app.assets.open("models/$name").use { src -> file.outputStream().use { src.copyTo(it) } }
                    context = WhisperContext.createContextFromFile(file.absolutePath, gpu)
                    if (gpu) WhisperContext.getGpuInfo() else "CPU"
                }
                mutable.update { it.copy(selected = name, gpuInfo = info, status = "Sẵn sàng · nạp ${SystemClock.elapsedRealtime() - start} ms · $info") }
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) { mutable.update { it.copy(selected = "", status = "Lỗi nạp model: ${e.message}. Có thể thử CPU.") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun setThreshold(value: Float) { if (!state.value.busy && !state.value.recording) mutable.update { it.copy(threshold = value.coerceIn(0.3f, 0.9f)) } }
    fun setGateEnabled(enabled: Boolean) { if (!state.value.busy && !state.value.recording) mutable.update { it.copy(gateEnabled = enabled) } }
    fun clearVoice() {
        if (state.value.busy || state.value.recording) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { withContext(nativeDispatcher) { speakerGate.clear() }; mutable.update { it.copy(enrollmentCount = 0, speakerResult = "Đã xóa mẫu giọng") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private suspend fun recognize(data: FloatArray, id: Int) {
        val threshold = state.value.threshold
        val verify = if (state.value.gateEnabled) withContext(nativeDispatcher) { speakerGate.verify(data, threshold) } else null
        if (generation != id) return
        mutable.update { it.copy(speakerResult = if (verify == null) "Đã tắt lọc giọng" else
            "${if (verify.accepted) "Khớp mẫu" else "Không đủ khớp mẫu"} · điểm ${verify.scores.joinToString { "%.3f".format(it) }}") }
        if (verify != null && !verify.accepted) return
        val start = SystemClock.elapsedRealtime()
        val (text, timing) = withContext(nativeDispatcher) { context!!.transcribeData(data, false).trim() to context!!.timings() }
        if (generation != id) return
        inferenceTotal += SystemClock.elapsedRealtime() - start; secondsTotal += data.size / 16000.0
        mutable.update { it.copy(text = (it.text + " " + text).trim(), metrics =
            "${it.selected} · ${if (it.useGpu) "Vulkan" else "CPU"} · audio %.2f s · STT %d ms · RTF %.2f\n%s".format(secondsTotal, inferenceTotal, inferenceTotal / 1000.0 / secondsTotal, timing)) }
    }
    fun toggleRecord(enroll: Boolean = false) {
        val current = state.value
        if (current.busy) return
        if (current.recording) { finishRecording(); return }
        if (enroll && current.enrollmentCount >= 3) return
        if (!enroll && (context == null || (current.gateEnabled && current.enrollmentCount < 3))) return
        mutable.update { it.copy(busy = true, text = "", metrics = "", enrolling = enroll, pending = 0, failed = 0) }
        viewModelScope.launch {
            try {
                generation++; val id = generation; secondsTotal = 0.0; inferenceTotal = 0; stopTime = 0
                if (!enroll && current.live) {
                    val channel = Channel<FloatArray>(16); segments = channel
                    segmenter = VoiceSegmenter { data ->
                        if (generation == id) {
                            mutable.update { it.copy(pending = it.pending + 1) }
                            check(channel.trySend(data).isSuccess) { "Hàng đợi nhận dạng đầy" }
                        }
                    }
                    processing = viewModelScope.launch {
                        for (data in channel) {
                            try { recognize(data, id) }
                            catch (e: CancellationException) { throw e }
                            catch (e: Throwable) { mutable.update { it.copy(failed = it.failed + 1, status = "Lỗi đoạn STT: ${e.message}") } }
                            finally { if (generation == id) mutable.update { it.copy(pending = (it.pending - 1).coerceAtLeast(0)) } }
                        }
                    }
                }
                recorder.startRecording(recordingFile,
                    onAudio = { segmenter?.accept(it) },
                    onFinished = { viewModelScope.launch { if (state.value.recording && generation == id) finishRecording() } },
                    onError = { error -> viewModelScope.launch { stopOnBackground(); mutable.update { it.copy(status = "Lỗi microphone: ${error.message}") } } })
                mutable.update { it.copy(recording = true, status = if (enroll) "Đăng ký mẫu ${it.enrollmentCount + 1}/3: nói 5–10 giây"
                    else if (it.live) "Đang nghe và nhận dạng theo khoảng nghỉ · tối đa 30 giây" else "Đang ghi âm · tối đa 30 giây") }
            } catch (e: Throwable) { mutable.update { it.copy(recording = false, status = "Lỗi: ${e.message}") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private fun finishRecording() {
        if (state.value.busy || !state.value.recording) return
        mutable.update { it.copy(busy = true, status = "Đang xử lý phần còn lại…") }; stopTime = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            try {
                recorder.stopRecording(); mutable.update { it.copy(recording = false) }
                if (state.value.enrolling) {
                    val data = withContext(Dispatchers.IO) { decodeWaveFile(recordingFile) }
                    val count = withContext(nativeDispatcher) { speakerGate.enroll(data) }
                    mutable.update { it.copy(enrollmentCount = count, speakerResult = "Đã đăng ký $count/3 mẫu", status = "Hoàn tất đăng ký mẫu") }
                } else {
                    if (segments != null) { segmenter?.finish(); segments!!.close(); processing?.join() }
                    else recognize(withContext(Dispatchers.IO) { decodeWaveFile(recordingFile) }, generation)
                    val tail = SystemClock.elapsedRealtime() - stopTime
                    mutable.update { it.copy(status = "${if (it.failed > 0) "Có ${it.failed} đoạn lỗi" else "Hoàn tất"} · chờ sau Dừng $tail ms", metrics = it.metrics + "\nChờ sau Dừng $tail ms") }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) { mutable.update { it.copy(status = "Lỗi: ${e.message}") }
            } finally { segments?.close(); segments = null; segmenter = null; processing = null; mutable.update { it.copy(busy = false, recording = false, enrolling = false) } }
        }
    }
    fun stopOnBackground() {
        if (!state.value.recording) return
        generation++; processing?.cancel(); segments?.cancel(); segments = null
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            recorder.stopRecording(); segmenter = null
            mutable.update { it.copy(recording = false, busy = false, pending = 0, status = "Đã dừng khi app vào nền") }
        }
    }
    override fun onCleared() {
        generation++; processing?.cancel(); segments?.cancel()
        CoroutineScope(nativeDispatcher).launch { recorder.stopRecording(); recorder.close(); context?.release(); speakerGate.release(); nativeDispatcher.close() }
    }
}
