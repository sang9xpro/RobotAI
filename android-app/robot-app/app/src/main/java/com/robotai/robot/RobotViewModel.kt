package com.robotai.robot

import android.os.SystemClock
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robotai.domain.*
import com.robotai.robot.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.URI

class RobotViewModel(application: Application) : AndroidViewModel(application) {
    private val backend = SocketBackend()
    private val identityState = MutableStateFlow<com.robotai.robot.identity.IdentityController?>(null)
    val identity = identityState.asStateFlow()
    private val awakeState = MutableStateFlow(true)
    val awake = awakeState.asStateFlow()
    private var voiceEnrollment: Job? = null
    fun enrollVoice(personId: String) {
        val controller=identityState.value ?: return
        if(stopping || voiceEnrollment!=null || state.value.phase !in listOf(Phase.IDLE,Phase.INTERRUPTED,Phase.LISTENING)) return
        if (state.value.phase == Phase.LISTENING) {
            guard.cancel()
            runCatching { control("abort", "reason" to "voice_enrollment") }
            identityState.value?.cancelTurn()
            needsFreshTransport = legacy
            update { it.copy(phase = Phase.IDLE) }
        }
        voiceEnrollment=viewModelScope.launch {
            controller.voiceBusy(true)
            val samples=ArrayList<Float>()
            try {
                mic.stop()
                mic.start({}, {}, { controller.error(it) }, { pcm -> synchronized(samples) { pcm.forEach { samples.add(it/32768f) } } })
                delay(8000)
                mic.stop()
                controller.saveVoice(personId,synchronized(samples) { samples.toFloatArray() })
            } finally { withContext(NonCancellable) { mic.stop() }; controller.voiceBusy(false); voiceEnrollment=null; interaction() }
        }
    }
    fun cancelVoiceEnrollment() { voiceEnrollment?.cancel() }

