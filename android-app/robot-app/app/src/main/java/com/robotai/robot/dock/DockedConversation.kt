package com.robotai.robot.dock

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.robotai.robot.companion.drawSocialHeart
import kotlinx.coroutines.delay
import kotlin.math.min

/** Foreground conversation uses the existing authenticated, hands-free audio owner. */
@Composable
fun DockedConversation(vm: RobotViewModel, session: UserSession, roleId: Int?, gaze: Float, onExit: () -> Unit,
    loving: Boolean = false, happy: Boolean = false) {
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
    Box(Modifier.fillMaxSize().background(Color(0xff030909)).testTag("dock-screen")) {
        DockFace(state.phase, gaze, Modifier.fillMaxSize().testTag("dock-face")
            .semantics { contentDescription = "Mặt Ken. Chạm để hiện điều khiển hội thoại." }
            .clickable {
                vm.interaction()
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

/** Soft asymmetric cyan eyes and a small mouth, scaled for a landscape phone on its stand. */
@Composable
fun DockFace(phase: Phase, gaze: Float, modifier: Modifier = Modifier, loving: Boolean = false, happy: Boolean = false, musicPlaying: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "dock-face")
    val blink by transition.animateFloat(1f, .07f,
        infiniteRepeatable(keyframes { durationMillis = 4800; 1f at 0; 1f at 4220; .07f at 4350; 1f at 4500 }), label = "dock-blink")
    val speech by transition.animateFloat(.85f, 1f, infiniteRepeatable(tween(230), RepeatMode.Reverse), label = "dock-speech")
    val look by animateFloatAsState(gaze.coerceIn(-1f, 1f), label = "dock-gaze")
    Canvas(modifier) {
        val unit = min(size.width / 2.15f, size.height)
        val w = unit * .36f
        val h = unit * .43f * if (phase == Phase.SLEEPING) .06f else blink
        val cx = size.width / 2 + look * unit * .045f
        val cy = size.height * .46f + if (musicPlaying) (speech - .925f) * unit * .25f else 0f
        val cyan = if (musicPlaying) lerp(Color(0xff67e8dc), Color(0xffee85d5), (speech - .85f) / .15f) else if (phase == Phase.SLEEPING) Color(0xff28565e) else Color(0xff55e1f3)
        for (side in listOf(-1, 1)) {
            val left = cx + side * unit * .30f - w / 2
            val top = cy - h / 2
            val eye = Path().apply {
                moveTo(left + w * .50f, top)
                cubicTo(left + w * .90f, top - h * .02f, left + w * 1.02f, top + h * .14f, left + w, top + h * .50f)
                cubicTo(left + w * .98f, top + h * .96f, left + w * .82f, top + h * 1.04f, left + w * .57f, top + h)
                cubicTo(left + w * .30f, top + h * .98f, left - w * .02f, top + h * .90f, left, top + h * .67f)
                cubicTo(left + w * .02f, top + h * .38f, left + w * .25f, top + h * .04f, left + w * .50f, top)
                close()
            }
            if (phase != Phase.SLEEPING) {
                // Layered blue shadow gives the glow in the reference without bitmap assets.
                for (layer in 7 downTo 1) {
                    val shift = unit * .005f * layer
                    val glow = Path().apply { addPath(eye, Offset(0f, shift)) }
                    drawPath(glow, Color(0xff2520ef).copy(alpha = .025f * (8 - layer)),
                        style = Stroke(unit * .009f * layer))
                }
            }
            if (loving && phase != Phase.SLEEPING)
                drawSocialHeart(Offset(left + w / 2, cy), w)
            else drawPath(eye, cyan)
        }
        val mouthY = cy + unit * .29f
        val mouth = Path().apply {
            moveTo(cx - unit * .018f, mouthY - unit * .02f)
            quadraticBezierTo(cx - unit * .044f, mouthY + unit * .01f, cx - unit * .016f, mouthY + unit * .012f)
            quadraticBezierTo(cx + unit * .018f, mouthY + unit * .015f,
                cx + unit * .025f, mouthY + unit * if (phase == Phase.SPEAKING) .045f * speech else .026f)
        }
        if ((happy || musicPlaying) && phase != Phase.SLEEPING) {
            drawArc(cyan, 10f, 160f, false, Offset(cx - unit * .08f, mouthY - unit * .03f),
                androidx.compose.ui.geometry.Size(unit * .16f, unit * .08f), style = Stroke(unit * .012f, cap = StrokeCap.Round))
        } else drawPath(mouth, cyan, style = Stroke(unit * .012f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (phase == Phase.LISTENING) drawCircle(Color(0xff8ef1dc), unit * .006f, Offset(cx, mouthY + unit * .08f))
        if (phase == Phase.THINKING || phase == Phase.FINALIZING) {
            for (i in -1..1) drawCircle(cyan.copy(alpha = .3f + speech * .4f), unit * .007f,
                Offset(cx + unit * .04f * i, mouthY + unit * .09f))
        }
    }
}
