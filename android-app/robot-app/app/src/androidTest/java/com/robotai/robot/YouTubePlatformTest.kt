package com.robotai.robot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.robot.companion.MusicPanel
import com.robotai.robot.data.MusicPlayer
import com.robotai.robot.data.YouTubeVideo
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class YouTubePlatformTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opensVisibleYoutubePlayerOnPhysicalPhone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var player: MusicPlayer
        instrumentation.runOnMainSync {
            player = MusicPlayer(instrumentation.targetContext)
            player.playYouTube("Đếm Thời Gian (Remake Version)", "NDMT", YouTubeVideo.id("https://youtu.be/bZ93yC-yAZ8")!!)
        }
        compose.setContent {
            val music by player.state.collectAsState()
            MaterialTheme {
                MusicPanel(music, player::pauseOrResume, { player.stop() }, attachYouTube = player::attachYouTube, detachYouTube = player::detachYouTube)
            }
        }
        try {
            compose.onNodeWithTag("youtube-player").assertExists()
            compose.waitUntil(30000) { player.state.value.phase in listOf("playing", "paused", "error") }
            assertEquals(player.state.value.message, "playing", player.state.value.phase)
        } finally { instrumentation.runOnMainSync { player.stop() } }
    }
}
