package com.robotai.robot.companion
import android.os.SystemClock
import com.robotai.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Device-side MCP endpoint. Only the simulator is available in this build. */
class DeviceRobotTools {
    val transport = SimulatedRobotTransport()
    private val link = RobotLink(transport)
    private var sequence = 0L
    private val seen = mutableSetOf<Long>()
    var enabled = false
        set(value) { field = value; if (!value) stop() }
    var status = "Đế mô phỏng"; private set
    var initialized = false; private set
    var listed = false; private set
    fun reset() { stop(); seen.clear(); initialized = false; listed = false }
    fun stop() = link.stop(SystemClock.elapsedRealtime())
    fun tick() = link.tick(SystemClock.elapsedRealtime())
    fun local(action: RobotAction, expired: Boolean = false): String {
        val now = SystemClock.elapsedRealtime()
        val error = link.propose(RobotCommand(++sequence, action, if (expired) now-1 else now+1000, 500, 20), now)
        status = error ?: "Mô phỏng $action trong 500 ms"; return status
    }
    fun handle(payload: JSONObject, activeTurn: Boolean): JSONObject? {
        if (!payload.has("id")) return null
        val id = payload.getLong("id")
        val response = JSONObject().put("jsonrpc","2.0").put("id",id)
        try {
            val result = when(payload.optString("method")) {
                "initialize" -> JSONObject().also { initialized = true }.put("protocolVersion","2024-11-05")
                    .put("capabilities",JSONObject().put("tools",JSONObject()))
                    .put("serverInfo",JSONObject().put("name","RobotAI simulator").put("version","1"))
                "tools/list" -> JSONObject().also { listed = true }.put("tools",JSONArray().put(JSONObject()
                    .put("name","self.robot.action")
                    .put("description","Propose a named robot action. Android validates consent, active turn, expiry and sensors. Current transport is simulation only.")
                    .put("inputSchema",JSONObject("""{"type":"object","properties":{"action":{"type":"string","enum":["STOP","FORWARD","BACKWARD","LEFT","RIGHT"]},"duration_ms":{"type":"integer","minimum":1,"maximum":1000},"speed":{"type":"integer","minimum":1,"maximum":30}},"required":["action","duration_ms","speed"],"additionalProperties":false}"""))))
                "tools/call" -> {
                    val p = payload.getJSONObject("params")
                    require(p.getString("name") == "self.robot.action") { "Unknown tool" }
                    require(seen.add(id)) { "Repeated request" }
                    if (seen.size > 256) { stop(); error("Request limit reached; reconnect") }
                    val a = p.getJSONObject("arguments")
                    val action = RobotAction.valueOf(a.getString("action"))
                    val now = SystemClock.elapsedRealtime()
                    if (action != RobotAction.STOP) {
                        require(enabled && activeTurn) { "Robot proposals disabled or turn ended" }
                        val age = System.currentTimeMillis() - a.getLong("issued_at_ms")
                        val remaining = a.getLong("expires_at_ms") - System.currentTimeMillis()
                        require(age in 0..2000 && remaining in 1..2000) { "Expired proposal or clock mismatch" }
                        val error = link.propose(RobotCommand(++sequence,action,now+remaining,a.getLong("duration_ms"),a.getInt("speed")),now)
                        require(error == null) { error ?: "Rejected" }
                    } else stop()
                    status = "MCP: mô phỏng $action"
                    JSONObject().put("content",JSONArray().put(JSONObject().put("type","text").put("text",status)))
                }
                else -> error("Unsupported MCP method")
            }
            response.put("result",result)
        } catch (e: Exception) {
            stop(); status = e.message ?: "Rejected proposal"
            response.put("error",JSONObject().put("code",-32602).put("message",status))
        }
        return response
    }
}
