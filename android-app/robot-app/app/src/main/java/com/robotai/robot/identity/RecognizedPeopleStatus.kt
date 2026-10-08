package com.robotai.robot.identity

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotai.domain.TrackingSource
import com.robotai.domain.GestureDecision

/** The same single enrolled owner required by the gesture engine. */
@Composable fun GestureReadinessStatus(state: IdentityUi, awake: Boolean, modifier: Modifier = Modifier) {
    val visible=state.trackedPeople.filter { it.visible }
    val ownerId=if(state.trackedPeople.isNotEmpty()) visible.singleOrNull()?.takeIf { it.gestureEligible }?.personId else state.faces.singleOrNull()?.personId
    val owner = ownerId?.let { id -> state.people.firstOrNull { it.id==id && it.faces.isNotEmpty() } }
    val message = when {
        !awake -> "Chạm mặt Ken để đánh thức và nhận cử chỉ"
        !state.socialGesturesEnabled -> "Phản ứng cử chỉ đang tắt"
        state.enrollment != null -> "Đang thu mẫu mặt · tạm dừng cử chỉ"
        state.voiceBusy -> "Đang thu mẫu giọng · tạm dừng cử chỉ"
        state.error != null -> state.error
        state.people.none { it.faces.isNotEmpty() } -> "Đăng ký khuôn mặt trong Người quen để tương tác cử chỉ"
        visible.size > 1 || state.faces.size > 1 -> "Chỉ để một người trong camera để nhận cử chỉ"
        state.trackedPeople.any { !it.visible && it.personId!=null } && visible.isEmpty() -> "Tạm mất dấu người quen · chờ xác nhận lại"
        state.faces.isEmpty() && visible.isEmpty() -> "Đưa mặt và bàn tay vào camera để tương tác"
        owner == null -> "Chưa nhận ra người quen · nhìn thẳng camera"
        state.gestureDecision==GestureDecision.CLIPPED_HAND -> "Đưa cả bàn tay vào hình · giơ tay cao hơn"
        state.gestureDecision==GestureDecision.OUTSIDE_OWNER -> "Đưa bàn tay gần vai và giữ trong hình"
        state.gestureDecision==GestureDecision.LOW_SCORE -> "Giữ rõ cử chỉ · hướng bàn tay về camera"
        state.gestureDecision==GestureDecision.HOLDING -> state.gestureCandidate?.let { "${it.label} · đang xác nhận…" } ?: "Đã thấy cử chỉ · giữ thêm một chút"
        state.gestureDecision==GestureDecision.WAIT_RELEASE -> "Đã phản ứng · đổi cử chỉ hoặc hạ tay để thử lại"
        visible.singleOrNull()?.source==TrackingSource.BODY -> "Đang theo dõi: ${owner.name} · sẵn sàng nhận cử chỉ"
        else -> "Đã nhận diện: ${owner.name} · sẵn sàng nhận cử chỉ"
    }
    Column(modifier) {
    Text(message, modifier=Modifier.testTag("gesture-readiness"),
        color=if(owner!=null && awake && state.socialGesturesEnabled && state.enrollment==null && !state.voiceBusy && state.error==null) Color.Cyan else Color.LightGray,
        style=MaterialTheme.typography.bodyMedium)
    if(owner!=null && awake && state.gestureDecision==GestureDecision.HOLDING)
        LinearProgressIndicator(progress=state.gestureProgress,modifier=Modifier.fillMaxWidth().padding(top=4.dp).testTag("gesture-progress"))
    }
}

@Composable fun RecognizedPeopleStatus(state: IdentityUi) {
    val ids=if(state.trackedPeople.isNotEmpty()) state.trackedPeople.filter { it.visible }.mapNotNull { it.personId }
        else state.faces.mapNotNull { it.personId }
    val names=ids.mapNotNull { id -> state.people.firstOrNull { it.id==id }?.name }.distinct()
    if (names.isNotEmpty()) {
        Text(
            "Đã nhận diện: ${names.joinToString(", ")}",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("recognized-people"),
            color = Color.Cyan,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
