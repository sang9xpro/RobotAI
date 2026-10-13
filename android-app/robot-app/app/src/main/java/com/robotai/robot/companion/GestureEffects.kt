package com.robotai.robot.companion

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robotai.domain.GestureReaction
import com.robotai.domain.SocialGesture

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

/** Caption only: vector reactions are rendered beside the eyes by the shared KenFace. */
@Composable fun GestureEffects(reaction: GestureReaction?, personName: String?, modifier: Modifier = Modifier) {
    if (reaction == null || personName == null) return
    val shared = com.robotai.robot.motion.sharedKenPose()
    val alpha = if(shared != null) {
        if(shared.reactionAtMs == reaction.atMs && shared.reaction?.name == reaction.kind.name)shared.reactionWeight else null
    } else standaloneReactionAlpha(reaction)
    if(alpha==null)return
    val caption = reaction.learnedName?.let { "Ken hiểu “$it”, $personName!" } ?: gestureCaption(reaction.kind, personName)
    Box(modifier.fillMaxSize().testTag("gesture-effect-${reaction.kind.name}").semantics { contentDescription = caption }) {
        Text(caption, color = Color.White, fontSize = 18.sp, modifier = Modifier.align(Alignment.BottomCenter)
            .padding(bottom = 100.dp, start = 16.dp, end = 16.dp).graphicsLayer { this.alpha = alpha }
            .background(Color(0xe6050e13), RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 10.dp))
    }
}

@Composable
private fun standaloneReactionAlpha(reaction:GestureReaction):Float? {
    val progress=remember(reaction) { Animatable(0f) }
    LaunchedEffect(reaction) {
        val elapsed=(SystemClock.elapsedRealtime()-reaction.atMs).coerceAtLeast(0)
        progress.snapTo((elapsed.toFloat()/GestureReaction.DURATION_MS).coerceIn(0f,1f))
        if(elapsed<GestureReaction.DURATION_MS)
            progress.animateTo(1f,tween((GestureReaction.DURATION_MS-elapsed).toInt(),easing=LinearEasing))
    }
    return if(progress.value>=1f)null else com.robotai.domain.KenMotionTiming.reactionWeight(progress.value*GestureReaction.DURATION_MS/1000f)
}
