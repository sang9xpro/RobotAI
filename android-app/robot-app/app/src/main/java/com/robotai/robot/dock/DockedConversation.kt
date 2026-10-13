package com.robotai.robot.dock

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.robotai.domain.Phase
import com.robotai.robot.RobotViewModel
import com.robotai.robot.auth.UserSession
import kotlinx.coroutines.delay

/** Foreground conversation uses the existing authenticated, hands-free audio owner. */
@Composable
fun DockedConversation(vm: RobotViewModel, session: UserSession, roleId: Int?, gaze: Float, onExit: () -> Unit,
    loving: Boolean = false, happy: Boolean = false, onFaceTouch: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    val music by vm.music.collectAsState()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var allowed by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var permissionRequested by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(0) }
    val latestRole by rememberUpdatedState(roleId)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (granted && latestRole != null && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (state.phase == Phase.SLEEPING) vm.wake() else vm.connect(session.socketUrl)
            entered = true
        }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            allowed = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(vm) {
        vm.cancelVoiceEnrollment()
        onDispose { vm.disconnect() }
    }
    LaunchedEffect(roleId, resumed, allowed) {
        if (roleId != null && resumed) {
            if (allowed) {
                if (!entered && state.phase == Phase.SLEEPING) vm.wake()
                else if (state.phase != Phase.SLEEPING) vm.connect(session.socketUrl)
                entered = true
            } else if (!permissionRequested) {
                permissionRequested = true
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    LaunchedEffect(controls, touch) { if (controls) { delay(6000); controls = false } }
    BackHandler(onBack = onExit)
    val needsHelp = roleId == null || !allowed || state.phase == Phase.ERROR ||
        (!state.connected && !state.connecting && state.phase != Phase.SLEEPING)
    Box(Modifier.fillMaxSize().background(com.robotai.robot.motion.KenColors.Background).testTag("dock-screen")) {
        DockFace(state.phase, gaze, Modifier.fillMaxSize().testTag("dock-face")
            .semantics { contentDescription = "Mặt Ken. Chạm để hiện điều khiển hội thoại." }
            .clickable {
                vm.interaction()
                if (state.phase != Phase.SLEEPING) onFaceTouch()
                if (state.phase == Phase.SLEEPING && roleId != null && allowed) vm.wake()
                controls = !controls; touch++
            }, loving = loving, happy = happy, musicPlaying = music.phase == "playing")
        com.robotai.robot.companion.MusicPanel(music, vm::pauseMusic, vm::stopMusic, Modifier.align(Alignment.TopEnd).width(270.dp).padding(12.dp), vm::attachYouTube, vm::detachYouTube)
        if (controls || needsHelp) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xe6030909))
                .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when {
                    roleId == null -> "Chọn cấu hình robot trong màn hình tài khoản để trò chuyện."
                    !allowed -> "Cho phép micro để nói chuyện với Ken."
                    else -> state.message
                }, color = Color(0xffb2ced2), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("dock-status"))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!allowed && roleId != null) TextButton(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.testTag("dock-microphone")) { Text("Cho phép micro") }
                    else if (roleId != null) {
                        if (!state.connected && !state.connecting && state.phase != Phase.SLEEPING)
                            TextButton(onClick = { vm.connect(session.socketUrl) }, modifier = Modifier.testTag("dock-reconnect")) { Text("Kết nối lại") }
                        TextButton(onClick = {
                            touch++
                            if (state.phase == Phase.SLEEPING) vm.wake() else vm.sleep(vm.wakePhrase, session.socketUrl)
                        }, modifier = Modifier.testTag("dock-sleep")) { Text(if (state.phase == Phase.SLEEPING) "Đánh thức" else "Ngủ") }
                        if (state.phase == Phase.SPEAKING || state.phase == Phase.THINKING || state.phase == Phase.FINALIZING)
                            TextButton(onClick = { touch++; vm.interrupt() }) { Text("Ngắt lời") }
                    }
                    TextButton(onClick = onExit, modifier = Modifier.testTag("dock-exit")) { Text("Thoát chế độ đế") }
                }
            }
        }
    }
}

/** Same geometry and clock as Ken and the conversation screen. */
@Composable
fun DockFace(phase: Phase, gaze: Float, modifier: Modifier = Modifier, loving: Boolean = false,
    happy: Boolean = false, musicPlaying: Boolean = false) {
    val loveAt = remember(loving) { if(loving) android.os.SystemClock.elapsedRealtime() else -1L }
    com.robotai.robot.motion.KenFace(com.robotai.domain.KenMotionInput(phase = phase,
        mood = if(happy) com.robotai.domain.Mood.HAPPY else com.robotai.domain.Mood.CALM,
        gaze = gaze, musicPlaying = musicPlaying,
        reaction = if(loving) com.robotai.domain.KenReaction.FINGER_HEART else null,
        reactionAtMs = loveAt), modifier)
}
