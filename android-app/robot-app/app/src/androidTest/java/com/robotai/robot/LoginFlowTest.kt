package com.robotai.robot

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import com.robotai.robot.auth.SessionStore

/** Real Android UI + Keystore + HTTP/WebSocket transports; isolated server fixture accounts. */
class LoginFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var savedEndpoints: Pair<String?, String?> = null to null
    @Before fun launch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val endpoints = context.getSharedPreferences("login-endpoints", 0)
        savedEndpoints = endpoints.getString("api", null) to endpoints.getString("socket", null)
        SessionStore(context).clear()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }
    @After fun close() {
        scenario.close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SessionStore(context).clear()
        context.getSharedPreferences("login-endpoints", 0).edit()
            .putString("api", savedEndpoints.first?.takeUnless { it.endsWith(":8891") })
            .putString("socket", savedEndpoints.second?.takeUnless { it.contains(":8892/") }).commit()
    }
    private fun restartApp() {
        scenario.close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }
    private fun waitLogin() {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("login-submit").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("login-username").fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
        }
        compose.waitForIdle()
    }
    private fun login(user: String, password: String = "fixture-only-password") {
        waitLogin()
        compose.onNodeWithTag("login-api").performTextReplacement("http://127.0.0.1:8891")
        compose.onNodeWithTag("login-socket").performTextReplacement("ws://127.0.0.1:8892/ws/xiaozhi/v1/")
        compose.onNodeWithTag("login-username").performTextReplacement(user)
        compose.onNodeWithTag("login-password").performTextReplacement(password)
        compose.onNodeWithTag("login-submit").performScrollTo().performClick()
    }
    @Test fun firstLoginRestoreAuthenticatedSocketLogoutAndSwitchAccount() {
        login("fixture1")
        compose.waitUntil(15000) { compose.onAllNodesWithText("Fixture 1").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Role 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-Hội thoại").performClick()
        compose.onNodeWithTag("connect").performScrollTo().performClick()
        // Hands-free mode can move straight from connected to listening, replacing the status text.
        lateinit var robot: RobotViewModel
        scenario.onActivity { robot = androidx.lifecycle.ViewModelProvider(it)[RobotViewModel::class.java] }
        compose.waitUntil(10000) { robot.state.value.connected }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Assert.assertNotNull(SessionStore(context).load())
        val ciphertext = context.getSharedPreferences("robot-auth", 0).getString("session", "")!!
        Assert.assertFalse(ciphertext.contains("fixture-token"))
        restartApp()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Fixture 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("logout").performClick()
        waitLogin()
        Assert.assertNull(SessionStore(context).load())
        login("fixture2")
        compose.waitUntil(15000) { compose.onAllNodesWithText("Fixture 2").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Role 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Fixture 1").assertCountEquals(0)
        compose.onNodeWithTag("logout").performClick()
        waitLogin()
    }
    @Test fun expiredServerTokenReturnsToLoginAndClearsStoredSession() {
        login("fixture1")
        compose.waitUntil(15000) { compose.onAllNodesWithText("Fixture 1").fetchSemanticsNodes().isNotEmpty() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stored = com.robotai.robot.auth.UserSession.fromJson(SessionStore(context).load()!!)
        com.robotai.robot.auth.AuthApi().request(stored.baseUrl, "/api/user/logout", stored.token, "POST")
        restartApp()
        waitLogin()
        compose.onNodeWithTag("auth-status").assertTextContains("Phiên đã hết hạn", substring = true)
        Assert.assertNull(SessionStore(context).load())
    }
    @Test fun offlineRestoreKeepsEncryptedSessionForRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SessionStore(context).save(com.robotai.robot.auth.UserSession(
            "http://127.0.0.1:8899", "ws://127.0.0.1:8899/ws/xiaozhi/v1/", 1, "Offline fixture", "fixture-only-offline-token").json())
        restartApp()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("restore-session").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertNotNull(SessionStore(context).load())
        compose.onAllNodesWithTag("account-name").assertCountEquals(0)
        SessionStore(context).clear()
    }
    @Test fun wrongPasswordDoesNotPersistSession() {
        login("fixture1", "wrong")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Tài khoản hoặc mật khẩu không đúng").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertNull(SessionStore(InstrumentationRegistry.getInstrumentation().targetContext).load())
        compose.onAllNodesWithTag("account-name").assertCountEquals(0)
    }
}
