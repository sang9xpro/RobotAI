package com.robotai.robot.identity

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.robotai.domain.FacialExpression

@Composable fun ExpressionReadinessStatus(state:IdentityUi,awake:Boolean,now:Long,modifier:Modifier=Modifier) {
    val observed=state.facialExpression?.takeIf { it.visible(now) }
    val person=state.people.firstOrNull { it.id==observed?.owner?.personId }
    val message=when {
        !awake -> "Ken ngủ · quan sát biểu cảm đang dừng"
        !state.expressionsEnabled -> "Phản ứng biểu cảm đang tắt"
        state.enrollment!=null || state.voiceBusy -> "Đang thu mẫu · tạm dừng quan sát biểu cảm"
        state.expressionError!=null -> state.expressionError
        state.faces.size>1 || state.trackedPeople.count { it.visible }>1 -> "Chỉ để một người trong hình để Ken đọc biểu cảm"
        person==null || observed==null -> "Đợi Ken nhận ra khuôn mặt người quen"
        observed.current(now)==FacialExpression.UNKNOWN -> "Đang quan sát nét mặt của ${person.name}"
        else -> "${observed.current(now).label} · ${person.name}"
    }
    Text(message,modifier.testTag("expression-status"),color=if(person!=null && awake && state.expressionsEnabled) Color.Cyan else Color.LightGray,
        style=MaterialTheme.typography.bodySmall)
}
