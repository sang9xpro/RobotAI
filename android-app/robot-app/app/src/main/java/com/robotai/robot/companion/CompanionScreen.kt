package com.robotai.robot.companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotai.domain.*
import com.robotai.robot.*
import com.robotai.robot.auth.UserSession
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable fun CompanionScreen(vm: RobotViewModel, session: UserSession, roleId: Int?, docked: Boolean = false, onExitDock: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    val music by vm.music.collectAsState()
    val identity by vm.identity.collectAsState()
    val awake by vm.awake.collectAsState()
    val recognition = identity ?: return
    val identityUi by recognition.state.collectAsState()
    val teachingUi by recognition.teaching.state.collectAsState()
    var front by remember(recognition) { mutableStateOf(recognition.prefs.getBoolean("front",true)) }
    val context = LocalContext.current
    val mind = remember { CompanionMind() }
    var tab by rememberSaveable { mutableStateOf("Ken") }
    val conversationVisible = docked || tab == "Ken" || tab == "Hội thoại"
    LaunchedEffect(vm, conversationVisible) { vm.setConversationScreen(conversationVisible) }
    DisposableEffect(vm) { onDispose { vm.setConversationScreen(false) } }
    LaunchedEffect(music.youtubeId) { if (music.youtubeId.isNotBlank()) tab = "Ken" }
    LaunchedEffect(awake,teachingUi.lesson.phase) {
        while(awake && teachingUi.lesson.phase !in listOf(LessonPhase.IDLE,LessonPhase.COMPLETE)) {
            vm.interaction();delay(15000)
        }
    }
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var wall by remember { mutableStateOf(System.currentTimeMillis()) }
    val reaction = identityUi.gestureReaction?.takeIf { awake && it.active(now) }
    val reactionName = identityUi.people.firstOrNull { it.id == reaction?.personId }?.name
    val knownReaction = reaction?.takeIf { reactionName != null }
    val visualReaction = knownReaction?.takeIf { state.phase !in listOf(Phase.SLEEPING, Phase.ERROR) }
    val loving = visualReaction?.kind?.loving == true
    var touchedAt by remember { mutableStateOf(-1L) }
    val face = identityUi.faces.isNotEmpty() || identityUi.trackedPeople.any { it.visible }
    val gaze = (identityUi.faces.firstOrNull()?.x ?: identityUi.trackedPeople.firstOrNull { it.visible }?.box?.cx?.let { it*2-1 })?.let { if(front) -it else it } ?: 0f
    var greeting by remember { mutableStateOf("Chào bạn, mình là Ken.") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) vm.listen() }
    LaunchedEffect(state.connected, conversationVisible) {
        if (conversationVisible && !docked && state.connected && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(awake) {
        val window = (context as android.app.Activity).window
        window.attributes = window.attributes.apply {
            screenBrightness = if (awake) android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else .03f
        }
    }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); wall = System.currentTimeMillis(); delay(200) } }
    LaunchedEffect(reaction?.atMs) {
        if (com.robotai.robot.BuildConfig.DEBUG) android.util.Log.d("RobotGestureUi", org.json.JSONObject()
            .put("awake",awake).put("docked",docked).put("displayed",reaction!=null && reactionName!=null)
            .put("kind",reaction?.kind?.name.orEmpty()).put("hasName",reactionName!=null)
            .put("ageMs",identityUi.gestureReaction?.let { SystemClock.elapsedRealtime()-it.atMs }).toString())
        if (reaction != null && reactionName != null) { mind.touch(SystemClock.elapsedRealtime()); vm.interaction() }
    }
    LaunchedEffect(state.spokenSentence, state.speechEmotion) {
        if (state.spokenSentence.isNotBlank()) mind.sentence(state.spokenSentence, now)
        if (state.speechEmotion.isNotBlank()) mind.emotion(state.speechEmotion)
    }
    val visualMood = VisualMoodPolicy.select(mind.mood(state.phase, now, face), state.phase,
        identityUi.facialExpression, now, awake, identityUi.expressionsEnabled && identityUi.enrollment==null && !identityUi.voiceBusy &&
            teachingUi.lesson.phase in listOf(LessonPhase.IDLE,LessonPhase.COMPLETE),
        recognition.hasRecentSpeech(now), directReaction = visualReaction != null || touchedAt>=0 && now-touchedAt in 0..3500)
    val mood = visualMood.mood
    val motionReaction = if (knownReaction != null && knownReaction.atMs >= touchedAt)
        KenReaction.valueOf(knownReaction.kind.name) else if (touchedAt >= 0L) KenReaction.TOUCH else null
    val motionAt = if (motionReaction == KenReaction.TOUCH) touchedAt else knownReaction?.atMs ?: -1L
    com.robotai.robot.motion.KenMotionHost(KenMotionInput(state.phase, mood, gaze,
        musicPlaying = music.phase == "playing", reaction = motionReaction, reactionAtMs = motionAt,
        visualMood = visualMood.fromCamera), vm.speechLevel,
        active = docked || tab in listOf("Ken", "Hội thoại", "Thị giác")) {
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Color(0xff08111b))) {
        com.robotai.robot.identity.CameraHost(recognition, awake, !docked && tab in listOf("Thị giác", "Người quen"), front, permissionButtonVisible = !docked)
        if (docked) {
            com.robotai.robot.dock.DockedConversation(vm, session, roleId, gaze, onExitDock, loving = loving, happy = visualReaction != null, onFaceTouch = { touchedAt = SystemClock.elapsedRealtime(); mind.touch(touchedAt) })
        } else {
            Text("${identityUi.status} · Người nói: ${identityUi.speaker}", color=Color.LightGray, style=MaterialTheme.typography.bodySmall)
            com.robotai.robot.identity.RecognizedPeopleStatus(identityUi)
            com.robotai.robot.identity.GestureReadinessStatus(identityUi, awake, Modifier.padding(horizontal=16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("Ken", "Hội thoại", "Thị giác", "Người quen", "Tiện ích", "Ký ức", "Đế robot").forEach { name ->
                    TextButton(onClick = {
                        vm.setConversationScreen(name == "Ken" || name == "Hội thoại")
                        tab = name
                    }, modifier = Modifier.testTag("tab-$name")) { Text(name, color = if (tab == name) Color.Cyan else Color.LightGray) }
                }
            }
            if (tab == "Hội thoại") RobotScreen(vm, session, roleId != null)
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    "Ken" -> {
                        Text(SimpleDateFormat("HH:mm · EEEE, dd/MM", Locale("vi", "VN")).format(Date(wall)), color = Color.LightGray)
                        CompanionFace(mood, state.phase, gaze, Modifier.fillMaxWidth().height(260.dp).testTag("companion-face").clickable {
                            touchedAt = SystemClock.elapsedRealtime(); mind.touch(touchedAt); vm.interaction(); greeting = "Mình đây! Rất vui được ở bên bạn."
                        }, loving = loving, musicPlaying = music.phase == "playing")
                        MusicPanel(music, vm::pauseMusic, vm::stopMusic, Modifier.fillMaxWidth(), vm::attachYouTube, vm::detachYouTube)
                        Text(when (mood) { Mood.HAPPY -> "Vui"; Mood.CURIOUS -> "Tò mò"; Mood.SLEEPY -> "Buồn ngủ"; Mood.CARING -> "Lắng nghe bạn"; else -> "Bình yên" }, color = Color.Cyan)
                        Text(state.reply.ifBlank { greeting }, color = Color.White)
                        Text(state.message, color = Color.LightGray)
                        Row {
                            Button(enabled = roleId != null && !state.connected && !state.connecting, onClick = { vm.connect(session.socketUrl) }) { Text("Kết nối") }
                            TextButton(enabled = state.connected && state.phase in listOf(Phase.IDLE, Phase.INTERRUPTED), onClick = {
                                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.listen()
                                else permission.launch(Manifest.permission.RECORD_AUDIO)
                            }) { Text("Nói với Ken") }
                        }
                        Row {
                            TextButton(enabled = state.phase == Phase.LISTENING, onClick = { vm.finish() }) { Text("Kết thúc sớm") }
                            TextButton(enabled = state.connected, onClick = { vm.interrupt() }) { Text("Ngắt lời") }
                            TextButton(onClick = { if (state.phase == Phase.SLEEPING) vm.wake() else {
                                vm.sleep(vm.wakePhrase, session.socketUrl)
                            } }) { Text(if (state.phase == Phase.SLEEPING) "Đánh thức" else "Ngủ") }
                        }
                        Text(vm.latencySummary(), color = Color.LightGray)
                        Text("Cứ nói với Ken khi đang thức. Tự ngủ sau 2 phút yên lặng; gọi “${vm.wakePhrase} ơi” để đánh thức.", color = Color.Gray)
                    }
                    "Thị giác" -> {
                        CompanionFace(mood, state.phase, gaze, Modifier.fillMaxWidth().height(100.dp), loving = loving)
                        com.robotai.robot.identity.ExpressionReadinessStatus(identityUi,awake,now)
                        VisionPanel(session, recognition, front) { front=it; recognition.prefs.edit().putBoolean("front",it).apply() }
                    }
                    "Người quen" -> com.robotai.robot.identity.PeoplePanel(recognition, awake && !state.connecting && state.phase in listOf(Phase.IDLE,Phase.INTERRUPTED), vm::enrollVoice, vm::cancelVoiceEnrollment,session,roleId,vm::disconnect,vm::deletePerson,awake=awake)

                    "Tiện ích" -> UtilityPanel(session)
                    "Ký ức" -> MemoryPanel(session, roleId, vm::disconnect)
                    "Đế robot" -> RobotLinkPanel(vm.robotTools)
                }
            }
        }
    }
    if(docked) com.robotai.robot.identity.GestureReadinessStatus(identityUi, awake,
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal=24.dp,vertical=8.dp)
            .background(Color(0xd9030909), MaterialTheme.shapes.small).padding(horizontal=12.dp,vertical=6.dp))
    GestureEffects(visualReaction, reactionName)
    }
    }
}

