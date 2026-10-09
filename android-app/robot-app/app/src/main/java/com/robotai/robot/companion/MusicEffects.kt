package com.robotai.robot.companion

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import com.robotai.robot.data.MusicUiState
import kotlin.math.sin

/** Decorative animation, not a microphone or audio-spectrum measurement. */
@Composable fun MusicPanel(music: MusicUiState, pause: () -> Unit, stop: () -> Unit, modifier: Modifier = Modifier,
    attachYouTube: (WebView) -> Unit = {}, detachYouTube: (WebView) -> Unit = {}) {
    if (!music.active && music.phase != "error") return
    val transition = rememberInfiniteTransition(label = "music")
    val beat by transition.animateFloat(0f, 6.283f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "music-beat")
    Column(modifier.background(Color(0xe60a1725), MaterialTheme.shapes.medium).padding(12.dp).testTag("music-panel")) {
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
        if (music.phase == "playing") Canvas(Modifier.fillMaxWidth().height(30.dp).padding(top = 6.dp).testTag("music-effects")) {
            val count = 16; val gap = size.width / count
            for (i in 0 until count) {
                val height = size.height * (.2f + .8f * kotlin.math.abs(sin(beat + i * .65f)))
                drawRoundRect(if (i % 3 == 0) Color(0xffee85d5) else Color(0xff67e8dc),
                    Offset(i * gap, (size.height - height) / 2), Size(gap * .55f, height), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
        }
        Row {
            if (music.phase in listOf("playing", "paused")) TextButton(onClick = pause, modifier = Modifier.testTag("music-pause")) { Text(if (music.phase == "paused") "Phát tiếp" else "Tạm dừng") }
            TextButton(onClick = stop, modifier = Modifier.testTag("music-stop")) { Text("Dừng nhạc") }
        }
    }
}
