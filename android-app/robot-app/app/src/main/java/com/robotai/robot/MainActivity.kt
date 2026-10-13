package com.robotai.robot

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robotai.domain.Phase
import com.robotai.robot.auth.*

class MainActivity : ComponentActivity() {
    private var robot: RobotViewModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xff34d4de))) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    val vm: RobotViewModel = viewModel()
                    robot = vm
                    val auth: AuthViewModel = viewModel()
                    val account by auth.state.collectAsState()
                    val mockTest = BuildConfig.DEBUG && intent.getBooleanExtra("robotai.mockTest", false)
                    val wirelessDock by com.robotai.robot.dock.rememberWirelessDock()
                    var dockDismissed by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(wirelessDock) { if (!wirelessDock) dockDismissed = false }
                    val dockMode = wirelessDock && !dockDismissed && account.session != null && !mockTest
                    com.robotai.robot.dock.DockWindow(this@MainActivity, dockMode)
                    LaunchedEffect(account.session, account.roleId) {
                        if (!mockTest) vm.setSession(account.session, account.roleId)

                    }
                    LaunchedEffect(account.session, account.busy, account.canRestore) {
                        if (account.session != null || (!account.busy && !account.canRestore))
                            com.robotai.robot.companion.LocalCompanionStore.activate(this@MainActivity, account.session)
                    }
                    vm.onAuthExpired = { auth.expire() }
                    if (mockTest) RobotScreen(vm)
                    else if (account.session == null) LoginScreen(auth)
                    else Column(Modifier.fillMaxSize()) {
                        if (!dockMode) AccountPanel(auth) { vm.disconnect() }
                        Box(Modifier.weight(1f)) { key(account.session!!.baseUrl, account.session!!.userId, account.roleId) {
                            com.robotai.robot.companion.CompanionScreen(vm, account.session!!, account.roleId,
                                docked = dockMode, onExitDock = { dockDismissed = true })
                        } }
                    }
                }
            }
        }
    }
    override fun onStart() { super.onStart(); robot?.resumeForeground() }
    override fun onStop() {
        robot?.pauseForeground() // Never retain a hidden microphone.
        super.onStop()
    }
}

@Composable
fun RobotScreen(vm: RobotViewModel, session: UserSession? = null, configured: Boolean = true) {
    val state by vm.state.collectAsState()
    com.robotai.robot.motion.KenMotionHostIfNeeded(com.robotai.domain.KenMotionInput(phase = state.phase), vm.speechLevel) {
        RobotScreenContent(vm, session, configured)
    }
}

