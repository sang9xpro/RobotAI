package com.robotai.robot.data

/** Credentials accompany only the protected backend audio route, never external music URLs. */
data class MusicSource(val url: String, val headers: Map<String, String>) {
    companion object {
        fun resolve(baseUrl: String, token: String, link: String): MusicSource {
            val local = link.startsWith("/api/robot/music/audio?")
            val url = if (local) baseUrl.trimEnd('/') + link else link
            if (YouTubeVideo.id(url) == null) MusicPlayer.validateUrl(url)
            return MusicSource(url, if (local) mapOf("Authorization" to "Bearer $token") else emptyMap())
        }
    }
}
