package com.robotai.robot.auth

import com.robotai.robot.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

class AuthFailure(val unauthorized: Boolean, message: String) : Exception(message)
class UserSession(val baseUrl: String, val socketUrl: String, val userId: Int, val name: String, val token: String) {
    fun json() = JSONObject().put("baseUrl", baseUrl).put("socketUrl", socketUrl)
        .put("userId", userId).put("name", name).put("token", token).toString()
    companion object {
        fun fromJson(json: String): UserSession {
            val j = JSONObject(json)
            return UserSession(j.getString("baseUrl"), j.getString("socketUrl"), j.getInt("userId"), j.getString("name"), j.getString("token"))
        }
    }
}
class AuthApi {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    fun validate(base: String, socket: String) {
        val api = URI(base)
        val ws = URI(socket)
        require(api.scheme in listOf("http", "https") && !api.host.isNullOrBlank() && api.userInfo == null && api.query == null && api.fragment == null) { "Địa chỉ API không hợp lệ" }
        require(api.scheme == "https" || BuildConfig.DEBUG) { "Backend cần HTTPS" }
        require(ws.scheme == (if (api.scheme == "https") "wss" else "ws") && ws.host == api.host && ws.userInfo == null && ws.query == null && ws.fragment == null) { "WebSocket phải cùng máy chủ và mức bảo mật với API" }
    }
    fun request(base: String, path: String, token: String? = null, method: String = "GET", body: JSONObject? = null): JSONObject {
        val builder = Request.Builder().url(base.trimEnd('/') + path)
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (method != "GET") builder.method(method, (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            val code = json?.optInt("code", response.code) ?: response.code
            if (!response.isSuccessful || code != 200) {
                throw AuthFailure(response.code == 401 || code == 401, when {
                    response.code == 401 || code == 401 -> if (path.endsWith("/login")) "Tài khoản hoặc mật khẩu không đúng" else "Phiên đăng nhập không hợp lệ hoặc đã hết hạn"
                    path.endsWith("/login") -> "Đăng nhập thất bại. Kiểm tra tài khoản và mật khẩu."
                    else -> "Backend chưa xử lý được yêu cầu ($code)"
                })
            }
            return json ?: throw AuthFailure(false, "Backend trả dữ liệu không hợp lệ")
        }
    }
    fun login(base: String, socket: String, username: String, password: String): UserSession {
        validate(base, socket)
        val data = request(base, "/api/user/login", method = "POST", body = JSONObject().put("username", username).put("password", password)).getJSONObject("data")
        return parse(base, socket, data)
    }
    fun check(session: UserSession): UserSession {
        validate(session.baseUrl, session.socketUrl)
        return parse(session.baseUrl, session.socketUrl, request(session.baseUrl, "/api/user/check-token", session.token).getJSONObject("data"))
    }
    private fun parse(base: String, socket: String, data: JSONObject): UserSession {
        val id = data.getInt("userId")
        val user = data.optJSONObject("user")
        val name = user?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }
            ?: user?.optString("username")?.takeIf { it.isNotBlank() } ?: "User $id"
        val token = data.getString("token")
        require(id > 0 && token.isNotBlank()) { "Phiên đăng nhập không hợp lệ" }
        return UserSession(base.trimEnd('/'), socket, id, name, token)
    }
}
