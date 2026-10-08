package com.robotai.robot.companion

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robotai.domain.GestureReaction
import com.robotai.domain.SocialGesture
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun DrawScope.drawSocialHeart(center: Offset, width: Float, color: Color = Color(0xffff6baf)) {
    val x = center.x - width / 2; val y = center.y - width / 2
    drawPath(Path().apply {
        moveTo(x + width * .5f, y + width * .25f)
        cubicTo(x + width * .15f, y - width * .1f, x - width * .1f, y + width * .3f, x + width * .12f, y + width * .55f)
        lineTo(x + width * .5f, y + width)
        lineTo(x + width * .88f, y + width * .55f)
        cubicTo(x + width * 1.1f, y + width * .3f, x + width * .85f, y - width * .1f, x + width * .5f, y + width * .25f)
        close()
    }, color)
}

fun gestureCaption(kind: SocialGesture, name: String): String = when (kind) {
    SocialGesture.FINGER_HEART, SocialGesture.TWO_HAND_HEART, SocialGesture.I_LOVE_YOU -> "Ken gửi tim tới $name!"
    SocialGesture.WAVE -> "Chào $name!"
    SocialGesture.THUMBS_UP -> "Cảm ơn $name!"
    SocialGesture.VICTORY -> "Cùng ăn mừng nào, $name!"
    SocialGesture.FIST_BUMP -> "Cùng cố gắng nhé, $name!"
    SocialGesture.HIGH_FIVE -> "Đập tay nào, $name!"
    SocialGesture.THUMBS_DOWN -> "Ken hiểu rồi, $name!"
    SocialGesture.POINT_UP -> "Ý tưởng gì vậy, $name?"
    SocialGesture.OK_SIGN -> "Đồng ý nhé, $name!"
    SocialGesture.ROCK_ON -> "Cháy lên nào, $name!"
}

/** Decorative layer has no input handler and never interrupts conversation audio. */
@Composable fun GestureEffects(reaction: GestureReaction?, personName: String?, modifier: Modifier = Modifier) {
    if (reaction == null || personName == null) return
    val progress = remember(reaction) { Animatable(0f) }
    LaunchedEffect(reaction) {
        val elapsed = (SystemClock.elapsedRealtime() - reaction.atMs).coerceAtLeast(0)
        progress.snapTo((elapsed.toFloat() / GestureReaction.DURATION_MS).coerceIn(0f, 1f))
        if (elapsed < GestureReaction.DURATION_MS)
            progress.animateTo(1f, tween((GestureReaction.DURATION_MS - elapsed).toInt(), easing = LinearEasing))
    }
    val p = progress.value
    if (p >= 1f) return
    val caption = reaction.learnedName?.let { "Ken hiểu “$it”, $personName!" } ?: gestureCaption(reaction.kind, personName)
    val alpha = ((1f - p) * 5f).coerceIn(0f, 1f)
    Box(modifier.fillMaxSize().testTag("gesture-effect-${reaction.kind.name}").semantics { contentDescription = caption }) {
        Canvas(Modifier.fillMaxSize()) {
            if (reaction.kind.loving) {
                if (reaction.kind == SocialGesture.TWO_HAND_HEART && p < .6f)
                    drawSocialHeart(Offset(size.width / 2, size.height * .42f), size.minDimension * (.16f + .1f * sin(p * PI).toFloat()), Color(0xffff6baf).copy(alpha = alpha))
                repeat(12) { index ->
                    val travel = (p + index * .061f) % 1f
                    val x = size.width * (.12f + .76f * ((index * 7 % 13) / 12f)) + sin(travel * 6f + index) * 16.dp.toPx()
                    drawSocialHeart(Offset(x, size.height * (1f - travel)), (14 + index % 4 * 7).dp.toPx(),
                        Color(0xffff6baf).copy(alpha = alpha * (1f - travel)))
                }
            } else {
                repeat(22) { index ->
                    val angle = index * 2f * PI / 22
                    val radius = size.minDimension * p * .65f
                    val origin = Offset(size.width / 2 + cos(angle).toFloat() * radius,
                        size.height * .45f + sin(angle).toFloat() * radius + p * p * size.height * .2f)
                    val colors = listOf(Color.Cyan, Color(0xffffd66e), Color(0xfffa7bab), Color(0xff8cf4ab))
                    drawCircle(colors[index % colors.size].copy(alpha = alpha), (3 + index % 3).dp.toPx(), origin)
                }
            }
        }
        if (!reaction.kind.loving) {
            val symbol = when (reaction.kind) {
                SocialGesture.WAVE -> "👋"
                SocialGesture.THUMBS_UP -> "👍"
                SocialGesture.VICTORY -> "🎉"
                SocialGesture.HIGH_FIVE -> "✋"
                SocialGesture.THUMBS_DOWN -> "🥺"
                SocialGesture.POINT_UP -> "💡"
                SocialGesture.OK_SIGN -> "👌"
                SocialGesture.ROCK_ON -> "🤘"
                else -> "👊"
            }
            Text(symbol, fontSize = 76.sp, modifier = Modifier.align(Alignment.Center).graphicsLayer {
                this.alpha = alpha
                rotationZ = if (reaction.kind == SocialGesture.WAVE) sin(p * 24f) * 18f else 0f
                scaleX = 1f + .12f * sin(p * 12f); scaleY = scaleX
                translationX = if (reaction.kind == SocialGesture.FIST_BUMP) sin(p * PI).toFloat() * 28.dp.toPx() else 0f
            })
        }
        Text(caption, color = Color.White, fontSize = 18.sp, modifier = Modifier.align(Alignment.BottomCenter)
            .padding(bottom = 100.dp, start = 16.dp, end = 16.dp).graphicsLayer { this.alpha = alpha }
            .background(Color(0xe608111b), RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 10.dp))
    }
}
