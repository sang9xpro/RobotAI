package com.robotai.robot.data

import com.robotai.domain.BackendPort
import okhttp3.*
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

class SocketBackend : BackendPort {
    private val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var credentials: Pair<Int, String>? = null
    fun authenticate(userId: Int, token: String) { credentials = userId to token }
    fun clearCredentials() { close(); credentials = null }
    override fun connect(url: String, onText: (String) -> Unit, onAudio: (ByteArray) -> Unit, onClosed: (String) -> Unit) {
        close()
        val builder = Request.Builder().url(url)
        credentials?.let { (id, token) ->
            builder.header("device-id", "user_chat_$id").header("Authorization", "Bearer $token")
        }
        val request = builder.build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"hello","version":1,"transport":"websocket","features":{"mcp":true},"audio_params":{"format":"opus","sample_rate":16000,"channels":1,"frame_duration":60},"robotai":{"contract_version":1,"audio_envelopes":["rai1"],"stt_partial":true,"playback_progress":true}}""")
            }
            override fun onMessage(webSocket: WebSocket, text: String) = onText(text)
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) = onAudio(bytes.toByteArray())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onClosed(t.message ?: "Lỗi kết nối")
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onClosed("Đã ngắt kết nối ($code)")
        })
    }
    override fun sendText(text: String) = socket?.send(text) == true
    fun reportMusic(state: String): Boolean = state in listOf("loading", "playing", "paused") &&
        sendText(org.json.JSONObject().put("type", "music_playback").put("state", state).toString())
    override fun sendAudio(audio: ByteArray): Boolean {
        val ws = socket ?: return false
        // Opus bitrate 24 kbps: ~3 KB/s. Never allow seconds of stale upload to collect.
        if (ws.queueSize() > 4096) return false
        return ws.send(audio.toByteString())
    }
    override fun close() { socket?.cancel(); socket = null }
}
