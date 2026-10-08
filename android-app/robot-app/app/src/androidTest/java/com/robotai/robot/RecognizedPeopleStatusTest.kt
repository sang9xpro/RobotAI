package com.robotai.robot

import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.robotai.robot.identity.*
import com.robotai.domain.*
import org.junit.Rule
import org.junit.Test

class RecognizedPeopleStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun trackedBodyKeepsNameWithoutVisibleFaceButOcclusionIsNotReady() {
        val person=Person("sang","Sang","anh Sang",faces=listOf(floatArrayOf(1f)))
        val tracked=TrackedPerson(1,person.id,TrackBox(.2f,.1f,.7f,.9f),null,emptyList(),true,TrackingSource.BODY,500,0)
        var state by mutableStateOf(IdentityUi(people=listOf(person),trackedPeople=listOf(tracked)))
        compose.setContent { MaterialTheme { androidx.compose.foundation.layout.Column {
            RecognizedPeopleStatus(state);GestureReadinessStatus(state,true)
        } } }
        compose.onNodeWithText("Đã nhận diện: Sang").assertIsDisplayed()
        compose.onNodeWithText("Đang theo dõi: Sang · sẵn sàng nhận cử chỉ").assertIsDisplayed()
        compose.runOnIdle { state=state.copy(trackedPeople=listOf(tracked.copy(visible=false,source=TrackingSource.OCCLUDED))) }
        compose.onNodeWithTag("recognized-people").assertDoesNotExist()
        compose.onNodeWithText("Tạm mất dấu người quen · chờ xác nhận lại").assertIsDisplayed()
        compose.runOnIdle { state=state.copy(trackedPeople=listOf(tracked.copy(personId=null,source=TrackingSource.UNCERTAIN))) }
        compose.onNodeWithTag("recognized-people").assertDoesNotExist()
        compose.onNodeWithText("Chưa nhận ra người quen · nhìn thẳng camera").assertIsDisplayed()
    }

    @Test fun dockReadinessRequiresExactlyOneEnrolledOwnerAndAwakeCamera() {
        val enrolled=Person("sang","Sang","anh Sang",faces=listOf(floatArrayOf(1f)))
        var state by mutableStateOf(IdentityUi(people=listOf(enrolled)))
        var awake by mutableStateOf(true)
        compose.setContent { MaterialTheme { GestureReadinessStatus(state,awake) } }
        compose.onNodeWithText("Đưa mặt và bàn tay vào camera để tương tác").assertIsDisplayed()
        compose.runOnIdle { state=state.copy(faces=listOf(FaceSeen(1,null,"Khách",0f))) }
        compose.onNodeWithText("Chưa nhận ra người quen · nhìn thẳng camera").assertIsDisplayed()
        compose.runOnIdle { state=state.copy(faces=listOf(FaceSeen(1,enrolled.id,enrolled.name,0f))) }
        compose.onNodeWithText("Đã nhận diện: Sang · sẵn sàng nhận cử chỉ").assertIsDisplayed()
        compose.runOnIdle { state=state.copy(gestureDecision=GestureDecision.CLIPPED_HAND) }
        compose.onNodeWithText("Đưa cả bàn tay vào hình · giơ tay cao hơn").assertIsDisplayed()
        compose.runOnIdle { state=state.copy(gestureDecision=GestureDecision.LOW_SCORE) }
        compose.onNodeWithText("Giữ rõ cử chỉ · hướng bàn tay về camera").assertIsDisplayed()
        compose.runOnIdle { state=state.copy(gestureDecision=GestureDecision.HOLDING) }
        compose.onNodeWithText("Đã thấy cử chỉ · giữ thêm một chút").assertIsDisplayed()
        compose.runOnIdle { state=state.copy(gestureDecision=GestureDecision.IDLE) }
        compose.runOnIdle { state=state.copy(faces=state.faces+FaceSeen(2,null,"Khách",.2f)) }
        compose.onNodeWithText("Chỉ để một người trong camera để nhận cử chỉ").assertIsDisplayed()
        compose.runOnIdle { state=state.copy(faces=state.faces.take(1),socialGesturesEnabled=false) }
        compose.onNodeWithText("Phản ứng cử chỉ đang tắt").assertIsDisplayed()
        compose.runOnIdle { awake=false }
        compose.onNodeWithText("Chạm mặt Ken để đánh thức và nhận cử chỉ").assertIsDisplayed()
        compose.runOnIdle { awake=true;state=state.copy(socialGesturesEnabled=true,people=emptyList()) }
        compose.onNodeWithText("Đăng ký khuôn mặt trong Người quen để tương tác cử chỉ").assertIsDisplayed()
    }

    @Test fun namesFollowRecognitionRenameAndRemoval() {
        val sang = Person("sang", "Nguyễn Văn Sang", "anh Sang")
        val lan = Person("lan", "Lan", "chị Lan")
        var state by mutableStateOf(IdentityUi(people = listOf(sang, lan)))
        compose.setContent { MaterialTheme { RecognizedPeopleStatus(state) } }
        compose.onNodeWithTag("recognized-people").assertDoesNotExist()

        compose.runOnIdle {
            state = state.copy(faces = listOf(FaceSeen(1, null, "Khách / Chưa xác định", 0f)))
        }
        compose.onNodeWithTag("recognized-people").assertDoesNotExist()

        compose.runOnIdle {
            state = state.copy(faces = listOf(
                FaceSeen(1, sang.id, sang.name, 0f),
                FaceSeen(2, sang.id, sang.name, .2f),
                FaceSeen(3, lan.id, lan.name, -.2f)
            ))
        }
        compose.onNodeWithText("Đã nhận diện: Nguyễn Văn Sang, Lan").assertIsDisplayed()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
            try {
                java.io.File(instrumentation.targetContext.filesDir, "recognized-people-test.png").outputStream().use {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { screenshot.recycle() }
        }

        compose.runOnIdle { state = state.copy(people = listOf(sang.copy(name = "Sáng"), lan)) }
        compose.onNodeWithText("Đã nhận diện: Sáng, Lan").assertIsDisplayed()

        compose.runOnIdle { state = state.copy(people = listOf(lan)) }
        compose.onNodeWithText("Đã nhận diện: Lan").assertIsDisplayed()

        compose.runOnIdle { state = state.copy(faces = emptyList()) }
        compose.onNodeWithTag("recognized-people").assertDoesNotExist()
    }
}
