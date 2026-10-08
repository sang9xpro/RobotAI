package com.robotai.robot

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Confirms the current app's unauthenticated handshake fails against real Java.
 * Passing this test confirms a blocker, not successful conversation E2E.
 */
@Ignore("Superseded by LoginFlowTest after implementing authenticated client")
class RealBackendCompatibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun currentAppCannotAuthenticateRealBackend() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            compose.onNodeWithTag("server-url").performScrollTo()
                .performTextReplacement("ws://127.0.0.1:8092/ws/xiaozhi/v1/")
            compose.onNodeWithTag("connect").performScrollTo().performClick()
            compose.waitUntil(15000) {
                compose.onNodeWithTag("status").fetchSemanticsNode().config[SemanticsProperties.Text]
                    .any { it.text.contains("401") }
            }
            val status = compose.onNodeWithTag("status").fetchSemanticsNode()
                .config[SemanticsProperties.Text].joinToString { it.text }
            File(context.cacheDir, "real-backend-compatibility.txt").writeText(status)
        } finally {
            context.getSharedPreferences("robot-test", 0).edit()
                .putString("url", "ws://127.0.0.1:8766/ws/xiaozhi/v1/").commit()
        }
    }
}
