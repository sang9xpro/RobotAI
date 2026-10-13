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
import android.webkit.WebView

class RobotViewModel(application: Application) : AndroidViewModel(application) {
    private val backend = SocketBackend()
    private val identityState = MutableStateFlow<com.robotai.robot.identity.IdentityController?>(null)
    val identity = identityState.asStateFlow()
    private val awakeState = MutableStateFlow(true)
    val awake = awakeState.asStateFlow()
    private var voiceEnrollment: Job? = null
    fun enrollVoice(personId: String) {
        val controller=identityState.value ?: return
        if(conversationVisible || !foreground || !hasMicrophonePermission() || music.value.active || voiceEnrollment!=null || state.value.phase !in listOf(Phase.IDLE,Phase.INTERRUPTED)) return
        voiceEnrollment=viewModelScope.launch {
            controller.voiceBusy(true)
            val samples=ArrayList<Float>()
            try {
                microphoneStop?.join()
                mic.stop()
                var captureError: String? = null
                mic.start({}, {}, { captureError=it }, { pcm -> synchronized(samples) {
                    val remaining=SpeakerAudioWindows.ENROLLMENT_SAMPLES-samples.size
                    for(index in 0 until minOf(pcm.size,remaining)) samples.add(pcm[index]/32768f)
                } })
                withTimeout(15000) {
                    while(true) {
                        captureError?.let { error(it) }
                        val count=synchronized(samples) { samples.size }
                        controller.voiceProgress(count.toFloat()/SpeakerAudioWindows.SAMPLE_RATE)
                        if(count>=SpeakerAudioWindows.ENROLLMENT_SAMPLES)break
                        delay(100)
                    }
                }
                mic.stop()
                controller.voiceProgress(8f,processing=true)
                controller.saveVoice(personId,synchronized(samples) { samples.toFloatArray() })
            } catch(e:TimeoutCancellationException) { controller.error("Micro chưa thu đủ âm thanh. Hãy kiểm tra quyền micro và thử lại.") }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { controller.error(e.message ?: "Không thu được mẫu giọng") }
            finally { withContext(NonCancellable) { mic.stop() }; controller.voiceBusy(false); voiceEnrollment=null; interaction() }
        }
    }
    fun deletePerson(personId: String) {
        val account=signedSession ?: return
        val controller=identityState.value ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { com.robotai.robot.auth.AuthApi().request(account.baseUrl,
                    "/api/robot/people/$personId",account.token,"DELETE") }
                if(signedSession===account && identityState.value===controller) {
                    disconnect(); controller.delete(personId)
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { controller.error("Chưa xóa được ký ức trên backend: ${e.message}") }
        }
    }
    fun cancelVoiceEnrollment() { voiceEnrollment?.cancel() }

