package com.robotai.robot.data

import java.net.URI
import java.net.URLDecoder

/** Accepted watch links are converted to an ID before entering the embedded player. */
object YouTubeVideo {
    fun id(link: String): String? = runCatching {
        val uri = URI(link)
        if (uri.scheme != "https" || uri.userInfo != null || uri.fragment != null) return null
        val host = uri.host?.lowercase() ?: return null
        val candidate = when (host) {
            "youtu.be" -> uri.path.removePrefix("/")
            "youtube.com", "www.youtube.com", "m.youtube.com" -> when {
                uri.path == "/watch" -> uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }
                    ?.substring(2)?.let { URLDecoder.decode(it, "UTF-8") }
                uri.path.startsWith("/shorts/") -> uri.path.removePrefix("/shorts/")
                else -> null
            }
            else -> null
        }
        candidate?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }.getOrNull()
}
