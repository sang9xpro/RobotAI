package com.robotai.robot

import com.robotai.robot.data.YouTubeMusicSearch
import com.robotai.robot.data.MusicPlayer
import com.robotai.robot.companion.DeviceMusicTools
import com.robotai.robot.companion.DeviceRobotTools
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

class YouTubeMusicSearchTest {
    @Test fun mcpSearchDispatchesSongAndKeywordToPhone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var tools: DeviceRobotTools
        lateinit var player: MusicPlayer
        var received = ""
        instrumentation.runOnMainSync {
            player = MusicPlayer(instrumentation.targetContext)
            val music = DeviceMusicTools(player) { _, _, _ -> error("Unexpected direct play") }
            music.onYouTubeSearch = { title, artist, query, _ ->
                received = "$title|$artist|$query"
                player.beginYouTubeSearch(title, artist)
            }
            tools = DeviceRobotTools(music)
        }
        val names = tools.handle(JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/list"), true)!!
            .getJSONObject("result").getJSONArray("tools")
        assertTrue((0 until names.length()).any { names.getJSONObject(it).getString("name") == "self.music.search" })
        val now = System.currentTimeMillis()
        val call = JSONObject().put("jsonrpc", "2.0").put("id", 2).put("method", "tools/call")
            .put("params", JSONObject().put("name", "self.music.search").put("arguments", JSONObject()
                .put("title", "Đếm thời gian").put("artist", "NDMT").put("searchQuery", "Đếm thời gian NDMT")
                .put("issued_at_ms", now).put("expires_at_ms", now + 5000)))
        lateinit var result: JSONObject
        instrumentation.runOnMainSync { result = tools.handle(call, true)!! }
        assertEquals("Đếm thời gian|NDMT|Đếm thời gian NDMT", received)
        assertEquals("loading", JSONObject(result.getJSONObject("result").getJSONArray("content")
            .getJSONObject(0).getString("text")).getString("phase"))
        instrumentation.runOnMainSync { tools.reset() }
    }

    @Test fun phoneSearchesEmbeddableVideosAndSelectsRequestedSong() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val request = AtomicReference("")
            val worker = Thread {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val lines = buildString {
                        appendLine(input.readLine())
                        while (true) {
                            val line = input.readLine()
                            if (line.isNullOrEmpty()) break
                            appendLine(line)
                        }
                    }
                    request.set(lines)
                    val body = """{"items":[
                        {"id":{"videoId":"AAAAAAAAAAA"},"snippet":{"title":"Đếm Thời Gian Karaoke","channelTitle":"karaoke"}},
                        {"id":{"videoId":"bZ93yC-yAZ8"},"snippet":{"title":"Đếm Thời Gian (Remake Version) - NDMT","channelTitle":"NDMT"}}
                    ]}""".trimIndent()
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n" + body).toByteArray())
                    socket.getOutputStream().flush()
                }
            }.apply { start() }
            try {
                val search = YouTubeMusicSearch("test-key", "com.robotai.robot", "ABCDEF",
                    "http://127.0.0.1:${server.localPort}/youtube/v3/search")
                val found = search.find("Đếm thời gian", "NDMT", "Đếm thời gian NDMT")
                assertEquals("bZ93yC-yAZ8", found.videoId)
                assertTrue(request.get().contains("q=%C4%90%E1%BA%BFm%20th%E1%BB%9Di%20gian%20NDMT"))
                assertTrue(request.get().contains("videoEmbeddable=true"))
                assertTrue(request.get().contains("X-Android-Package: com.robotai.robot"))
                assertTrue(request.get().contains("X-Android-Cert: ABCDEF"))
            } finally { worker.join(5000) }
        }
    }

    @Test fun directLinkDoesNotNeedSearchKey() {
        val search = YouTubeMusicSearch("", "com.robotai.robot", "")
        assertEquals("M7lc1UVf-VE", search.find("Video", "", "", "https://youtu.be/M7lc1UVf-VE").videoId)
        assertEquals("bZ93yC-yAZ8", search.find("Đếm thời gian", "", "").videoId)
    }
}
