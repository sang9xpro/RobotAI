package com.robotai.robot.companion

import com.robotai.robot.data.MusicPlayer
import org.json.JSONArray
import org.json.JSONObject
import android.os.SystemClock

/** These tools execute locally; MCP success acknowledges loading, state reports actual playback. */
class DeviceMusicTools(private val player: MusicPlayer, private val start: (String, String, String) -> Unit) {
    var onYouTubeSearch: ((String, String, String, String) -> Unit)? = null
    var onStop: () -> Unit = { player.stop() }
    private val seen = mutableSetOf<Long>()
    private var serverTime = 0L
    private var receivedAt = 0L
    fun syncClock(value: Long) { serverTime = value; receivedAt = SystemClock.elapsedRealtime() }
    fun reset() { seen.clear(); serverTime = 0; onStop() }
    fun tools(): JSONArray = JSONArray().put(tool("self.music.play", "Phát link audio trực tiếp hoặc mở video YouTube trong trình phát hiển thị cùng Ken trên Android. Backend xác định tên bài và URL. Trả về trạng thái đang tải; gọi status để kiểm tra phát thật.",
        """{"type":"object","properties":{"title":{"type":"string","maxLength":300},"artist":{"type":"string","maxLength":300},"url":{"type":"string","maxLength":4096}},"required":["title","url"],"additionalProperties":false}"""))
        .put(tool("self.music.search", "App Android tự tìm video YouTube theo tên bài, ca sĩ và từ khóa backend đề xuất, chọn video có thể nhúng rồi phát trong trình phát hiển thị cùng Ken. Backend không tìm URL.",
            """{"type":"object","properties":{"title":{"type":"string","maxLength":300},"artist":{"type":"string","maxLength":300},"searchQuery":{"type":"string","maxLength":300},"videoUrl":{"type":"string","maxLength":4096}},"required":["title","searchQuery"],"additionalProperties":false}"""))
        .put(tool("self.music.stop", "Dừng nhạc trên Android", """{"type":"object","properties":{},"additionalProperties":false}"""))
        .put(tool("self.music.status", "Lấy trạng thái nhạc thực tế trên Android", """{"type":"object","properties":{},"additionalProperties":false}"""))
    private fun tool(name: String, description: String, schema: String) = JSONObject().put("name", name).put("description", description).put("inputSchema", JSONObject(schema))
    fun call(payload: JSONObject, activeTurn: Boolean): JSONObject {
        val response = JSONObject().put("jsonrpc", "2.0").put("id", payload.getLong("id"))
        try {
            val params = payload.getJSONObject("params")
            val name = params.getString("name")
            if (name != "self.music.status") {
                require(seen.add(payload.getLong("id"))) { "Repeated music request" }
                require(seen.size <= 256) { "Music request limit reached; reconnect" }
            }
            when (name) {
                "self.music.play", "self.music.search" -> {
                    require(activeTurn) { "Music requires an active foreground conversation" }
                    val args = params.getJSONObject("arguments")
                    val now = if (serverTime > 0) serverTime + SystemClock.elapsedRealtime() - receivedAt else System.currentTimeMillis()
                    require(now - args.getLong("issued_at_ms") in -2000..10000 && args.getLong("expires_at_ms") - now in 1..12000) { "Expired music request" }
                    if (name == "self.music.play") start(args.getString("title"), args.optString("artist"), args.getString("url"))
                    else (onYouTubeSearch ?: error("YouTube search unavailable"))(
                        args.getString("title"), args.optString("artist"), args.getString("searchQuery"), args.optString("videoUrl"))
                }
                "self.music.stop" -> onStop()
                "self.music.status" -> Unit
                else -> error("Unknown music tool")
            }
            val current = player.state.value
            val status = JSONObject().put("phase", current.phase).put("title", current.title).put("artist", current.artist).put("message", current.message)
            response.put("result", JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", status.toString()))))
        } catch (e: Exception) {
            response.put("result", JSONObject().put("isError", true).put("content", JSONArray().put(JSONObject().put("type", "text").put("text", e.message ?: "Music rejected"))))
        }
        return response
    }
}
