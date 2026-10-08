package com.robotai.robot

import android.content.Intent
import android.media.MediaPlayer
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.domain.*
import com.robotai.robot.auth.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Physical microphone + real providers. Never calls listen() or finish(). */
class HandsFreeAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var vm: RobotViewModel
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(timeout: Long = 120000, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) {
            if (::vm.isInitialized && vm.state.value.phase == Phase.ERROR) fail(vm.state.value.message)
            if (predicate()) return
            Thread.sleep(20)
        }
        fail("Timeout: ${if (::vm.isInitialized) vm.state.value.message else "initialization"}")
    }
    private fun playInput() {
        if (InstrumentationRegistry.getArguments().getString("externalAudio") == "true") {
            val request = UUID.randomUUID().toString()
            val ack = File(context.filesDir, "acceptance-audio.done").apply { delete() }
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "PLAY_INPUT:$request\n") })
            waitFor(20000) { ack.exists() && ack.readText().startsWith(request) }
            assertEquals("External speaker failed", "$request:ok", ack.readText())
        } else {
            val player = MediaPlayer().apply { setDataSource(File(context.filesDir, "acceptance.wav").absolutePath); prepare() }
            try { player.start(); Thread.sleep(player.duration.toLong() + 300) } finally { player.release() }
        }
    }
    @Test fun wakeStreamsUntilSilenceRepliesAndListensAgain() {
        val fixture = JSONObject(File(context.filesDir, "acceptance.json").readText())
        val account = fixture.getJSONArray("accounts").getJSONObject(0)
        val api = AuthApi()
        val session = api.login(fixture.getString("api"), fixture.getString("socket"), account.getString("username"), account.getString("password"))
        val role = account.getJSONArray("roles").getInt(0)
        api.request(session.baseUrl, "/api/user/robot-profile/role", session.token, "PUT", JSONObject().put("roleId", role))
        val store = SessionStore(context)
        val previousSession = store.load()
        store.save(session.json())
        val report = JSONObject().put("scope", "manual wake; physical microphone; automatic speech endpoint; real STT/LLM/TTS")
            .put("voiceWakeTested", false).put("turns", JSONArray())
        val output = File(context.filesDir, "acceptance-result.json")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val snapshots = java.util.Collections.synchronizedList(mutableListOf<RobotUiState>())
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            lateinit var auth: AuthViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[RobotViewModel::class.java]; auth = ViewModelProvider(it)[AuthViewModel::class.java] }
            waitFor(15000) { auth.state.value.roleId == role && vm.state.value.message == "Sẵn sàng kết nối" }
            main { scope.launch { vm.state.collect { snapshots.add(it) } }; vm.sleep("Ken", session.socketUrl) }
            waitFor(5000) { vm.state.value.phase == Phase.SLEEPING }
            Thread.sleep(2000)
            report.put("sleepStatus", vm.state.value.message)
            assertFalse(vm.state.value.connected)
            main { vm.wake() }
            waitFor(15000) { vm.state.value.connected && vm.state.value.phase == Phase.LISTENING }
            Thread.sleep(2000)
            val quietFrames = vm.state.value.uploaded
            report.put("wakeToListening", true).put("ambientUplinkFramesBeforeFixture", quietFrames)
                .put("ambientQuietCheckPassed", quietFrames == 0)
            // Keep testing the complete flow even if ambient sound triggered a turn.
            // Retain that failure and assert it after recording the voice results.
            if (quietFrames > 0) {
                main { vm.sleep("Ken", session.socketUrl) }
                Thread.sleep(1000)
                main { vm.wake() }
                waitFor(15000) { vm.state.value.connected && vm.state.value.phase == Phase.LISTENING }
            }
            val count = InstrumentationRegistry.getArguments().getString("turns", "2")!!.toInt()
            repeat(count) { index ->
                snapshots.clear()
                playInput()
                waitFor(20000) { vm.state.value.caption.isNotBlank() }
                val captured = synchronized(snapshots) { snapshots.toList() }
                report.put("lastInput", JSONObject().put("caption", vm.state.value.caption)
                    .put("streamedWhileListening", captured.any { it.phase == Phase.LISTENING && it.uploaded > 0 })
                    .put("automaticEnd", captured.any { it.phase == Phase.FINALIZING }))
                output.writeText(report.toString(2))
                var completed: RobotUiState? = null
                waitFor {
                    completed = synchronized(snapshots) { snapshots.firstOrNull { it.phase == Phase.IDLE && it.downloaded > 0 && it.latencyMs != null } }
                    completed != null
                }
                val states = synchronized(snapshots) { snapshots.toList() }
                val end = completed!!
                assertTrue("No audio streamed while listening", states.any { it.phase == Phase.LISTENING && it.uploaded > 0 })
                assertTrue("No automatic endpoint", states.any { it.phase == Phase.FINALIZING })
                assertTrue("No playback", states.any { it.phase == Phase.SPEAKING })
                val transcript = end.caption.lowercase()
                assertTrue("Wrong STT: $transcript", listOf("dung lượng", "lưu trữ", "ram", "bộ nhớ", "tính toán").count { it in transcript } >= 2)
                assertTrue("Wrong response role: ${end.reply}", end.reply.startsWith("KENA"))
                assertTrue(end.uploaded > 0 && end.downloaded > 0)
                report.getJSONArray("turns").put(JSONObject().put("caption", end.caption).put("reply", end.reply)
                    .put("uplink", end.uploaded).put("downlink", end.downloaded).put("latencyMs", end.latencyMs)
                    .put("streamedWhileListening", true).put("automaticEnd", true))
                waitFor(15000) { vm.state.value.connected && vm.state.value.phase == Phase.LISTENING }
                report.put("automaticNextListening", true)
                output.writeText(report.toString(2))
                instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Completed real turn ${index + 1}/$count\n") })
            }
            assertEquals("Ambient quiet check: audio uploaded before fixture playback", 0, quietFrames)
            report.put("result", "passed")
        } catch (error: Throwable) {
            report.put("result", "failed").put("error", error.message)
            throw error
        } finally {
            output.writeText(report.toString(2))
            main { scope.cancel(); if (::vm.isInitialized) vm.disconnect() }
            scenario.close()
            if (previousSession == null) store.clear() else store.save(previousSession)
        }
    }
}