/** Shared Ken renderer; the enclosing host owns the actual reaction and speech meter. */
@Composable fun CompanionFace(mood: Mood, phase: Phase, gaze: Float, modifier: Modifier,
    loving: Boolean = false, musicPlaying: Boolean = false) {
    val loveAt = remember(loving) { if (loving) SystemClock.elapsedRealtime() else -1L }
    com.robotai.robot.motion.KenFace(KenMotionInput(phase = phase, mood = mood, gaze = gaze,
        musicPlaying = musicPlaying, reaction = if (loving) KenReaction.FINGER_HEART else null,
        reactionAtMs = loveAt), modifier)
}

@Composable fun RobotLinkPanel(tools: DeviceRobotTools) {
    val transport = tools.transport
    var message by remember { mutableStateOf("Đế mô phỏng · chưa kết nối ESP32") }
    var connected by remember { mutableStateOf(true) }
    var frozen by remember { mutableStateOf(false) }
    var cliff by remember { mutableStateOf(false) }
    var telemetry by remember { mutableStateOf(transport.telemetry(SystemClock.elapsedRealtime())) }
    var action by remember { mutableStateOf(RobotAction.STOP) }
    LaunchedEffect(Unit) { while (true) { telemetry = tools.tick(); action = transport.action; delay(100) } }
    DisposableEffect(Unit) { onDispose { tools.stop() } }
    var proposals by remember { mutableStateOf(tools.enabled) }
    Row { Switch(proposals, { proposals = it; tools.enabled = it }); Text("Cho phép đề xuất robot từ server") }
    Text("Mô phỏng cổng lệnh và telemetry", style = MaterialTheme.typography.titleLarge)
    Text("Trạng thái: $action · pin ${telemetry.battery}%\n$message")
    Row { Switch(connected, { connected = it; transport.connected = it }); Text("Có liên lạc") }
    Row { Switch(frozen, { frozen = it; transport.frozen = it }); Text("Đóng băng telemetry") }
    Row { Switch(cliff, { cliff = it; transport.cliff = it }); Text("Cảm biến mép bàn") }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        RobotAction.values().forEach { a -> TextButton(onClick = {
            message = tools.local(a)
        }) { Text(a.name) } }
    }
    Button(onClick = { message = tools.local(RobotAction.FORWARD, expired = true) }) { Text("Thử lệnh hết hạn") }
    Text("UI hoặc LLM chỉ được đề xuất hành động có tên qua RobotLink. STOP luôn được ưu tiên; mất telemetry 750 ms thì dừng. Đây chưa phải kiểm thử motor thật.")
}
