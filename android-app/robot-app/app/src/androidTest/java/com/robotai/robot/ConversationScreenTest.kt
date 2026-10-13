package com.robotai.robot

import android.media.AudioManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.domain.Phase
import com.robotai.robot.auth.UserSession
import com.robotai.robot.companion.CompanionScreen
import com.robotai.robot.companion.LocalCompanionStore
import com.robotai.robot.identity.PeopleStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Uses the isolated auth WebSocket on 8892; never changes the user's saved login. */
class ConversationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun settingsReleaseConversationMicrophoneAndVoiceEnrollmentCannotRestartChat() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.RECORD_AUDIO").close()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val session = UserSession("http://127.0.0.1:8891/screen-test-${UUID.randomUUID()}",
            "ws://127.0.0.1:8892/ws/xiaozhi/v1/", 1, "Screen fixture", "fixture-token-1")
        lateinit var vm: RobotViewModel
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity)[RobotViewModel::class.java]
            vm.setSession(session, 1)
        }
        val controller = vm.identity.value!!
        val store = PeopleStore(context, LocalCompanionStore.key(session))
        try {
            controller.add("Người thử màn hình", "bạn")
            compose.waitUntil(10000) { controller.state.value.people.size == 1 }
            compose.setContent { MaterialTheme { CompanionScreen(vm, session, 1) } }
            waitListening(vm)
            compose.waitUntil(5000) { audio.activeRecordingConfigurations.isNotEmpty() }

            tab("Người quen")
            waitStopped(vm, audio)
            compose.runOnUiThread { vm.connect(session.socketUrl); vm.listen() }
            Thread.sleep(600)
            assertTrue(vm.state.value.connected)

            controller.error("Có đoạn quá ngắn để xác định người nói")
            compose.waitUntil(5000) { controller.state.value.error != null }
            compose.onNodeWithText("Thêm mẫu giọng").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(5000) { controller.state.value.voiceBusy }
            compose.waitUntil(5000) { controller.state.value.error == null && controller.state.value.voiceSeconds > .2f }
            compose.waitUntil(5000) { audio.activeRecordingConfigurations.isNotEmpty() }
            Thread.sleep(1000) // Cross several automatic listen ticks while enrollment owns AudioRecord.
            assertTrue(vm.state.value.connected)
            assertEquals(Phase.IDLE, vm.state.value.phase)
            assertTrue(controller.state.value.voiceBusy)
            compose.onNodeWithText("Hủy thu giọng").performScrollTo().performClick()
            compose.waitUntil(5000) { !controller.state.value.voiceBusy }
            waitStopped(vm, audio)

            // Returning during a fresh capture must cancel/join its owner before listening resumes.
            compose.onNodeWithText("Thêm mẫu giọng").performScrollTo().performClick()
            compose.waitUntil(5000) { controller.state.value.voiceBusy }
            tab("Hội thoại")
            waitListening(vm)
            assertFalse(controller.state.value.voiceBusy)
            assertNull(controller.state.value.error)

            for (name in listOf("Thị giác", "Người quen", "Tiện ích", "Ký ức", "Đế robot")) {
                tab(name)
                waitStopped(vm, audio)
                compose.runOnUiThread { vm.pauseForeground(); vm.resumeForeground(); vm.listen() }
                Thread.sleep(400)
                waitStopped(vm, audio)
                tab("Ken")
                waitListening(vm)
            }
            // A delayed reconnect must not outlive a second exit.
            tab("Người quen"); tab("Ken"); tab("Người quen")
            Thread.sleep(1000)
            waitStopped(vm, audio)
            tab("Ken")
            waitListening(vm)
        } finally {
            compose.runOnUiThread { vm.disconnect() }
            compose.waitUntil(5000) { !controller.state.value.voiceBusy }
            store.write(emptyList())
        }
    }

    private fun tab(name: String) = compose.onNodeWithTag("tab-$name").performScrollTo().performClick()
    private fun waitListening(vm: RobotViewModel) {
        compose.waitUntil(15000) { vm.state.value.connected && vm.state.value.phase == Phase.LISTENING }
    }
    private fun waitStopped(vm: RobotViewModel, audio: AudioManager) {
        compose.waitUntil(10000) { vm.state.value.phase == Phase.IDLE && audio.activeRecordingConfigurations.isEmpty() }
    }
}