    private val mic = Microphone(viewModelScope, application.assets)
    private val player = StreamPlayer(viewModelScope)
    val speechLevel = player.speechLevel
    private val guard = TurnGuard()
    private val musicPlayer = MusicPlayer(application)
    private val youtubeSearch by lazy { YouTubeMusicSearch.from(application) }
    private var youtubeSearchJob: Job? = null
    val music = musicPlayer.state
    private val musicTools = com.robotai.robot.companion.DeviceMusicTools(musicPlayer, ::playMusic).apply {
        onYouTubeSearch = ::searchYouTube
        onStop = ::stopMusic
    }
    val robotTools = com.robotai.robot.companion.DeviceRobotTools(musicTools)
    private fun playMusic(title: String, artist: String, link: String) {
        check(foreground && signedSession != null && state.value.connected) { "App phải ở foreground và đã kết nối" }
        youtubeSearchJob?.cancel()
        val session = signedSession!!
        val source = MusicSource.resolve(session.baseUrl, session.token, link)
        val videoId = YouTubeVideo.id(source.url)
        if (videoId != null) musicPlayer.playYouTube(title, artist, videoId)
        else musicPlayer.play(title, artist, source.url, source.headers)
        timeout?.cancel()
        interaction()
        viewModelScope.launch { mic.stop() }
    }
    private fun searchYouTube(title: String, artist: String, query: String, videoUrl: String) {
        check(foreground && signedSession != null && state.value.connected) { "App phải ở foreground và đã kết nối" }
        youtubeSearchJob?.cancel()
        musicPlayer.beginYouTubeSearch(title, artist)
        timeout?.cancel()
        interaction()
        viewModelScope.launch { mic.stop() }
        youtubeSearchJob = viewModelScope.launch {
            try {
                val video = withContext(Dispatchers.IO) { youtubeSearch.find(title, artist, query, videoUrl) }
                musicPlayer.playYouTube(video.title, video.artist, video.videoId)
            } catch (_: CancellationException) {
                // A stop, disconnect or newer search has already replaced this request.
            } catch (e: Exception) {
                musicPlayer.stop(e.message ?: "Không tìm được video YouTube", error = true)
            }
        }
    }
    fun stopMusic() { youtubeSearchJob?.cancel(); youtubeSearchJob = null; musicPlayer.stop("Đã dừng nhạc"); interaction() }
    fun pauseMusic() = musicPlayer.pauseOrResume()
    fun attachYouTube(view: WebView) = musicPlayer.attachYouTube(view)
    fun detachYouTube(view: WebView) = musicPlayer.detachYouTube(view)
    private val wakeWord: WakeWordPort = LocalWakeWord(application)
    private val mutable = MutableStateFlow(RobotUiState())
    val state = mutable.asStateFlow()
    init { viewModelScope.launch { while (isActive) { robotTools.tick(); delay(100) } } }
    private var signedSession: com.robotai.robot.auth.UserSession? = null
    var onAuthExpired: (() -> Unit)? = null
    private var individualProtocol = false
    private val individualListening = IndividualListening()
    private val capturedWake = CapturedWakeRecognizer(application, viewModelScope)
    @Volatile private var pendingPackets = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
    private fun resetPendingAudio() {
        val previous=pendingPackets
        synchronized(previous) { previous.forEach { it.fill(0) };previous.clear() }
        pendingPackets=java.util.Collections.synchronizedList(mutableListOf())
    }
    private var manualTurn = false
    @Volatile private var lastPlayedSequence = -1L
    private var localWakeAvailable = false
    private var interruptionCheck: Job? = null
    private var interruptionStartedAt = -1L
    private var nextInterruptionCheck = 0L
    @Volatile private var interruptionCapture = false
    private var interruptionTurn = false
    private var interruptionOwner: JSONObject? = null
    private val interruptionReady get() = android.media.audiofx.AcousticEchoCanceler.isAvailable() &&
        com.robotai.robot.data.ConversationReadiness.passed(getApplication(),"bargeIn")
    private var sleepingRequest = false
    private var sleepingWakeConfirmed = false
    private var uploadSequence = 0L
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
    @Volatile private var connection = 0
    private var finishTime = 0L
    private var timeout: Job? = null
    private var stopping = false
    private var lastUrl = "ws://127.0.0.1:8766/ws/xiaozhi/v1/"
    private var listenAfterHello = false
    private var conversationEnabled = false
    @Volatile private var conversationVisible = true
    private var resumeOnConversationScreen = false
    private var screenResume: Job? = null
    private var microphoneStop: Job? = null
    private var foreground = true
    private val idle = ConversationIdle().apply { touch(SystemClock.elapsedRealtime()) }
    init { viewModelScope.launch { music.collect { if (!it.active) interaction() } } }
    init { viewModelScope.launch {
        while (isActive) {
            delay(15000)
            if (foreground && state.value.connected && music.value.active) backend.reportMusic(music.value.phase)
        }
    } }
    private var resumeConversation = false
    var wakePhrase: String = "Ken"
        private set
    fun configureWakePhrase(value: String) { if (value.isNotBlank()) wakePhrase = value.trim() }
    fun interaction() { if (awakeState.value) idle.touch(SystemClock.elapsedRealtime()) }
    private fun hasMicrophonePermission() = getApplication<Application>().checkSelfPermission(
        android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    fun resumeForeground() {
        foreground = true
        if (signedSession != null && !state.value.connected && !state.value.connecting) connect(lastUrl)
        if (!conversationVisible) return
        if (state.value.phase == Phase.SLEEPING) sleep(wakePhrase, lastUrl)
        else if ((resumeConversation || resumeOnConversationScreen) && signedSession != null) connect(lastUrl)
        resumeConversation = false
        resumeOnConversationScreen = false
    }
    fun pauseForeground() {
        resumeConversation = conversationVisible && conversationEnabled
        foreground = false
        val resumeScreen = resumeOnConversationScreen
        disconnect()
        resumeOnConversationScreen = resumeScreen
    }

    /** Conversation owns the microphone only while a conversation surface is visible. */
    fun setConversationScreen(visible: Boolean) {
        if (conversationVisible == visible) return
        conversationVisible = visible
        screenResume?.cancel()
        if (!visible) {
            val resume = conversationEnabled || state.value.connected || state.value.connecting || resumeConversation || resumeOnConversationScreen
            resumeConversation = false
            if (state.value.connected) runCatching { control("abort", "reason" to "leave_conversation_screen") }
            // Keep the authenticated model connection; only suspend microphone/audio on hidden screens.
            interruptionCheck?.cancel(); interruptionCapture = false
            capturedWake.stop(); wakeWord.stop(); robotTools.stop()
            resetPendingAudio()
            stopping = true
            microphoneStop = viewModelScope.launch { try { mic.stop() } finally { stopping = false } }
            player.stop()
            guard.cancel()
            update { it.copy(phase = Phase.IDLE) }
            resumeOnConversationScreen = resume
            update { it.copy(message = "Trò chuyện tạm dừng ngoài màn Ken và Hội thoại") }
        } else {
            val enrollment = voiceEnrollment
            enrollment?.cancel()
            val epoch = connection
            screenResume = viewModelScope.launch {
                enrollment?.join() // Enrollment's finally releases the microphone before listening resumes.
                microphoneStop?.join()
                if (!conversationVisible || !foreground || epoch != connection) return@launch
                val resume = resumeOnConversationScreen
                resumeOnConversationScreen = false
                interaction()
                if (state.value.phase == Phase.SLEEPING) sleep(wakePhrase, lastUrl)
                else if (resume && signedSession != null) connect(lastUrl)
            }
        }
    }
    init {
        viewModelScope.launch {
            while (isActive) {
                delay(250)
                if (!foreground || !conversationVisible || !conversationEnabled || !awakeState.value || voiceEnrollment != null || stopping || music.value.active) continue
                val phase = state.value.phase
                if (idle.shouldSleep(SystemClock.elapsedRealtime(), phase)) {
                    sleep(wakePhrase, lastUrl)
                } else if (state.value.connected && phase in listOf(Phase.IDLE, Phase.INTERRUPTED) && hasMicrophonePermission()) {
                    listen(manual = false)
                }
            }
        }
    }
    private fun update(block: (RobotUiState) -> RobotUiState) { mutable.value = block(mutable.value) }

    fun setSession(session: com.robotai.robot.auth.UserSession?, selectedRole: Int? = null) {
        resumeConversation = false
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
        update { RobotUiState(message = if (session == null) "Chưa đăng nhập" else "Đang chuẩn bị kết nối") }
        if (session != null && foreground && selectedRole != null) connect(lastUrl)
    }

    fun connect(url: String) {
        if (!foreground) return
        if (signedSession != null && roleId == null) {
            update { it.copy(message = "Hãy chọn nhân vật để chuẩn bị phiên mô hình") }
            return
        }
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
            timeout = viewModelScope.launch { delay(100000); if (!state.value.connected) fail("Chuẩn bị phiên mô hình quá thời gian") }
        } catch (e: Exception) { fail(e.message ?: "Lỗi kết nối") }
    }

