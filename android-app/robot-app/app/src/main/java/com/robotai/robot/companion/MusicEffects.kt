package com.robotai.robot.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import com.robotai.robot.data.MusicUiState

/** Decorative animation, not a microphone or audio-spectrum measurement. */
@Composable fun MusicPanel(music: MusicUiState, pause: () -> Unit, stop: () -> Unit, modifier: Modifier = Modifier,
    attachYouTube: (WebView) -> Unit = {}, detachYouTube: (WebView) -> Unit = {}) {
    if (!music.active && music.phase != "error") return
    Column(modifier.background(Color(0xe60b211e), MaterialTheme.shapes.medium).padding(12.dp).testTag("music-panel")) {
        Text("♫ ${music.title}", color = Color.White, maxLines = 2)
        if (music.artist.isNotBlank()) Text(music.artist, color = Color.LightGray, maxLines = 1)
        Text(music.message, color = Color(0xff8cf3e5), style = MaterialTheme.typography.bodySmall)
        if (music.youtubeId.isNotBlank() && music.active) {
            key(music.youtubeId) {
                val context = LocalContext.current
                val embedded = remember(context) { WebView(context) }
                AndroidView(factory = { attachYouTube(embedded); embedded },
                    modifier = Modifier.fillMaxWidth().height(210.dp).testTag("youtube-player"))
                DisposableEffect(embedded) { onDispose { detachYouTube(embedded) } }
            }
        }
        if (music.phase == "playing") com.robotai.robot.motion.KenMusicWave(
            Modifier.fillMaxWidth().height(30.dp).padding(top = 6.dp))
        Row {
            if (music.phase in listOf("playing", "paused")) TextButton(onClick = pause, modifier = Modifier.testTag("music-pause")) { Text(if (music.phase == "paused") "Phát tiếp" else "Tạm dừng") }
            TextButton(onClick = stop, modifier = Modifier.testTag("music-stop")) { Text("Dừng nhạc") }
        }
    }
}
