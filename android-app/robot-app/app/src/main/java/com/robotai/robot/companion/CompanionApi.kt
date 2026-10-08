package com.robotai.robot.companion
import com.robotai.robot.auth.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CompanionApi {
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).followRedirects(false).build()
    fun weather(session: UserSession, latitude: Double, longitude: Double): String {
        require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val c=AuthApi().request(session.baseUrl,"/api/robot/weather?latitude=$latitude&longitude=$longitude",session.token).getJSONObject("data")
            val code = c.getInt("weatherCode")
            val desc = when (code) { 0 -> "Trời quang"; in 1..3 -> "Có mây"; 45,48 -> "Sương mù"; in 51..67 -> "Mưa"; in 71..77 -> "Tuyết"; in 80..82 -> "Mưa rào"; in 95..99 -> "Dông"; else -> "Mã thời tiết $code" }
            return "${c.getDouble("temperatureC")} °C · $desc\n${c.getString("time")} (${c.optString("timezone")})\nNguồn: ${c.getString("source")} · dữ liệu mô hình"
    }
    suspend fun vision(session: UserSession, bytes: ByteArray, question: String, consent: Boolean): String {
        require(consent) { "Bật cho phép gửi ảnh trước" }
        require(bytes.size in 1..2_000_000 && question.isNotBlank())
        AuthApi().validate(session.baseUrl, session.socketUrl)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("consent", "true").addFormDataPart("question", question)
            .addFormDataPart("image", "scene.jpg", bytes.toRequestBody("image/jpeg".toMediaType())).build()
        val call = http.newCall(Request.Builder().url(session.baseUrl + "/api/robot/vision")
            .header("Authorization", "Bearer ${session.token}").post(body).build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val answer = response.use { r ->
                            val json = JSONObject(r.body?.string().orEmpty())
                            check(r.isSuccessful && json.optInt("code") == 200) { "Không phân tích được ảnh; kiểm tra phiên và model thị giác ở server (${r.code})" }
                            json.getJSONObject("data").getString("answer")
                        }
                        if (continuation.isActive) continuation.resume(answer)
                    } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            })
        }
    }
}