@Composable
private fun RobotScreenContent(vm: RobotViewModel, session: UserSession?, configured: Boolean) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(if (session == null) "robot-test" else
        "robot-user-" + java.security.MessageDigest.getInstance("SHA-256").digest((session.baseUrl + ":" + session.userId).toByteArray())
            .joinToString("") { "%02x".format(it) }, 0) }
    var url by remember { mutableStateOf(prefs.getString("url", session?.socketUrl ?: "ws://127.0.0.1:8766/ws/xiaozhi/v1/")!!) }
    var wakePhrase by remember { mutableStateOf(prefs.getString("wake_phrase", "Ken")!!) }
    var permissionForSleep by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (permissionForSleep) vm.sleep(wakePhrase, url) else vm.listen()
        }
    }
    LaunchedEffect(state.phase) {
        val window = (context as Activity).window
        val params = window.attributes
        params.screenBrightness = if (state.phase == Phase.SLEEPING) .03f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = params
    }
    if (state.phase == Phase.SLEEPING) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(24.dp), verticalArrangement = Arrangement.Center) {
            RobotEyes(Phase.SLEEPING, Modifier.fillMaxWidth().height(130.dp))
            Text("Ken đang ngủ", color = Color(0xff65777f), modifier = Modifier.testTag("sleep-status"))
            Text("Gọi “$wakePhrase” hoặc “$wakePhrase ơi” để đánh thức.\n${state.message}", color = Color(0xff65777f))
            OutlinedButton(onClick = { vm.wake() }, modifier = Modifier.testTag("wake")) { Text("Đánh thức") }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(Color(0xff08111b)).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("RobotAI · Hội thoại", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(if (session == null) "BACKEND GIẢ LẬP · chưa nhận dạng câu bạn nói" else "Hội thoại và cấu hình theo tài khoản", color = Color(0xffffce70), modifier = Modifier.padding(top = 6.dp))
        RobotEyes(state.phase, Modifier.fillMaxWidth().height(210.dp).testTag("robot-eyes"))
        Text(state.message, color = Color.White, modifier = Modifier.testTag("status"))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(url, { url = it }, label = { Text("WebSocket backend") }, singleLine = true,
            enabled = session == null && !state.connected && !state.connecting, modifier = Modifier.fillMaxWidth().testTag("server-url"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { prefs.edit().putString("url", url).apply(); vm.connect(url) }, enabled = configured && !state.connected && !state.connecting,
                modifier = Modifier.testTag("connect")) { Text("Kết nối") }
            OutlinedButton(onClick = vm::disconnect, enabled = state.connected || state.connecting) { Text("Ngắt kết nối") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                permissionForSleep = false
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.listen()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            }, enabled = state.connected && state.phase in listOf(Phase.IDLE, Phase.INTERRUPTED), modifier = Modifier.testTag("listen")) { Text("Nghe") }
            Button(onClick = { vm.finish() }, enabled = state.phase == Phase.LISTENING, modifier = Modifier.testTag("finish")) { Text("Kết thúc câu") }
            OutlinedButton(onClick = vm::interrupt, enabled = state.connected && state.phase !in listOf(Phase.IDLE, Phase.INTERRUPTED),
                modifier = Modifier.testTag("interrupt")) { Text("Ngắt") }
        }
        OutlinedTextField(wakePhrase, { wakePhrase = it; vm.configureWakePhrase(it) }, label = { Text("Tên đánh thức local") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("wake-phrase"))
        OutlinedButton(onClick = {
            prefs.edit().putString("wake_phrase", wakePhrase.trim()).apply()
            permissionForSleep = true
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.sleep(wakePhrase, url)
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }, enabled = wakePhrase.isNotBlank(), modifier = Modifier.testTag("sleep")) { Text("Cho Ken ngủ") }
        Text("Nghe tên: Android on-device (bản thử). Cần máy và gói tiếng Việt hỗ trợ; chưa phải model từ khóa tiết kiệm pin.", color = Color(0xff98abc0))
        Text(if (session == null) "Phụ đề giả lập" else "Bạn nói", style = MaterialTheme.typography.titleMedium, color = Color(0xff34d4de))
        Text(state.caption.ifBlank { "Bạn cứ nói; Ken tự kết thúc câu sau khoảng lặng và nghe tiếp khi trả lời xong." }, color = Color.White,
            modifier = Modifier.padding(vertical = 10.dp).testTag("caption"))
        Text(state.reply, color = Color(0xffbbcbdc), modifier = Modifier.testTag("reply"))
        HorizontalDividerCompat()
        Text("Audio ↑ ${state.uploaded} frame · ↓ ${state.downloaded} frame\nServer giải mã ${state.serverFrames} frame / ${state.decodedSamples} sample\nDữ liệu cũ đã loại: ${state.discarded}\nKết thúc câu → bắt đầu phát: ${state.latencyMs?.let { "$it ms" } ?: "—"}",
            color = Color(0xff98abc0), modifier = Modifier.testTag("diagnostics"))
        Text("Đế: mô phỏng\nMicro dừng khi app vào nền. Backend có thể lưu lịch sử và audio hội thoại theo cấu hình.",
            color = Color(0xff98abc0), modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun HorizontalDividerCompat() { Divider(Modifier.padding(vertical = 16.dp)) }

@Composable
fun RobotEyes(phase: Phase, modifier: Modifier = Modifier) {
    com.robotai.robot.motion.KenFace(com.robotai.domain.KenMotionInput(phase = phase), modifier)
}
