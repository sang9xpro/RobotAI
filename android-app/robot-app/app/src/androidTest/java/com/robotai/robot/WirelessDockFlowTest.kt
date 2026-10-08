package com.robotai.robot

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.robot.auth.AuthApi
import com.robotai.robot.auth.SessionStore
import org.junit.*
import org.junit.Assert.*

/** Uses Android's battery service and isolated auth fixture; no real account or voice inference. */
class WirelessDockFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }
    private fun power(source: String) {
        shell("dumpsys battery unplug")
        if (source != "none") shell("dumpsys battery set $source 1")
    }
    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }
    private fun waitDock() {
        compose.waitUntil(15000) { compose.onAllNodesWithTag("dock-screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dock-face").assertIsDisplayed()
    }
    private fun waitNormal() {
        compose.waitUntil(15000) { compose.onAllNodesWithTag("account-name").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("dock-screen").assertCountEquals(0)
    }
    @Before fun setup() {
        power("none")
        shell("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
        val session = AuthApi().login("http://127.0.0.1:8891", "ws://127.0.0.1:8892/ws/xiaozhi/v1/", "fixture1", "fixture-only-password")
        SessionStore(context).save(session.json())
    }
    @After fun cleanup() {
        scenario?.close()
        shell("dumpsys battery reset")
        SessionStore(context).clear()
    }
    @Test fun wirelessSwitchesFaceAutoConnectsAndUndockStopsConversation() {
        launch(); waitNormal()
        power("usb"); compose.waitForIdle()
        compose.onAllNodesWithTag("dock-screen").assertCountEquals(0)
        power("ac"); compose.waitForIdle()
        compose.onAllNodesWithTag("dock-screen").assertCountEquals(0)
        power("wireless"); waitDock()
        lateinit var vm: RobotViewModel
        scenario!!.onActivity { vm = ViewModelProvider(it)[RobotViewModel::class.java] }
        compose.waitUntil(15000) { vm.state.value.connected }
        compose.waitUntil(15000) { vm.state.value.phase == com.robotai.domain.Phase.LISTENING }
        compose.waitUntil(5000) { context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        // Full battery remains on the stand even when it no longer actively charges.
        shell("dumpsys battery set status 5")
        compose.waitForIdle(); compose.onNodeWithTag("dock-face").assertIsDisplayed()
        // Capture app content, excluding Android's charging overlay and rotation animation.
        val screenshot = compose.onNodeWithTag("dock-screen").captureToImage().asAndroidBitmap()
        java.io.File(context.filesDir, "dock-preview.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        compose.onNodeWithTag("dock-face").performClick()
        compose.onNodeWithTag("dock-sleep").performClick()
        compose.waitUntil(5000) { vm.state.value.phase == com.robotai.domain.Phase.SLEEPING }
        compose.onNodeWithTag("dock-sleep").performClick()
        // Leave during the delayed wake: the old wake must not reconnect after leaving.
        power("none"); waitNormal()
        compose.waitUntil(5000) { !vm.state.value.connected && !vm.state.value.connecting }
        instrumentation.waitForIdleSync()
        scenario!!.onActivity { assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, it.requestedOrientation) }
        assertFalse(vm.state.value.connected)
    }
    @Test fun foregroundRestoreRefreshesPowerAndStopsHiddenMicrophone() {
        power("wireless"); launch(); waitDock()
        lateinit var vm: RobotViewModel
        scenario!!.onActivity { vm = ViewModelProvider(it)[RobotViewModel::class.java] }
        compose.waitUntil(15000) { vm.state.value.connected }
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        assertFalse(vm.state.value.connected)
        power("none")
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        waitNormal()
        compose.waitUntil(5000) { !vm.state.value.connected && !vm.state.value.connecting }
    }
    @Test fun launchAlreadyDockedExitStaysDismissedUntilNextPlacement() {
        power("wireless"); launch(); waitDock()
        compose.onNodeWithTag("dock-face").performClick()
        compose.onNodeWithTag("dock-exit").performClick(); waitNormal()
        shell("dumpsys battery set level 99")
        compose.waitForIdle(); compose.onAllNodesWithTag("dock-screen").assertCountEquals(0)
        power("none"); waitNormal()
        power("wireless"); waitDock()
        scenario!!.recreate(); waitDock()
    }
    @Test fun dockingDoesNotBypassLogin() {
        SessionStore(context).clear()
        power("wireless"); launch()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("login-submit").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("dock-screen").assertCountEquals(0)
    }
}
