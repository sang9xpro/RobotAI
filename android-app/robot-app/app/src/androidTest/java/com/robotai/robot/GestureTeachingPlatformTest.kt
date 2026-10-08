package com.robotai.robot

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.domain.*
import com.robotai.robot.identity.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class GestureTeachingPlatformTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val owner=GestureOwner("fixture",.25f,.05f,.75f,.35f,body=TrackBox(0f,0f,1f,1f))
    private fun dataset()=GestureLessonJson.decodeData(JSONObject(instrumentation.context.assets.open("lesson-dataset-fixture.json").bufferedReader().use { it.readText() }))
    private fun await(condition:()->Boolean) {
        val until=SystemClock.elapsedRealtime()+8000
        while(!condition() && SystemClock.elapsedRealtime()<until) Thread.sleep(30)
        assertTrue("Timed out waiting for teaching operation",condition())
    }
    private fun teacher(data:GestureLessonData=dataset()):GestureTeacher {
        val scope="teaching-test-${UUID.randomUUID()}"
        GestureLessonStore(context,scope).write(GestureLessonBook(data))
        return GestureTeacher(context,scope).also { t -> await { t.state.value.ready } }
    }

    @Test fun encryptedRoundTripRetainsRawPointsLabelsAndModelWithoutTouchingPeople() {
        val scope="teaching-test-${UUID.randomUUID()}"
        val people=PeopleStore(context,scope)
        people.write(listOf(Person("fixture","Mẫu kiểm thử","",faces=listOf(floatArrayOf(.2f,.8f)))))
        val data=dataset()
        val store=GestureLessonStore(context,scope)
        store.write(GestureLessonBook(data))
        val reopened=store.read().data
        assertEquals(data.definitions,reopened.definitions)
        assertEquals(data.examples,reopened.examples)
        val bytes=File(context.noBackupFilesDir,"gestures-$scope.bin").readBytes()
        assertFalse(String(bytes,Charsets.ISO_8859_1).contains("session-like"))
        assertEquals("Mẫu kiểm thử",people.read().single().name)
        assertArrayEquals(floatArrayOf(.2f,.8f),people.read().single().faces.single(),0f)
    }

    @Test fun realMediaPipeHandCanBeTaughtOnceUntilReleaseAndRejectsWrongOwner() {
        val bitmap=instrumentation.context.assets.open("gesture-thumb-up.jpg").use { BitmapFactory.decodeStream(it) }
        val hand=try { SocialGestureDetector(context).use { it.detect(bitmap).single() } } finally { bitmap.recycle() }
        assertNotNull(GestureFeatures.extract(hand))
        val definition=TaughtGesture("like","fixture","Like",SocialGesture.THUMBS_UP)
        teacher(GestureLessonData(definitions=listOf(definition))).use { t ->
            t.start("fixture","like");await { t.state.value.lesson.phase==LessonPhase.WAITING }
            for(now in 0L..3500 step 100) t.observe(listOf(owner),listOf(hand),now)
            assertEquals(1,t.state.value.data.examples.size)
            for(now in 3600L..4500 step 100) t.observe(listOf(owner),listOf(hand),now)
            assertEquals(1,t.state.value.data.examples.size)
            for(now in 4600L..5000 step 100) t.observe(listOf(owner),emptyList(),now)
            for(now in 5100L..8500 step 100) t.observe(listOf(owner.copy(id="stranger")),listOf(hand),now)
            assertEquals(1,t.state.value.data.examples.size)
        }
    }

    @Test fun trainedPythonModelImportsPredictsActivatesAndRollsBackWithOwnerIsolation() {
        val root=JSONObject(instrumentation.context.assets.open("lesson-model-fixture.json").bufferedReader().use { it.readText() })
        val file=File(context.cacheDir,"lesson-model-test.json")
        teacher().use { t ->
            try {
                file.writeText(root.toString());t.importModel("fixture",Uri.fromFile(file))
                await { t.state.value.models.size==1 }
                val model=t.state.value.models.single().model
                t.activate("fixture",model.id);await { t.state.value.active["fixture"]==model.id }
                val like=dataset().examples.first { it.gestureId=="like" }.frames.first().hand()
                assertEquals("like",t.predict("fixture",like)?.definition?.id)
                assertNull(t.predict("someone-else",like))
                val neutral=dataset().examples.first { it.gestureId=="neutral" }.frames.first().hand()
                assertTrue(t.rejects("fixture",neutral));assertFalse(t.rejects("someone-else",neutral))
                t.activate("fixture",null);await { t.state.value.active["fixture"]==null }
                assertEquals(model.id,t.state.value.previous["fixture"])
                t.rollback("fixture");await { t.state.value.active["fixture"]==model.id }
                val altered=JSONObject(root.toString());altered.getJSONObject("model").put("personId","someone-else")
                file.writeText(altered.toString());t.importModel("fixture",Uri.fromFile(file))
                await { t.state.value.error!=null }
                assertEquals(1,t.state.value.models.size)
                assertEquals(model.id,t.state.value.active["fixture"])
            } finally { file.delete() }
        }
    }

    @Test fun feedbackFreezesItsSampleAndCannotSaveAfterOwnerDisappears() {
        teacher().use { t ->
            val data=dataset();val like=data.examples.first { it.gestureId=="like" }.frames.first().hand()
            val rock=data.examples.first { it.gestureId=="rock" }.frames.first().hand()
            t.start("fixture","like",true);await { t.state.value.lesson.phase==LessonPhase.TESTING }
            for(now in 0L..600 step 100) t.observe(listOf(owner),listOf(like),now)
            assertTrue(t.state.value.lesson.canConfirm)
            t.observe(listOf(owner),listOf(rock),700)
            assertEquals("like",t.state.value.lesson.predictionId)
            t.feedback(true);await { t.state.value.data.examples.size==data.examples.size+1 }
            val saved=t.state.value.data.examples.last()
            assertEquals("like",saved.gestureId)
            assertTrue(GestureFeatures.distance(GestureFeatures.center(saved)!!,GestureFeatures.extract(like)!!)<.01f)
            for(now in 800L..1300 step 100) t.observe(listOf(owner),listOf(rock),now)
            t.observe(listOf(owner.copy(id=null)),listOf(rock),1400)
            assertFalse(t.state.value.lesson.canConfirm)
            t.feedback(true);await { t.state.value.error!=null }
            assertEquals(data.examples.size+1,t.state.value.data.examples.size)
        }
    }

    @Test fun lessonUiShowsProgressGuidanceAndLandmarks() {
        compose.setContent { MaterialTheme { GestureLessonStatus(GestureLessonUi(
            personId="fixture",gestureId="like",phase=LessonPhase.RELEASE,collected=2,
            message="Đã thu một lượt · hạ tay khỏi hình để tiếp tục",progress=1f,
            hands=listOf(dataset().examples.first().frames.first().hand()))) } }
        compose.onNodeWithText("2/5 lượt").assertIsDisplayed()
        compose.onNodeWithText("Đã thu một lượt · hạ tay khỏi hình để tiếp tục").assertIsDisplayed()
        compose.onNodeWithTag("lesson-landmarks").assertIsDisplayed()
    }
}
