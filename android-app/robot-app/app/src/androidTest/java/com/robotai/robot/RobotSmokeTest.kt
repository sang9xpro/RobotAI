package com.robotai.robot

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import android.content.Intent
import org.junit.Before
import org.junit.After
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class RobotSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    @Before fun launchMock() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("robotai.mockTest", true))
    }
    @After fun close() { scenario.close() }
    @Test fun mockAudioAndLocalSleepLifecycle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("pm grant com.robotai.robot android.permission.RECORD_AUDIO").close()
        compose.onNodeWithTag("connect").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Đã kết nối · STT/LLM/TTS giả lập").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("listen").performScrollTo().performClick()
        Thread.sleep(1500)
        compose.onNodeWithTag("finish").performScrollTo().performClick()
        compose.waitUntil(12000) { compose.onAllNodesWithText("Đang phát mẫu giọng Hải Đăng").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("interrupt").performScrollTo().performClick()
        compose.waitUntil(3000) { compose.onAllNodesWithText("Đã ngắt · audio/text cũ sẽ bị loại").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("caption").assertTextContains("[GIẢ LẬP]", substring = true)
        compose.onNodeWithTag("sleep").performScrollTo().performClick()
        compose.onNodeWithTag("sleep-status").assertTextEquals("Ken đang ngủ")
        compose.onNodeWithTag("wake").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Đã kết nối · STT/LLM/TTS giả lập").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("listen").performScrollTo().performClick()
        Thread.sleep(1000)
        compose.onNodeWithTag("finish").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Phát xong · sẵn sàng lượt mới").fetchSemanticsNodes().isNotEmpty() }
    }
}
