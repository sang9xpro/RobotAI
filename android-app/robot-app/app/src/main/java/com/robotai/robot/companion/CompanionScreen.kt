package com.robotai.robot.companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
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
    var tab by remember { mutableStateOf("Ken") }
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
    val loving = reaction?.kind?.loving == true && reactionName != null
    val face = identityUi.faces.isNotEmpty() || identityUi.trackedPeople.any { it.visible }
    val gaze = (identityUi.faces.firstOrNull()?.x ?: identityUi.trackedPeople.firstOrNull { it.visible }?.box?.cx?.let { it*2-1 })?.let { if(front) -it else it } ?: 0f
    var greeting by remember { mutableStateOf("Chào bạn, mình là Ken.") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) vm.listen() }
    LaunchedEffect(state.connected) {
        if (!docked && state.connected && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
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
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Color(0xff08111b))) {
        com.robotai.robot.identity.CameraHost(recognition, awake, !docked && tab in listOf("Thị giác", "Người quen"), front, permissionButtonVisible = !docked)
        if (docked) {
            com.robotai.robot.dock.DockedConversation(vm, session, roleId, gaze, onExitDock, loving = loving, happy = reaction != null)
        } else {
            Text("${identityUi.status} · Người nói: ${identityUi.speaker}", color=Color.LightGray, style=MaterialTheme.typography.bodySmall)
            com.robotai.robot.identity.RecognizedPeopleStatus(identityUi)
            com.robotai.robot.identity.GestureReadinessStatus(identityUi, awake, Modifier.padding(horizontal=16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("Ken", "Hội thoại", "Thị giác", "Người quen", "Tiện ích", "Ký ức", "Đế robot").forEach { name ->
                    TextButton(onClick = { tab = name }, modifier = Modifier.testTag("tab-$name")) { Text(name, color = if (tab == name) Color.Cyan else Color.LightGray) }
                }
            }
            if (tab == "Hội thoại") RobotScreen(vm, session, roleId != null)
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    "Ken" -> {
                        Text(SimpleDateFormat("HH:mm · EEEE, dd/MM", Locale("vi", "VN")).format(Date(wall)), color = Color.LightGray)
                        val mood = if (reaction != null) Mood.HAPPY else mind.mood(state.phase, now, face)
                        CompanionFace(mood, state.phase, gaze, Modifier.fillMaxWidth().height(260.dp).testTag("companion-face").clickable {
                            mind.touch(now); vm.interaction(); greeting = "Mình đây! Rất vui được ở bên bạn."
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
                        CompanionFace(if (reaction != null) Mood.HAPPY else mind.mood(state.phase, now, face), state.phase, gaze, Modifier.fillMaxWidth().height(100.dp), loving = loving)
                        VisionPanel(session, recognition, front) { front=it; recognition.prefs.edit().putBoolean("front",it).apply() }
                    }
                    "Người quen" -> com.robotai.robot.identity.PeoplePanel(recognition, awake && state.phase in listOf(Phase.IDLE,Phase.INTERRUPTED,Phase.LISTENING), vm::enrollVoice, vm::cancelVoiceEnrollment)

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
    GestureEffects(reaction, reactionName)
    }
}

@Composable fun CompanionFace(mood: Mood, phase: Phase, gaze: Float, modifier: Modifier, loving: Boolean = false, musicPlaying: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "face")
    val blink by transition.animateFloat(1f, .08f, infiniteRepeatable(keyframes { durationMillis = 4200; 1f at 0; 1f at 3700; .08f at 3850; 1f at 4000 }), label = "blink")
    val speech by transition.animateFloat(.85f, 1f, infiniteRepeatable(tween(200), RepeatMode.Reverse), label = "speech")
    val look by animateFloatAsState(gaze.coerceIn(-1f,1f), label = "look")
    Canvas(modifier) {
        val color = if (musicPlaying) androidx.compose.ui.graphics.lerp(Color(0xff67e8dc), Color(0xffee85d5), (speech - .85f) / .15f) else if (phase == Phase.ERROR) Color(0xffff9988) else when(mood) {
            Mood.HAPPY -> Color(0xff79f4ac); Mood.CARING -> Color(0xffffca99); Mood.SLEEPY -> Color(0xff466775); else -> Color(0xff34d4de)
        }
        val w = size.width * .25f
        val baseH = size.height * when(mood) { Mood.SLEEPY -> .04f; Mood.HAPPY -> .22f; Mood.CARING -> .28f; else -> .4f }
        val h = baseH * blink * if (phase == Phase.SPEAKING || musicPlaying) speech else 1f
        for ((i, x) in listOf(size.width*.17f, size.width*.59f).withIndex()) {
            val musicSway = if (musicPlaying) (speech - .925f) * size.height * .3f else 0f
            val offset = musicSway + if (mood == Mood.CURIOUS && i == 1) -size.height*.05f else 0f
            if (loving) drawSocialHeart(Offset(x + w / 2 + look * size.width * .07f, size.height / 2), minOf(w, size.height * .55f))
            else drawRoundRect(color, Offset(x + look * size.width*.07f, (size.height-h)/2+offset), Size(w,h), CornerRadius(w*.35f))
        }
        if (mood == Mood.HAPPY) drawArc(color, 10f, 160f, false, Offset(size.width*.43f,size.height*.64f), Size(size.width*.14f,size.height*.1f), style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
    }
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
