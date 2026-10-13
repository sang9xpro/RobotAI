package com.robotai.robot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.domain.*
import com.robotai.robot.companion.CompanionFace
import com.robotai.robot.dock.DockFace
import com.robotai.robot.identity.*
import com.robotai.robot.motion.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class FacialExpressionPlatformTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext

    private class Fixture(val bitmap:Bitmap,val faces:List<Face>):AutoCloseable {
        override fun close() { bitmap.recycle() }
    }
    private fun fixture():Fixture {
        val bitmap=instrumentation.context.assets.open("tracking-face.jpg").use { BitmapFactory.decodeStream(it) }
        val detector=FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).enableTracking().build())
        try {
            val faces=Tasks.await(detector.process(InputImage.fromBitmap(bitmap,0)),5,TimeUnit.SECONDS)
            assertEquals(1,faces.size)
            return Fixture(bitmap,faces)
        } catch(e:Throwable) { bitmap.recycle();throw e }
        finally { detector.close() }
    }
    private fun modelScores(f:Fixture):FloatArray {
        val crop=FacialExpressionDetector.crop(f.bitmap,f.faces.single())
        assertNotNull("Fixture must pass face quality gate",crop)
        try { return FacialExpressionDetector(context).use { it.infer(crop!!) } }
        finally { crop?.recycle() }
    }

    @Test fun realPinnedModelRecognizesSmileAndKeepsSourceBitmapAlive() {
        fixture().use { f ->
            val crop=FacialExpressionDetector.crop(f.bitmap,f.faces.single())
            assertNotNull(crop)
            val times=mutableListOf<Long>()
            val scores=try { FacialExpressionDetector(context).use { model ->
                model.infer(crop!!)
                var last=floatArrayOf()
                repeat(20) {
                    val begin=SystemClock.elapsedRealtimeNanos()
                    last=model.infer(crop)
                    times.add((SystemClock.elapsedRealtimeNanos()-begin)/1000000)
                }
                last
            } } finally { crop?.recycle() }
            assertEquals(7,scores.size);assertEquals(1f,scores.sum(),.001f)
            assertTrue(scores.all { it.isFinite() && it in 0f..1f })
            assertEquals(FacialExpression.HAPPY.ordinal,scores.indices.maxByOrNull { scores[it] })
            assertTrue("Smile fixture score must be sufficient for a reaction: ${scores.toList()}",scores[3]>=.65f)
            assertFalse(f.bitmap.isRecycled)
            val metrics=JSONObject().put("sample","tracking-face.jpg").put("scores",org.json.JSONArray(scores.toList()))
                .put("inferenceSamples",times.size).put("inferenceP50Ms",times.sorted()[9]).put("inferenceP95Ms",times.sorted()[18])
                .put("modelSha256",FacialExpressionDetector.MODEL_SHA256)
            File(context.filesDir,"facial-expression-model-result.json").writeText(metrics.toString())
        }
    }

    @Test fun registeredSilentFaceReactsWithoutVoiceOrBackendAndClearsOnBodyMultiplePeopleAndStop() = runBlocking {
        fixture().use { f ->
            val scope="expression-pipeline-"+UUID.randomUUID()
            val embedding=FaceEmbedding(context).use { it.extract(f.bitmap,f.faces.single()) }
            assertNotNull(embedding)
            val person=PeopleStore.create("Người trong ảnh thử","bạn").copy(faces=List(8){embedding!!.copyOf()})
            val store=PeopleStore(context,scope)
            store.write(listOf(person))
            context.getSharedPreferences("identity-$scope",0).edit().putBoolean("socialGestures",false)
                .putBoolean("adaptiveLearning",false).commit()
            val controller=IdentityController(context,scope)
            val epoch=controller.startCamera()
            suspend fun waitForSmile() {
                val deadline=SystemClock.elapsedRealtime()+12000
                while(SystemClock.elapsedRealtime()<deadline) {
                    controller.analyze(f.bitmap,f.faces,epoch)
                    delay(220)
                    if(controller.state.value.facialExpression?.current(SystemClock.elapsedRealtime())==FacialExpression.HAPPY) return
                }
                fail("Silent face did not react: ${controller.state.value}")
            }
            try {
                val deadline=SystemClock.elapsedRealtime()+3000
                while(controller.state.value.people.isEmpty() && SystemClock.elapsedRealtime()<deadline) delay(20)
                waitForSmile()
                assertEquals(person.id,controller.state.value.facialExpression?.owner?.personId)
                assertTrue(controller.state.value.people.single().voices.isEmpty())
                assertEquals("Chưa xác định",controller.state.value.speaker)
                controller.analyze(f.bitmap,emptyList(),epoch)
                assertNull("Body identity must not keep a visible-face emotion",controller.state.value.facialExpression)
                waitForSmile()
                controller.analyze(f.bitmap,listOf(f.faces.single(),f.faces.single()),epoch)
                assertNull("Multiple faces must clear the previous reaction",controller.state.value.facialExpression)
                waitForSmile()
                controller.stopCamera(epoch)
                delay(150)
                assertNull(controller.state.value.facialExpression)
                controller.analyze(f.bitmap,f.faces,epoch)
                assertNull("Old camera epoch cannot revive the reaction",controller.state.value.facialExpression)
            } finally { controller.close();store.write(emptyList()) }
        }
    }

    @Test fun unregisteredFaceNeverGetsAnExpressionReaction() = runBlocking {
        fixture().use { f ->
            val controller=IdentityController(context,"expression-unknown-"+UUID.randomUUID())
            try {
                controller.enableSocialGestures(false)
                val epoch=controller.startCamera()
                repeat(6) { controller.analyze(f.bitmap,f.faces,epoch);delay(220) }
                assertTrue(controller.state.value.faces.isNotEmpty())
                assertTrue(controller.state.value.faces.all { it.personId==null })
                assertNull(controller.state.value.facialExpression)
            } finally { controller.close() }
        }
    }

    @Test fun expressionSwitchPersistsPerAccountWithoutDisablingIdentity() {
        val scope="expression-settings-"+UUID.randomUUID()
        val controller=IdentityController(context,scope)
        var reloaded:IdentityController?=null
        val other=IdentityController(context,"expression-other-"+UUID.randomUUID())
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                PeoplePanel(controller,true,{}, {})
            } } }
            compose.onNodeWithTag("facial-expressions-switch").assertIsOn().performClick()
            compose.waitUntil(3000) { !controller.state.value.expressionsEnabled }
            compose.onNodeWithText("Phản ứng biểu cảm đang tắt").assertIsDisplayed()
            assertNull(controller.state.value.facialExpression)
            assertTrue(controller.state.value.socialGesturesEnabled)
            reloaded=IdentityController(context,scope)
            assertFalse(reloaded.state.value.expressionsEnabled)
            assertTrue(other.state.value.expressionsEnabled)
        } finally { controller.close();reloaded?.close();other.close() }
    }

    @Test fun realModelMoodReachesSharedKenAndDockDuringQuietListening() {
        val scores=fixture().use { modelScores(it) }
        val engine=FacialExpressionEngine()
        val owner=ExpressionOwner("fixture",1,1)
        val at=SystemClock.elapsedRealtime()
        for(t in at-1000..at step 250) { engine.present(owner,t,0f);engine.observe(owner,scores,t,t) }
        val observed=engine.state!!
        assertEquals(FacialExpression.HAPPY,observed.current(at))
        var docked by mutableStateOf(false)
        val choice=VisualMoodPolicy.select(Mood.CURIOUS,Phase.LISTENING,observed,at,true,true,false)
        val input=KenMotionInput(Phase.LISTENING,choice.mood,visualMood=choice.fromCamera)
        var pose:KenPose?=null
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme { KenMotionHost(input) {
            val shared=sharedKenPose()
            SideEffect { pose=shared }
            Box(Modifier.fillMaxSize().background(KenColors.Background)) {
                // Deliberately pass CALM to children: the shared host owns the actual visual mood.
                if(docked) DockFace(Phase.LISTENING,0f,Modifier.fillMaxSize().testTag("expression-face"))
                else CompanionFace(Mood.CALM,Phase.LISTENING,0f,Modifier.fillMaxSize().testTag("expression-face"))
            }
        } } }
        for(dock in listOf(false,true)) {
            compose.runOnIdle { docked=dock }
            compose.mainClock.advanceTimeBy(1200)
            compose.onNodeWithTag("expression-face").assertIsDisplayed()
            assertEquals(Phase.LISTENING,pose?.phase)
            assertTrue("Quiet listening must preserve the camera smile",pose!!.happy>.8f)
            assertTrue(pose!!.smile>.9f)
            val bitmap=compose.onNodeWithTag("expression-face").captureToImage().asAndroidBitmap()
            try {
                val dir=File(context.filesDir,"expression-tests").apply { mkdirs() }
                File(dir,if(dock) "expression-dock-smile.png" else "expression-ken-smile.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                }
            } finally { bitmap.recycle() }
        }
    }
}
