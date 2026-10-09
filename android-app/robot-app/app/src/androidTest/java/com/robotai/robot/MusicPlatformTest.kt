package com.robotai.robot

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.domain.Mood
import com.robotai.domain.Phase
import com.robotai.robot.data.MusicPlayer
import com.robotai.robot.companion.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.net.ServerSocket
import kotlin.concurrent.thread

class MusicPlatformTest {
    @get:Rule val compose = createComposeRule()
    @Test fun mcpStartsNativePlayerAndRobotEffectsThenPauseResumeStop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val audio = instrumentation.context.assets.open("music/ken-test.mp3").use { it.readBytes() }
        val server = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            while (!server.isClosed) runCatching {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    var range = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Range:", true)) range = line.substringAfter("bytes=").substringBefore('-').toIntOrNull() ?: 0
                    }
                    val start = range.coerceIn(0, audio.size - 1)
                    val header = if (range > 0) "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $start-${audio.size-1}/${audio.size}\r\n" else "HTTP/1.1 200 OK\r\n"
                    socket.getOutputStream().apply {
                        write((header + "Content-Type: audio/mpeg\r\nContent-Length: ${audio.size-start}\r\nConnection: close\r\n\r\n").toByteArray())
                        write(audio, start, audio.size-start); flush()
                    }
                }
            }
        }
        lateinit var player: MusicPlayer
        lateinit var tools: DeviceRobotTools
        instrumentation.runOnMainSync {
            player = MusicPlayer(instrumentation.targetContext)
            tools = DeviceRobotTools(DeviceMusicTools(player) { title, artist, url -> player.play(title, artist, url) })
            tools.handle(JSONObject().put("id", 100).put("method", "initialize")
                .put("params", JSONObject().put("server_time_ms", System.currentTimeMillis() + 60000)), true)
        }
        compose.setContent {
            val state by player.state.collectAsState()
            MaterialTheme { Column {
                CompanionFace(Mood.HAPPY, Phase.IDLE, 0f, Modifier.fillMaxWidth().height(180.dp), musicPlaying = state.phase == "playing")
                MusicPanel(state, player::pauseOrResume, { player.stop() })
            } }
        }
        fun call(id: Long, name: String, active: Boolean = true, age: Long = 0): JSONObject {
            val now = System.currentTimeMillis() + 60000 - age
            val payload = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", "tools/call")
                .put("params", JSONObject().put("name", name).put("arguments", JSONObject()
                    .put("title", "Nhạc kiểm tra").put("artist", "RobotAI").put("url", "http://127.0.0.1:${server.localPort}/tone.mp3")
                    .put("issued_at_ms", now).put("expires_at_ms", now + 1000)))
            var response: JSONObject? = null
            instrumentation.runOnMainSync { response = tools.handle(payload, active) }
            return response!!
        }
        try {
            assertTrue(call(1, "self.music.play", false).getJSONObject("result").getBoolean("isError"))
            assertTrue(call(6, "self.music.play", age = 20000).getJSONObject("result").getBoolean("isError"))
            assertFalse(call(2, "self.music.play").getJSONObject("result").optBoolean("isError"))
            compose.waitUntil(15000) { player.state.value.phase == "playing" }
            compose.onNodeWithTag("music-effects").assertExists()
            assertTrue(call(3, "self.music.status").toString().contains("playing"))
            compose.onNodeWithTag("music-pause").performClick()
            assertEquals("paused", player.state.value.phase)
            compose.onNodeWithTag("music-pause").performClick()
            assertEquals("playing", player.state.value.phase)
            assertTrue(call(2, "self.music.play").getJSONObject("result").getBoolean("isError"))
            compose.onNodeWithTag("music-stop").performClick()
            assertEquals("idle", player.state.value.phase)
            call(4, "self.music.play")
            compose.waitUntil(15000) { player.state.value.phase == "playing" }
            compose.waitUntil(15000) { player.state.value.phase == "idle" }
            assertEquals("Đã phát hết bài", player.state.value.message)
            call(5, "self.music.play")
            instrumentation.runOnMainSync { tools.reset() }
            Thread.sleep(1000)
            assertEquals("idle", player.state.value.phase)
        } finally {
            instrumentation.runOnMainSync { player.stop() }
            server.close(); worker.join(1000)
        }
    }
}
