package com.robotai.robot

import com.robotai.robot.data.MusicPlayer
import com.robotai.robot.data.MusicSource
import com.robotai.robot.data.YouTubeVideo
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assert.assertThrows

class MusicUrlTest {
    @Test fun acceptsRemoteAudioAndLocalBackendHttp() {
        MusicPlayer.validateUrl("https://music.example/track.mp3")
        MusicPlayer.validateUrl("http://192.168.1.2:8091/api/robot/music/audio?name=demo.mp3")
    }
    @Test fun credentialsAreSentOnlyToBackendAudio() {
        val local = MusicSource.resolve("http://192.168.1.2:8091", "private", "/api/robot/music/audio?name=demo.mp3")
        assertEquals("Bearer private", local.headers["Authorization"])
        assertTrue(MusicSource.resolve("http://192.168.1.2:8091", "private", "https://music.example/audio.mp3").headers.isEmpty())
    }
    @Test fun rejectsFilesystemCredentialsAndYoutubePages() {
        for (url in listOf("file:///data/private", "http://user:pass@music.example/a.mp3", "https://www.youtube.com/watch?v=abc", "https://youtu.be/abc", "/api/robot/music/audio"))
            assertThrows(IllegalArgumentException::class.java) { MusicPlayer.validateUrl(url) }
    }
    @Test fun youtubeWatchLinksUseEmbeddedPlayerWithoutBackendCredentials() {
        val url = "https://www.youtube.com/watch?v=M7lc1UVf-VE"
        assertEquals("M7lc1UVf-VE", YouTubeVideo.id(url))
        assertEquals("M7lc1UVf-VE", YouTubeVideo.id("https://youtu.be/M7lc1UVf-VE"))
        assertNull(YouTubeVideo.id("https://youtube.com.evil.test/watch?v=M7lc1UVf-VE"))
        assertTrue(MusicSource.resolve("http://127.0.0.1:8091", "secret", url).headers.isEmpty())
    }
}
