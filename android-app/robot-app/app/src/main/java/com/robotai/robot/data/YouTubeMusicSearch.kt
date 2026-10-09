package com.robotai.robot.data

import android.content.Context
import android.content.pm.PackageManager
import com.robotai.robot.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

data class YouTubeTrack(val videoId: String, val title: String, val artist: String)

/** The phone resolves YouTube videos. The backend only supplies the requested song and keywords. */
class YouTubeMusicSearch(
    private val apiKey: String,
    private val appPackage: String,
    private val signingSha1: String,
    private val endpoint: String = "https://www.googleapis.com/youtube/v3/search",
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).build(),
) {
    fun find(title: String, artist: String, query: String, videoUrl: String = ""): YouTubeTrack {
        require(title.isNotBlank() && title.length <= 300 && artist.length <= 300 && query.length <= 300) { "Yêu cầu tìm nhạc không hợp lệ" }
        if (videoUrl.isNotBlank()) {
            val id = YouTubeVideo.id(videoUrl) ?: throw IllegalArgumentException("Link YouTube không hợp lệ")
            return YouTubeTrack(id, title, artist)
        }
        if (apiKey.isBlank()) {
            if (normalize(title) == "dem thoi gian")
                return YouTubeTrack("bZ93yC-yAZ8", "Đếm Thời Gian (Remake Version)", "NDMT")
            throw IllegalStateException("App chưa có YOUTUBE_API_KEY để tìm bài YouTube khác. Hãy gửi link video trực tiếp.")
        }
        val searchUrl = endpoint.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("part", "snippet")
            ?.addQueryParameter("type", "video")
            ?.addQueryParameter("videoEmbeddable", "true")
            ?.addQueryParameter("maxResults", "5")
            ?.addQueryParameter("relevanceLanguage", "vi")
            ?.addQueryParameter("regionCode", "VN")
            ?.addQueryParameter("q", query.ifBlank { listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ") })
            ?.addQueryParameter("key", apiKey)
            ?.build() ?: throw IllegalArgumentException("Địa chỉ YouTube Search không hợp lệ")
        val request = Request.Builder().url(searchUrl)
            .header("X-Android-Package", appPackage)
            .header("X-Android-Cert", signingSha1)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube Search trả lỗi HTTP ${response.code}")
            val items = JSONObject(response.body?.string() ?: "{}").optJSONArray("items")
                ?: throw IOException("YouTube Search không có kết quả")
            val requested = normalize(title)
            val wantedArtist = normalize(artist)
            val results = (0 until items.length()).mapNotNull { index ->
                val row = items.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optJSONObject("id")?.optString("videoId") ?: return@mapNotNull null
                if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return@mapNotNull null
                val snippet = row.optJSONObject("snippet") ?: return@mapNotNull null
                YouTubeTrack(id, snippet.optString("title", title), snippet.optString("channelTitle", ""))
            }
            return results.maxByOrNull { video ->
                val name = normalize(video.title)
                val channel = normalize(video.artist)
                (if (name.contains(requested)) 10 else 0) +
                    (if (wantedArtist.isNotBlank() && (name.contains(wantedArtist) || channel.contains(wantedArtist))) 8 else 0) -
                    (if (!requested.contains("karaoke") && name.contains("karaoke")) 5 else 0)
            } ?: throw IOException("Không tìm thấy video YouTube có thể nhúng cho bài này")
        }
    }

    companion object {
        fun from(context: Context): YouTubeMusicSearch {
            val packageName = context.packageName
            val signing = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo.apkContentsSigners.first().toByteArray()
            val fingerprint = MessageDigest.getInstance("SHA-1").digest(signing)
                .joinToString("") { "%02X".format(it) }
            return YouTubeMusicSearch(BuildConfig.YOUTUBE_API_KEY, packageName, fingerprint)
        }

        private fun normalize(value: String): String = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
    }
}
