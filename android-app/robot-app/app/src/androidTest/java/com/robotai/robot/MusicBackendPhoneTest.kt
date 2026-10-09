package com.robotai.robot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.robot.auth.AuthApi
import com.robotai.robot.data.*
import com.robotai.robot.companion.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real Java -> MCP over WebSocket -> native Android HTTP playback. Text input bypasses STT. */
class MusicBackendPhoneTest {
    @get:Rule val compose = createComposeRule()
    @Test fun backendResolvesTrackAndInvokesAndroidMcp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = JSONObject(File(context.filesDir, "acceptance.json").readText())
        val options = InstrumentationRegistry.getArguments()
        val account = fixture.getJSONArray("accounts").getJSONObject(options.getString("musicAccountIndex", "1")!!.toInt())
        val session = AuthApi().login(fixture.getString("api"), fixture.getString("socket"), account.getString("username"), account.getString("password"))
        val socket = SocketBackend()
        socket.authenticate(session.userId, session.token)
        lateinit var player: MusicPlayer
        lateinit var tools: DeviceRobotTools
        instrumentation.runOnMainSync {
            player = MusicPlayer(context)
            val musicTools = DeviceMusicTools(player) { title, artist, link ->
                val source = MusicSource.resolve(session.baseUrl, session.token, link)
                val videoId = YouTubeVideo.id(source.url)
                if (videoId != null) player.playYouTube(title, artist, videoId)
                else player.play(title, artist, source.url, source.headers)
            }
            musicTools.onYouTubeSearch = { title, artist, query, videoUrl ->
                player.beginYouTubeSearch(title, artist)
                Thread {
                    try {
                        val video = YouTubeMusicSearch.from(context).find(title, artist, query, videoUrl)
                        instrumentation.runOnMainSync { player.playYouTube(video.title, video.artist, video.videoId) }
                    } catch (e: Exception) {
                        instrumentation.runOnMainSync { player.stop(e.message ?: "Không tìm được video YouTube", error = true) }
                    }
                }.start()
            }
            tools = DeviceRobotTools(musicTools)
        }
        compose.setContent {
            val music by player.state.collectAsState()
            MaterialTheme { MusicPanel(music, player::pauseOrResume, { player.stop() },
                attachYouTube = player::attachYouTube, detachYouTube = player::detachYouTube) }
        }
        val listed = CountDownLatch(1)
        val musicCall = AtomicReference("")
        var problem: String? = null
        var sid = ""
        socket.connect(session.socketUrl, { text ->
            try {
                val event = JSONObject(text)
                when (event.optString("type")) {
                    "hello" -> sid = event.getString("session_id")
                    "mcp" -> {
                        val payload = event.getJSONObject("payload")
                        instrumentation.runOnMainSync {
                            if (payload.optString("method") == "tools/call") {
                                val name = payload.getJSONObject("params").optString("name")
                                if (name == "self.music.play" || name == "self.music.search") musicCall.set(name)
                            }
                            val result = tools.handle(payload, true)
                            if (result != null) socket.sendText(JSONObject().put("type", "mcp").put("sessionId", sid).put("payload", result).toString())
                            if (payload.optString("method") == "tools/list") listed.countDown()
                        }
                    }
                }
            } catch (e: Exception) { problem = e.message }
        }, {}, { problem = it })
        try {
            assertTrue("MCP discovery did not complete: $problem", listed.await(20, TimeUnit.SECONDS))
            // Discovery response needs to reach the Java tool registry before the next input.
            Thread.sleep(500)
            val command = options.getString("musicCommand", "Phát bài Nhạc thử Ken")!!
            assertTrue(socket.sendText(JSONObject().put("type", "listen").put("state", "text").put("text", command).toString()))
            compose.waitUntil(30000) { player.state.value.phase in listOf("playing", "error") || problem != null }
            assertNull(problem)
            assertEquals("Backend invoked the wrong Android music MCP", options.getString("musicExpectedMcp", "self.music.play"), musicCall.get())
            assertEquals(player.state.value.message, "playing", player.state.value.phase)
            assertEquals(options.getString("musicExpectedTitle", "Nhạc thử Ken"), player.state.value.title)
            repeat(if (options.getString("musicShort") == "true") 0 else 5) {
                assertTrue("Music heartbeat rejected: ${player.state.value}", socket.reportMusic(player.state.value.phase))
                Thread.sleep(15000)
                assertNull("Music session disconnected during playback", problem)
            }
            assertEquals("playing", player.state.value.phase)
            File(context.filesDir, "music-backend-result.json").writeText(JSONObject()
                .put("device", android.os.Build.MODEL).put("mcp", musicCall.get())
                .put("phase", player.state.value.phase).put("title", player.state.value.title)
                .put("command", command).put("source", if (player.state.value.youtubeId.isBlank()) "authenticated HTTP from Java, native Android MediaPlayer" else "YouTube IFrame visible in Android")
                .put("sessionAliveSeconds", if (options.getString("musicShort") == "true") 0 else 75)
                .put("sttTested", false).toString(2))
        } finally {
            instrumentation.runOnMainSync { tools.reset() }
            socket.close()
        }
    }
}