    private fun control(type: String, vararg values: Pair<String, Any>) {
        val event = JSONObject().put("type", type).put("session_id", sid)
            .put("generation", guard.generation).put("turn_id", guard.turn.toString())
        values.forEach { event.put(it.first, it.second) }
        check(backend.sendText(event.toString())) { "Không gửi được control event" }
    }

    fun listen(manual: Boolean = true) {
        if(!manual && signedSession!=null && !individualProtocol) return
        if (music.value.active || !foreground || !conversationVisible || !hasMicrophonePermission() || voiceEnrollment!=null || !state.value.connected || stopping || state.value.phase !in listOf(Phase.IDLE, Phase.INTERRUPTED)) return
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
            uploadSequence = 0L
            manualTurn = manual
            interruptionTurn = false
            lastPlayedSequence = -1
            resetPendingAudio()
            val capturePackets=pendingPackets
            localWakeAvailable = individualProtocol && capturedWake.start(wakePhrase)
            finishTime = 0L
            update { it.copy(phase = Phase.LISTENING, caption = "", reply = "", spokenSentence = "", speechEmotion = "", uploaded = 0,
                downloaded = 0, serverFrames = 0, decodedSamples = 0, latencyMs = null,
                message = if (individualProtocol) "Đang nghe · gọi $wakePhrase ơi để bắt đầu" else if (legacy) "Mình đang nghe · bạn cứ nói, mình tự nhận biết khi hết câu" else "Đang nghe · phụ đề sẽ là dữ liệu giả lập") }
            incomingSequence = 0
            // Backend manual means client-owned endpoint; the UI is hands-free.
            control("listen", "state" to "start", "mode" to if (legacy || individualProtocol) "manual" else "auto")
            val controller = identityState.value
            viewModelScope.launch {
            controller?.beginTurn()
            if(!conversationVisible || !foreground || !guard.accepts(generation,turn) || state.value.phase!=Phase.LISTENING) return@launch
            try { mic.start(onPacket@{ packet ->
                if (!conversationVisible || !guard.accepts(generation, turn)) return@onPacket
                if (individualProtocol && !manualTurn) {
                    if(capturePackets.size < 510) capturePackets.add(packet) else viewModelScope.launch { fail("Câu nói vượt giới hạn 30 giây") }
                    return@onPacket
                }
                if (!backend.sendAudio(if (legacy) packet else Rai1.encode(AudioFrame(generation, turn, uploadSequence++, packet)))) {
                    viewModelScope.launch { if (conversationVisible && guard.accepts(generation, turn)) fail("Hàng đợi upload đầy hoặc mất kết nối") }
                } else viewModelScope.launch { if (guard.accepts(generation, turn)) update { it.copy(uploaded = it.uploaded + 1) } }
            }, { if (guard.accepts(generation, turn)) finish() },
                { if (guard.accepts(generation, turn)) fail(it) }, { if(conversationVisible && guard.accepts(generation,turn)) { controller?.audio(it); if(individualProtocol && localWakeAvailable) capturedWake.audio(it) } },
                automatic = conversationEnabled, onSpeech = { if (guard.accepts(generation, turn)) { controller?.speechActivity(); interaction() } },
                waitingForRequest = { individualProtocol && capturedWake.onlyCall })
            } catch(e:Exception) { if(conversationVisible && guard.accepts(generation,turn)) fail(e.message ?: "Lỗi micro") }
            }
        } catch (e: Exception) { fail(e.message ?: "Lỗi bắt đầu nghe") }
    }

    fun finish() = finishInput(true)
    private fun finishInput(sendStop: Boolean) {
        if (state.value.phase != Phase.LISTENING || stopping) return
        stopping = true
        if(!individualProtocol || manualTurn || pendingPackets.isNotEmpty()) interaction()
        finishTime = SystemClock.elapsedRealtime()
        update { it.copy(phase = Phase.FINALIZING, message = "Đã kết thúc câu · đang xử lý") }
        val turn = guard.turn
        val generation = guard.generation
        val controller = identityState.value
        viewModelScope.launch {
            try {
                mic.stop() // Wait for the last encoded frame before listen.stop.
                val called = individualProtocol && (sleepingWakeConfirmed || if(localWakeAvailable) capturedWake.finish() else false)
                sleepingWakeConfirmed=false
                var speaker = interruptionOwner ?: controller?.finishTurn() ?: JSONObject().put("status","unknown")
                interruptionOwner=null
                interruptionCapture=false
                if (!guard.accepts(generation, turn)) return@launch
                if (individualProtocol) {
                    if(called || manualTurn) speaker = controller?.acceptSpeaker(speaker, called || manualTurn) ?: speaker
                    val evidence = controller?.listeningEvidence(speaker) ?: ListeningEvidence()
                    val admission = individualListening.admit(speaker.optString("person_id").takeIf { it.isNotBlank() },
                        speaker.optString("status", "unknown"), evidence, SystemClock.elapsedRealtime(), wake=called, manual=manualTurn, interruption=interruptionTurn)
                    if(!admission.accepted) {
                        control("abort", "reason" to "not_addressed")
                        guard.cancel()
                        resetPendingAudio()
                        update { it.copy(phase=Phase.IDLE, message=if(localWakeAvailable) "Gọi $wakePhrase ơi để bắt đầu" else "Chạm micro để nói · nhận dạng local chưa sẵn sàng") }
                        return@launch
                    }
                    if(!called && !manualTurn) controller?.acceptSpeaker(speaker, false)
                    if(!manualTurn) {
                        val packets = synchronized(pendingPackets) { pendingPackets.toList().also { pendingPackets.clear() } }
                        try {
                            for(packet in packets) {
                                val frame = Rai1.encode(AudioFrame(generation, turn, uploadSequence, packet))
                                withTimeout(2000) { while(!backend.sendAudio(frame)) { check(guard.accepts(generation,turn)) { "Lượt đã bị hủy" }; delay(10) } }
                                uploadSequence++
                            }
                        } finally { packets.forEach { it.fill(0) } }
                        update { it.copy(uploaded=uploadSequence.toInt()) }
                    }
                    val context = JSONObject().put("conversation_id", admission.conversationId).put("revision", admission.revision)
                        .put("reason", admission.reason).put("robot_name", wakePhrase).put("accepted", true)
                        .put("visible", evidence.visible).put("facing_robot", evidence.facing)
                        .put("active_speaker_verified", evidence.activeSpeaker).put("camera_age_ms", evidence.cameraAgeMs)
                        .put("visible_people", evidence.people).put("overlapping", evidence.overlapping).put("uncertain", evidence.uncertain)
                    if(sendStop) control("listen", "state" to "stop", "speaker" to speaker, "listening_context" to context)
                } else if (sendStop) control("listen", "state" to "stop", "speaker" to speaker)
                timeout?.cancel()
                timeout = viewModelScope.launch {
                    delay(if (legacy || individualProtocol) 120000 else 15000)
                    if (guard.accepts(guard.generation, turn) && state.value.phase != Phase.IDLE) fail("Phản hồi quá thời gian chờ")
                }
            } catch (e: Exception) { fail(e.message ?: "Lỗi kết thúc lượt") }
            finally { stopping = false }
        }
    }

    fun interrupt() {
        stopMusic()
        if (!state.value.connected || state.value.phase in listOf(Phase.IDLE, Phase.INTERRUPTED, Phase.SLEEPING)) return
        interruptionCheck?.cancel(); interruptionCapture=false; capturedWake.stop()
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
        resumeOnConversationScreen = false
        conversationEnabled = false
        cancelVoiceEnrollment()
        identityState.value?.cancelTurn()
        wakeWord.stop()
        capturedWake.stop()
        interruptionCheck?.cancel();interruptionCapture=false;interruptionTurn=false;interruptionOwner=null
        sleepingRequest=false; sleepingWakeConfirmed=false
        individualListening.reset()
        identityState.value?.clearTemporarySpeaker()
        resetPendingAudio()
        robotTools.reset()
        listenAfterHello = false
        connection++
        timeout?.cancel()
        guard.cancel()
        backend.close()
        player.stop()
        stopping = true
        microphoneStop = viewModelScope.launch { try { mic.stop() } finally { stopping = false } }
        update { it.copy(connected = false, connecting = false, phase = if(awakeState.value) Phase.IDLE else Phase.SLEEPING, message = "Đã ngắt kết nối") }
    }

    fun sleep(phrase: String, url: String) {
        configureWakePhrase(phrase)
        lastUrl = url.trim()
        if (signedSession != null && (state.value.connected || state.value.connecting) && !legacy) {
            if (state.value.connected) runCatching { control("abort", "reason" to "sleep") }
            interruptionCheck?.cancel(); interruptionCapture = false
            capturedWake.stop(); robotTools.stop(); timeout?.cancel(); guard.cancel()
            player.stop(); resetPendingAudio()
            microphoneStop = viewModelScope.launch { mic.stop() }
        } else disconnect()
        awakeState.value=false
        update { it.copy(phase = Phase.SLEEPING, message = "Đang ngủ · không truyền audio lên backend") }
        val epoch = connection
        viewModelScope.launch { beginSleepingCapture(epoch) }
    }

    private suspend fun beginSleepingCapture(epoch: Int) {
        mic.stop(); stopping=false
        if(!foreground || !conversationVisible || connection!=epoch || state.value.phase!=Phase.SLEEPING) return
        if(!capturedWake.start(wakePhrase)) {
            update { it.copy(message="Chạm Đánh thức · máy chưa hỗ trợ nhận dạng local từ bản thu") }
            return
        }
        localWakeAvailable=true
        resetPendingAudio()
        identityState.value?.beginTurn()
        val capturePackets=pendingPackets
        mic.start({ packet -> if(connection==epoch && state.value.phase==Phase.SLEEPING && capturePackets.size<510) capturePackets.add(packet) }, {
            viewModelScope.launch {
                mic.stop()
                val called=capturedWake.finish()
                if(connection!=epoch || state.value.phase!=Phase.SLEEPING) return@launch
                if(called) {
                    sleepingRequest=true; sleepingWakeConfirmed=true
                    wake(fromVoice=true)
                } else {
                    identityState.value?.cancelTurn()
                    resetPendingAudio()
                    delay(300); beginSleepingCapture(epoch)
                }
            }
        }, { problem ->
            capturedWake.stop(); identityState.value?.cancelTurn()
            update { it.copy(message=problem) }
        }, { pcm ->
            if(connection==epoch && state.value.phase==Phase.SLEEPING) {
                identityState.value?.audio(pcm); capturedWake.audio(pcm)
            }
        }, automatic=true, waitingForRequest={capturedWake.onlyCall})
    }

    private fun deliverSleepingRequest() {
        guard.begin(); uploadSequence=0; lastPlayedSequence=-1; manualTurn=false
        control("listen", "state" to "start", "mode" to "manual")
        update { it.copy(phase=Phase.LISTENING, message="Đã nghe lời gọi · đang xử lý câu hỏi") }
        sleepingRequest=false
        finishInput(true)
    }

    fun wake(fromVoice: Boolean = false) {
        if (!conversationVisible || state.value.phase != Phase.SLEEPING) return
        wakeWord.stop()
        awakeState.value=true
        update { it.copy(phase = Phase.IDLE, message = "Đã thức dậy") }
        val wakeConnection = connection
        viewModelScope.launch {
            if(!sleepingRequest) {
                capturedWake.stop(); mic.stop(); identityState.value?.cancelTurn()
                resetPendingAudio()
            }
            delay(250)
            if (foreground && wakeConnection == connection && state.value.phase == Phase.IDLE && !state.value.connected) {
                listenAfterHello = fromVoice || signedSession != null
                connect(lastUrl)
            } else if (foreground && wakeConnection == connection && state.value.phase == Phase.IDLE && state.value.connected) {
                if (sleepingRequest && individualProtocol) deliverSleepingRequest()
                else if (fromVoice || signedSession != null) listen(manual = false)
            }
        }
    }

    private fun beginInterruptionCapture(generation: Long, turn: Long) {
        if(!individualProtocol || !interruptionReady || interruptionCapture || stopping) return
        interruptionCapture=true;interruptionStartedAt=-1;nextInterruptionCheck=0
        resetPendingAudio()
        val epoch=connection
        viewModelScope.launch {
            mic.stop()
            val controller=identityState.value
            val capturePackets=pendingPackets
            controller?.beginTurn()
            localWakeAvailable=capturedWake.start(wakePhrase) {
                if(connection==epoch && interruptionCapture && state.value.phase==Phase.SPEAKING) openInterruptionTurn()
            }
            if(!localWakeAvailable || !guard.accepts(generation,turn) || state.value.phase!=Phase.SPEAKING) { interruptionCapture=false;capturedWake.stop();return@launch }
            mic.start({ packet -> if(connection==epoch && interruptionCapture && capturePackets.size<510) capturePackets.add(packet) }, {
                viewModelScope.launch finishCapture@{
                    mic.stop()
                    if(connection!=epoch || !interruptionCapture) return@finishCapture
                    if(state.value.phase==Phase.SPEAKING) {
                        val speaker=controller?.finishTurn() ?: JSONObject().put("status","unknown")
                        val evidence=controller?.listeningEvidence(speaker) ?: ListeningEvidence()
                        val owner=individualListening.owns(speaker.optString("person_id").takeIf { it.isNotBlank() },speaker.optString("status"),SystemClock.elapsedRealtime())
                        if(!owner || evidence.uncertain || evidence.overlapping) {
                            interruptionCapture=false;capturedWake.stop();controller?.cancelTurn()
                            resetPendingAudio()
                            if(guard.accepts(generation,turn) && state.value.phase==Phase.SPEAKING) beginInterruptionCapture(generation,turn)
                            return@finishCapture
                        }
                        interruptionOwner=speaker
                        openInterruptionTurn()
                    }
                    if(state.value.phase==Phase.LISTENING) finishInput(true)
                }
            }, { error ->
                interruptionCapture=false;capturedWake.stop();controller?.cancelTurn()
                update { it.copy(message="Ngắt lời bằng giọng chưa sẵn sàng: $error") }
            }, { pcm -> if(connection==epoch && interruptionCapture) { controller?.audio(pcm);capturedWake.audio(pcm) } },
                automatic=true,onSpeech={
                    controller?.speechActivity()
                    val now=SystemClock.elapsedRealtime()
                    if(interruptionStartedAt<0) interruptionStartedAt=now
                    if(state.value.phase==Phase.SPEAKING && now-interruptionStartedAt>=600 && now>=nextInterruptionCheck && interruptionCheck?.isActive!=true) {
                        nextInterruptionCheck=now+500
                        interruptionCheck=viewModelScope.launch {
                            val speaker=runCatching { controller?.interruptionSpeaker() }.getOrNull()
                            if(connection==epoch && interruptionCapture && state.value.phase==Phase.SPEAKING && speaker!=null &&
                                individualListening.owns(speaker.optString("person_id"),speaker.optString("status"),SystemClock.elapsedRealtime())) openInterruptionTurn()
                        }
                    }
                }, waitingForRequest={capturedWake.onlyCall},echoCancellation=true)
        }
    }
    private fun openInterruptionTurn() {
        if(!interruptionCapture || state.value.phase!=Phase.SPEAKING) return
        val latency=interruptionStartedAt.takeIf { it>=0 }?.let { SystemClock.elapsedRealtime()-it }
        control("abort", "reason" to "eligible_interruption")
        player.stop();robotTools.stop();timeout?.cancel();guard.cancel()
        guard.begin();uploadSequence=0;lastPlayedSequence=-1;manualTurn=false;interruptionTurn=true
        control("listen", "state" to "start", "mode" to "manual")
        update { it.copy(phase=Phase.LISTENING,caption="",reply="",spokenSentence="",message="Mình đang nghe bạn") }
        if(latency!=null) viewModelScope.launch(Dispatchers.IO) {
            val dir=java.io.File(getApplication<Application>().filesDir,"measurements").apply { mkdirs() }
            java.io.File(dir,"interruptions.jsonl").appendText(JSONObject().put("at",System.currentTimeMillis())
                .put("latencyMs",latency).put("measurement","speech_start_to_audio_flush").toString()+"\n")
        }
    }

    private fun receive(text: String) {
        if (!foreground) return
        try {
            val e = JSONObject(text)
            if (e.optString("type") == "session") {
                when (e.optString("state")) {
                    "preparing" -> update { it.copy(connecting = true, connected = false, message = "Đang làm nóng phiên mô hình…") }
                    "failed" -> fail(e.optString("message", "Không thể chuẩn bị phiên mô hình"))
                }
                return
            }
            if (e.getString("type") == "hello") {
                val ext = e.optJSONObject("robotai")
                if(state.value.connected) {
                    require(e.optString("session_id")==sid && (ext?.optLong("generation") ?: 0L)==guard.generation) { "Backend đổi transport giữa phiên" }
                    return
                }
                legacy = signedSession != null && ext == null
                individualProtocol = ext?.optBoolean("conversation_context_v1") == true
                val audio = e.getJSONObject("audio_params")
                require(legacy || (ext != null && ext.getString("audio_envelope") == "rai1" && ext.getInt("contract_version") == 1 && (ext.optBoolean("mock") || individualProtocol))) {
                    "Backend chưa hỗ trợ giao thức RAI1"
                }
                require(audio.getString("format") == "opus" && audio.getInt("sample_rate") == (if (legacy || individualProtocol) 16000 else 24000) &&
                    audio.getInt("channels") == 1 && audio.getInt("frame_duration") == 60) { "Audio format chưa hỗ trợ" }
                needsFreshTransport = false
                player.configure(audio.getInt("sample_rate"))
                sid = e.getString("session_id")
                guard.reset(ext?.optLong("generation") ?: 0L)
                timeout?.cancel()
                update { it.copy(connected = true, connecting = false, phase = Phase.IDLE, message = if (individualProtocol) "Mô hình sẵn sàng · hội thoại riêng" else if(legacy) "Backend cũ · chạm micro để nói" else "Đã kết nối · STT/LLM/TTS giả lập") }
                if(sleepingRequest) {
                    listenAfterHello=false
                    if(individualProtocol) deliverSleepingRequest()
                    else { sleepingRequest=false; sleepingWakeConfirmed=false; resetPendingAudio(); listen() }
                } else if (listenAfterHello) { listenAfterHello = false; listen(manual=false) }
                return
            }
            if (e.optString("type") == "mcp") {
                if (e.optString("sessionId", e.optString("session_id")) != sid) return
                if(individualProtocol && e.has("generation") && !guard.accepts(e.optLong("generation"),e.optString("turn_id").toLongOrNull() ?: -1)) return
                robotTools.handle(e.getJSONObject("payload"), foreground && conversationVisible && voiceEnrollment == null && state.value.phase in listOf(Phase.FINALIZING, Phase.THINKING, Phase.SPEAKING))?.let { result ->
                    backend.sendText(JSONObject().put("type", "mcp").put("sessionId", sid).put("payload", result).toString())
                }
                return
            }
            if (!conversationVisible || voiceEnrollment != null) return
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
                "turn_ignored" -> { timeout?.cancel(); guard.cancel(); update { it.copy(phase=Phase.IDLE, message="Chưa xác định lời nói dành cho mình · hãy gọi $wakePhrase ơi") } }
                "utterance_end" -> finishInput(false)
                "stt" -> {
                    val final = e.getString("state") == "final"
                    if (guard.acceptText(generation, turn, e.getInt("revision"), final)) {
                        update { it.copy(caption = e.getString("text"), phase = if (final) Phase.THINKING else it.phase) }
                    }
                }
                "diagnostics" -> update { it.copy(serverFrames = e.getInt("uplink_frames"), decodedSamples = e.getInt("decoded_samples")) }
                "tts" -> when (e.getString("state")) {
                    "start" -> { musicPlayer.duck(true); player.start({
                        if (guard.accepts(generation, turn)) {
                            val latency = if (finishTime != 0L) SystemClock.elapsedRealtime() - finishTime else null
                            update { it.copy(phase = Phase.SPEAKING, latencyMs = latency, message = if (legacy || individualProtocol) "Đang phát phản hồi" else "Đang phát mẫu giọng Hải Đăng") }
                            if (!legacy) control("playback", "state" to "started")
                            beginInterruptionCapture(generation,turn)
                        }
                    }, {
                        if (guard.accepts(generation, turn)) {
                            if(interruptionCapture) { interruptionCapture=false;capturedWake.stop();identityState.value?.cancelTurn()
                                stopping=true;viewModelScope.launch { try { mic.stop() } finally { stopping=false } }
                                resetPendingAudio() }
                            timeout?.cancel()
                            recordCompletedTurn()
                            musicPlayer.duck(false)
                            interaction()
                            needsFreshTransport = legacy
                            update { it.copy(phase = Phase.IDLE, message = "Phát xong · sẵn sàng lượt mới") }
                            if (!legacy) control("playback", "state" to "completed", "audio_seq" to lastPlayedSequence)
                            if(individualProtocol) individualListening.playbackCompleted(SystemClock.elapsedRealtime())
                            guard.cancel()
                        }
                    }, { fail(it) }, { sequence ->
                        if(individualProtocol && guard.accepts(generation,turn)) {
                            lastPlayedSequence = sequence
                            runCatching { control("playback", "state" to "progress", "audio_seq" to sequence) }
                        }
                    }) }
                    "sentence_start" -> update { it.copy(reply = (it.reply + " " + e.getString("text")).trim(), spokenSentence = e.getString("text")) }
                    "stop" -> if (!player.finish()) fail("Backend không tạo được tiếng trả lời; hãy thử lại")
                }
                "llm" -> update { it.copy(speechEmotion = e.optString("emotion")) }
                "error" -> fail(e.optString("message", e.optString("code")))
            }
        } catch (e: Exception) { fail("Contract: ${e.message}") }
    }

    private fun receiveAudio(bytes: ByteArray) {
        if (!conversationVisible || !foreground || voiceEnrollment != null) return
        try {
            val frame = if (legacy) AudioFrame(guard.generation, guard.turn, incomingSequence++, bytes) else Rai1.decode(bytes)
            if (!guard.acceptAudio(frame)) { update { it.copy(discarded = it.discarded + 1) }; return }
            if (!player.offer(frame.payload, frame.seq)) { fail("Audio backlog: hãy kết nối lại"); return }
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
        capturedWake.stop()
        sleepingRequest=false; sleepingWakeConfirmed=false
        individualListening.reset()
        identityState.value?.clearTemporarySpeaker()
        resetPendingAudio()
        robotTools.reset()
        backend.close()
        player.stop()
        // ViewModel scope cancellation makes recorder exit and release in finally.
        super.onCleared()
    }
}