    private val mic = Microphone(viewModelScope)
    private val player = StreamPlayer(viewModelScope)
    private val guard = TurnGuard()
    val robotTools = com.robotai.robot.companion.DeviceRobotTools()
    private val wakeWord: WakeWordPort = LocalWakeWord(application)
    private val mutable = MutableStateFlow(RobotUiState())
    val state = mutable.asStateFlow()
    init { viewModelScope.launch { while (isActive) { robotTools.tick(); delay(100) } } }
    private var signedSession: com.robotai.robot.auth.UserSession? = null
    var onAuthExpired: (() -> Unit)? = null
    private var legacy = false
    private var needsFreshTransport = false
    private var roleId: Int? = null
    private val completedLatencies = mutableListOf<Long>()
    fun latencySummary(): String = "${completedLatencies.size} lượt hoàn tất · p50 ${LatencyStats.percentile(completedLatencies, 50) ?: "—"} ms · p95 ${LatencyStats.percentile(completedLatencies, 95) ?: "—"} ms"
    private fun recordCompletedTurn() {
        val latency = state.value.latencyMs ?: return
        completedLatencies.add(latency)
        val session = signedSession ?: return
        val scope = com.robotai.robot.companion.LocalCompanionStore.key(session)
        val line = JSONObject().put("at", System.currentTimeMillis()).put("roleId", roleId)
            .put("turn", guard.turn).put("connection", connection).put("latencyMs", latency)
            .put("uplinkFrames", state.value.uploaded).put("downlinkFrames", state.value.downloaded)
            .put("measurement", "manual_end_input_to_first_non_silent_sample_presented").toString()
        viewModelScope.launch(Dispatchers.IO) {
            val dir = java.io.File(getApplication<Application>().filesDir, "measurements").apply { mkdirs() }
            java.io.File(dir, "$scope-$roleId.jsonl").appendText(line + "\n")
        }
    }
    private var incomingSequence = 0L
    private var sid = ""
    private var connection = 0
    private var finishTime = 0L
    private var timeout: Job? = null
    private var stopping = false
    private var lastUrl = "ws://127.0.0.1:8766/ws/xiaozhi/v1/"
    private var listenAfterHello = false
    private var conversationEnabled = false
    private var foreground = true
    private val idle = ConversationIdle().apply { touch(SystemClock.elapsedRealtime()) }
    private var resumeConversation = false
    var wakePhrase: String = "Ken"
        private set
    fun configureWakePhrase(value: String) { if (value.isNotBlank()) wakePhrase = value.trim() }
    fun interaction() { if (awakeState.value) idle.touch(SystemClock.elapsedRealtime()) }
    private fun hasMicrophonePermission() = getApplication<Application>().checkSelfPermission(
        android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    fun resumeForeground() {
        foreground = true
        if (state.value.phase == Phase.SLEEPING) sleep(wakePhrase, lastUrl)
        else if (resumeConversation && signedSession != null) connect(lastUrl)
        resumeConversation = false
    }
    fun pauseForeground() { resumeConversation = conversationEnabled; foreground = false; disconnect() }
    init {
        viewModelScope.launch {
            while (isActive) {
                delay(250)
                if (!foreground || !conversationEnabled || !awakeState.value || voiceEnrollment != null || stopping) continue
                val phase = state.value.phase
                if (idle.shouldSleep(SystemClock.elapsedRealtime(), phase)) {
                    sleep(wakePhrase, lastUrl)
                } else if (state.value.connected && phase in listOf(Phase.IDLE, Phase.INTERRUPTED) && hasMicrophonePermission()) {
                    listen()
                }
            }
        }
    }
    private fun update(block: (RobotUiState) -> RobotUiState) { mutable.value = block(mutable.value) }

    fun setSession(session: com.robotai.robot.auth.UserSession?, selectedRole: Int? = null) {
        robotTools.enabled = false
        roleId = selectedRole
        completedLatencies.clear()
        disconnect()
        identityState.value?.close()
        identityState.value = session?.let { com.robotai.robot.identity.IdentityController(getApplication(), com.robotai.robot.companion.LocalCompanionStore.key(it)) }
        awakeState.value = session != null
        wakePhrase = session?.let {
            val key = java.security.MessageDigest.getInstance("SHA-256").digest((it.baseUrl + ":" + it.userId).toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
            getApplication<Application>().getSharedPreferences("robot-user-$key", 0).getString("wake_phrase", "Ken")
        } ?: "Ken"
        signedSession = session
        backend.clearCredentials()
        if (session != null) {
            backend.authenticate(session.userId, session.token)
            lastUrl = session.socketUrl
        }
        update { RobotUiState(message = if (session == null) "Chưa đăng nhập" else "Sẵn sàng kết nối") }
    }

    fun connect(url: String) {
        if (state.value.connecting || state.value.connected) return
        awakeState.value = true
        conversationEnabled = signedSession != null
        interaction()
        try {
            signedSession?.let { com.robotai.robot.auth.AuthApi().validate(it.baseUrl, url.trim()) }
            val uri = URI(url.trim())
            require(uri.scheme in listOf("ws", "wss") && !uri.host.isNullOrBlank()) { "Nhập địa chỉ ws:// hoặc wss:// hợp lệ" }
            val epoch = ++connection
            lastUrl = url.trim()
            update { RobotUiState(connecting = true, message = "Đang kết nối…") }
            backend.connect(url.trim(),
                { text -> viewModelScope.launch { if (epoch == connection) receive(text) } },
                { bytes -> viewModelScope.launch { if (epoch == connection) receiveAudio(bytes) } },
                { error -> viewModelScope.launch { if (epoch == connection) {
                    if (signedSession != null && error.contains("401")) { setSession(null); onAuthExpired?.invoke() }
                    else fail(error)
                } } })
            timeout = viewModelScope.launch { delay(10000); if (!state.value.connected) fail("Hello timeout") }
        } catch (e: Exception) { fail(e.message ?: "Lỗi kết nối") }
    }

    private fun control(type: String, vararg values: Pair<String, Any>) {
        val event = JSONObject().put("type", type).put("session_id", sid)
            .put("generation", guard.generation).put("turn_id", guard.turn.toString())
        values.forEach { event.put(it.first, it.second) }
        check(backend.sendText(event.toString())) { "Không gửi được control event" }
    }

    fun listen() {
        if (!foreground || !hasMicrophonePermission() || voiceEnrollment!=null || !state.value.connected || stopping || state.value.phase !in listOf(Phase.IDLE, Phase.INTERRUPTED)) return
        if (legacy && needsFreshTransport) {
            disconnect()
            val epoch = connection
            viewModelScope.launch {
                delay(200)
                if (foreground && epoch == connection && signedSession != null) {
                    listenAfterHello = true
                    connect(lastUrl)
                }
            }
            return
        }
        try {
            timeout?.cancel()
            guard.begin()
            val generation = guard.generation
            val turn = guard.turn
            var sequence = 0L
            finishTime = 0L
            update { it.copy(phase = Phase.LISTENING, caption = "", reply = "", spokenSentence = "", speechEmotion = "", uploaded = 0,
                downloaded = 0, serverFrames = 0, decodedSamples = 0, latencyMs = null,
                message = if (legacy) "Mình đang nghe · bạn cứ nói, mình tự nhận biết khi hết câu" else "Đang nghe · phụ đề sẽ là dữ liệu giả lập") }
            incomingSequence = 0
            // Backend manual means client-owned endpoint; the UI is hands-free.
            control("listen", "state" to "start", "mode" to if (legacy) "manual" else "auto")
            val controller = identityState.value
            viewModelScope.launch {
            controller?.beginTurn()
            if(!guard.accepts(generation,turn) || state.value.phase!=Phase.LISTENING) return@launch
            try { mic.start({ packet ->
                if (!backend.sendAudio(if (legacy) packet else Rai1.encode(AudioFrame(generation, turn, sequence++, packet)))) {
                    viewModelScope.launch { fail("Hàng đợi upload đầy hoặc mất kết nối") }
                } else viewModelScope.launch { if (guard.accepts(generation, turn)) update { it.copy(uploaded = it.uploaded + 1) } }
            }, { if (guard.accepts(generation, turn)) finish() },
                { if (guard.accepts(generation, turn)) fail(it) }, { controller?.audio(it) },
                automatic = conversationEnabled, onSpeech = { if (guard.accepts(generation, turn)) interaction() })
            } catch(e:Exception) { fail(e.message ?: "Lỗi micro") }
            }
        } catch (e: Exception) { fail(e.message ?: "Lỗi bắt đầu nghe") }
    }

    fun finish() = finishInput(true)
    private fun finishInput(sendStop: Boolean) {
        if (state.value.phase != Phase.LISTENING || stopping) return
        stopping = true
        interaction()
        finishTime = SystemClock.elapsedRealtime()
        update { it.copy(phase = Phase.FINALIZING, message = "Đã kết thúc câu · đang xử lý") }
        val turn = guard.turn
        val generation = guard.generation
        val controller = identityState.value
        viewModelScope.launch {
            try {
                mic.stop() // Wait for the last encoded frame before listen.stop.
                val speaker = controller?.finishTurn() ?: JSONObject().put("status","unknown")
                if (!guard.accepts(generation, turn)) return@launch
                if (sendStop) control("listen", "state" to "stop", "speaker" to speaker)
                timeout?.cancel()
                timeout = viewModelScope.launch {
                    delay(if (legacy) 120000 else 15000)
                    if (guard.accepts(guard.generation, turn) && state.value.phase != Phase.IDLE) fail("Phản hồi quá thời gian chờ")
                }
            } catch (e: Exception) { fail(e.message ?: "Lỗi kết thúc lượt") }
            finally { stopping = false }
        }
    }

    fun interrupt() {
        if (!state.value.connected || state.value.phase in listOf(Phase.IDLE, Phase.INTERRUPTED, Phase.SLEEPING)) return
        player.stop()
        robotTools.stop()
        timeout?.cancel()
        try { control("abort", "request_id" to "abort-${guard.turn}", "reason" to "user_interrupt") }
        catch (e: Exception) { fail(e.message ?: "Lỗi gửi abort"); return }
        guard.cancel()
        if (legacy) {
            // Raw Opus has no turn marker: close transport to exclude late audio before a new turn.
            disconnect()
            update { it.copy(phase = Phase.INTERRUPTED, message = "Đã ngắt · đang tạo kết nối sạch") }
            val epoch = connection
            viewModelScope.launch {
                delay(300)
                if (foreground && connection == epoch && signedSession != null) connect(lastUrl)
            }
            return
        }
        stopping = true
        update { it.copy(phase = Phase.INTERRUPTED, message = "Đã ngắt · audio/text cũ sẽ bị loại") }
        viewModelScope.launch { try { mic.stop() } finally { stopping = false } }
    }

    fun disconnect() {
        conversationEnabled = false
        cancelVoiceEnrollment()
        identityState.value?.cancelTurn()
        wakeWord.stop()
        robotTools.reset()
        listenAfterHello = false
        connection++
        timeout?.cancel()
        guard.cancel()
        backend.close()
        player.stop()
        stopping = true
        viewModelScope.launch { try { mic.stop() } finally { stopping = false } }
        update { it.copy(connected = false, connecting = false, phase = if(awakeState.value) Phase.IDLE else Phase.SLEEPING, message = "Đã ngắt kết nối") }
    }

    fun sleep(phrase: String, url: String) {
        configureWakePhrase(phrase)
        lastUrl = url.trim()
        disconnect()
        awakeState.value=false
        update { it.copy(phase = Phase.SLEEPING, message = "Đang ngủ · không truyền audio lên backend") }
        // disconnect already scheduled stop; join the recorder before another microphone owner starts.
        viewModelScope.launch {
            mic.stop()
            stopping = false
            if (foreground && state.value.phase == Phase.SLEEPING) {
                wakeWord.start(wakePhrase, { wake(true) }, { problem ->
                    if (state.value.phase == Phase.SLEEPING) update { it.copy(message = problem) }
                })
            }
        }
    }

    fun wake(fromVoice: Boolean = false) {
        if (state.value.phase != Phase.SLEEPING) return
        wakeWord.stop()
        awakeState.value=true
        update { it.copy(phase = Phase.IDLE, message = "Đã thức dậy") }
        val wakeConnection = connection
        viewModelScope.launch {
            delay(250) // Allow the local recognition service to relinquish its microphone.
            if (foreground && wakeConnection == connection && state.value.phase == Phase.IDLE && !state.value.connected) {
                listenAfterHello = fromVoice || signedSession != null
                connect(lastUrl)
            }
        }
    }

    private fun receive(text: String) {
        try {
            val e = JSONObject(text)
            if (e.getString("type") == "hello") {
                val ext = e.optJSONObject("robotai")
                legacy = signedSession != null && ext == null
                val audio = e.getJSONObject("audio_params")
                require(legacy || (ext != null && ext.getString("audio_envelope") == "rai1" && ext.getInt("contract_version") == 1 && ext.optBoolean("mock"))) {
                    "Bản này chỉ kết nối backend giả lập RAI1"
                }
                require(audio.getString("format") == "opus" && audio.getInt("sample_rate") == (if (legacy) 16000 else 24000) &&
                    audio.getInt("channels") == 1 && audio.getInt("frame_duration") == 60) { "Audio format chưa hỗ trợ" }
                needsFreshTransport = false
                player.configure(audio.getInt("sample_rate"))
                sid = e.getString("session_id")
                guard.reset(ext?.optLong("generation") ?: 0L)
                timeout?.cancel()
                update { it.copy(connected = true, connecting = false, phase = Phase.IDLE, message = if (legacy) "Đã kết nối backend" else "Đã kết nối · STT/LLM/TTS giả lập") }
                if (listenAfterHello) { listenAfterHello = false; listen() }
                return
            }
            if (e.optString("type") == "mcp") {
                if (e.optString("sessionId", e.optString("session_id")) != sid) return
                robotTools.handle(e.getJSONObject("payload"), state.value.phase in listOf(Phase.FINALIZING, Phase.THINKING, Phase.SPEAKING))?.let { result ->
                    backend.sendText(JSONObject().put("type", "mcp").put("sessionId", sid).put("payload", result).toString())
                }
                return
            }
            if (legacy) {
                if (e.has("session_id") && e.optString("session_id") != sid) return
                e.put("session_id", sid).put("generation", guard.generation).put("turn_id", guard.turn.toString())
                if (e.optString("type") == "stt") e.put("state", "final").put("revision", 0)
            }
            if (e.optString("session_id") != sid) return
            val generation = e.optLong("generation", -1)
            val turn = e.optString("turn_id").toLongOrNull() ?: return
            if (!guard.accepts(generation, turn)) {
                update { it.copy(discarded = it.discarded + 1) }
                return
            }
            when (e.getString("type")) {
                "utterance_end" -> finishInput(false)
                "stt" -> {
                    val final = e.getString("state") == "final"
                    if (guard.acceptText(generation, turn, e.getInt("revision"), final)) {
                        update { it.copy(caption = e.getString("text"), phase = if (final) Phase.THINKING else it.phase) }
                    }
                }
                "diagnostics" -> update { it.copy(serverFrames = e.getInt("uplink_frames"), decodedSamples = e.getInt("decoded_samples")) }
                "tts" -> when (e.getString("state")) {
                    "start" -> player.start({
                        if (guard.accepts(generation, turn)) {
                            val latency = if (finishTime != 0L) SystemClock.elapsedRealtime() - finishTime else null
                            update { it.copy(phase = Phase.SPEAKING, latencyMs = latency, message = if (legacy) "Đang phát phản hồi" else "Đang phát mẫu giọng Hải Đăng") }
                            if (!legacy) control("playback", "state" to "started")
                        }
                    }, {
                        if (guard.accepts(generation, turn)) {
                            timeout?.cancel()
                            recordCompletedTurn()
                            interaction()
                            needsFreshTransport = legacy
                            update { it.copy(phase = Phase.IDLE, message = "Phát xong · sẵn sàng lượt mới") }
                            if (!legacy) control("playback", "state" to "completed")
                            guard.cancel()
                        }
                    }, { fail(it) })
                    "sentence_start" -> update { it.copy(reply = (it.reply + " " + e.getString("text")).trim(), spokenSentence = e.getString("text")) }
                    "stop" -> if (!player.finish()) fail("Backend không tạo được tiếng trả lời; hãy thử lại")
                }
                "llm" -> update { it.copy(speechEmotion = e.optString("emotion")) }
                "error" -> fail(e.optString("message", e.optString("code")))
            }
        } catch (e: Exception) { fail("Contract: ${e.message}") }
    }

    private fun receiveAudio(bytes: ByteArray) {
        try {
            val frame = if (legacy) AudioFrame(guard.generation, guard.turn, incomingSequence++, bytes) else Rai1.decode(bytes)
            if (!guard.acceptAudio(frame)) { update { it.copy(discarded = it.discarded + 1) }; return }
            if (!player.offer(frame.payload)) { fail("Audio backlog: hãy kết nối lại"); return }
            update { it.copy(downloaded = it.downloaded + 1) }
        } catch (e: Exception) { fail(e.message ?: "Sai audio packet") }
    }

    private fun fail(message: String) {
        disconnect()
        update { it.copy(phase = Phase.ERROR, message = message) }
    }
    override fun onCleared() {
        identityState.value?.close()
        wakeWord.stop()
        robotTools.stop()
        backend.close()
        player.stop()
        // ViewModel scope cancellation makes recorder exit and release in finally.
        super.onCleared()
    }
}
