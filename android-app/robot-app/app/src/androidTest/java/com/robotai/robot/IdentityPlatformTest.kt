package com.robotai.robot

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.robot.identity.*
import com.robotai.domain.IdentityMath
import ai.onnxruntime.*
import org.junit.*
import org.junit.Assert.*
import java.nio.*
import java.util.UUID
import kotlinx.coroutines.runBlocking

class IdentityPlatformTest {
    @get:Rule val compose=createComposeRule()
    @Test fun liveFaceEnrollmentRecognizesRegisteredNameAfterReload() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val scope="test-live-face-"+UUID.randomUUID()
        val controller=IdentityController(ctx,scope)
        var cameraController by mutableStateOf(controller)
        val store=PeopleStore(ctx,scope)
        try {
            controller.add("Người thử camera","bạn")
            compose.waitUntil(10000) { controller.state.value.people.size==1 }
            val id=controller.state.value.people.single().id
            compose.setContent { CameraHost(cameraController,true,false,true) }
            compose.waitUntil(30000) { controller.snapshot()!=null }
            // The emulator's virtual room camera has no face unless a fixture is supplied.
            // Keep this hardware test conditional; deterministic enrollment is covered separately.
            repeat(40) { if(controller.state.value.faces.isEmpty()) Thread.sleep(100) }
            Assume.assumeTrue("Camera source must contain a visible face for live enrollment",controller.state.value.faces.isNotEmpty())
            controller.enroll(id)
            compose.waitUntil(45000) { controller.state.value.people.single().faces.size==8 }
            assertNull(controller.state.value.enrollment)
            assertNull(controller.state.value.error)
            compose.waitUntil(15000) { controller.state.value.faces.any { it.personId==id && it.label=="Người thử camera" } }
            assertEquals(8,store.read().single().faces.size)
            val reloaded=IdentityController(ctx,scope)
            try {
                compose.runOnIdle { cameraController=reloaded }
                compose.waitUntil(30000) { reloaded.state.value.faces.any { it.personId==id && it.label=="Người thử camera" } }
            } finally { reloaded.close() }
        } finally { controller.close();store.write(emptyList()) }
    }
    @Test fun registerEditReloadAndDeleteVietnameseNameThroughUi() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val scope="test-name-"+UUID.randomUUID()
        val controller=IdentityController(ctx,scope)
        val store=PeopleStore(ctx,scope)
        try {
            compose.setContent {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PeoplePanel(controller,true,{}, {})
                }
            }
            compose.onNodeWithText("Thêm người").assertIsNotEnabled()
            compose.onNodeWithText("Tên").performTextReplacement("   ")
            compose.onNodeWithText("Thêm người").assertIsNotEnabled()
            compose.onNodeWithText("Tên").performTextReplacement("  Nguyễn Văn Sang  ")
            compose.onNodeWithText("Cách xưng hô (ví dụ: anh Sang)").performTextReplacement("  anh Sang  ")
            compose.onNodeWithText("Thêm người").assertIsEnabled().performClick()
            compose.waitUntil(10000) { controller.state.value.people.size==1 }
            val id=controller.state.value.people.single().id
            assertEquals("Nguyễn Văn Sang",store.read().single().name)
            assertEquals("anh Sang",store.read().single().address)
            compose.onNodeWithText("Sửa").performScrollTo().performClick()
            compose.onNodeWithText("Tên").performScrollTo().performTextReplacement("Sáng")
            compose.onNodeWithText("Cách xưng hô (ví dụ: anh Sang)").performTextReplacement("bạn Sáng")
            compose.onNodeWithText("Lưu thay đổi").performClick()
            compose.waitUntil(10000) { controller.state.value.people.single().name=="Sáng" }
            val reloaded=IdentityController(ctx,scope)
            try {
                compose.waitUntil(10000) { reloaded.state.value.people.size==1 }
                assertEquals(id,reloaded.state.value.people.single().id)
                assertEquals("bạn Sáng",reloaded.state.value.people.single().address)
            } finally { reloaded.close() }
            compose.onNodeWithText("Đăng ký mặt").performScrollTo().performClick()
            compose.waitUntil(10000) { controller.state.value.enrollment==id }
            compose.onNodeWithText("Hủy thu mặt").performScrollTo().performClick()
            compose.waitUntil(10000) { controller.state.value.enrollment==null }
            assertTrue(store.read().single().faces.isEmpty())
            compose.onNodeWithText("Xóa người").performScrollTo().performClick()
            compose.onNodeWithText("Xóa").performClick()
            compose.waitUntil(10000) { controller.state.value.people.isEmpty() }
            assertTrue(store.read().isEmpty())
            assertNull(controller.state.value.error)
        } finally { controller.close();store.write(emptyList()) }
    }
    @Test fun enrolledVoiceReturnsSavedNameAndClearsIdentityOnSilentTurn() = runBlocking {
        val i=InstrumentationRegistry.getInstrumentation();val ctx=i.targetContext
        val scope="test-speaker-"+UUID.randomUUID()
        val controller=IdentityController(ctx,scope)
        try {
            controller.add("Nguyễn Văn Sang","anh Sang")
            compose.waitUntil(10000) { controller.state.value.people.size==1 }
            val id=controller.state.value.people.single().id
            val wav=i.context.assets.open("identity-voice.wav").use { it.readBytes() }
            val buffer=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
            var offset=12;var start=0;var length=0
            while(offset+8<=wav.size) {
                val size=buffer.getInt(offset+4)
                if(String(wav,offset,4,Charsets.US_ASCII)=="data") { start=offset+8;length=size;break }
                offset+=8+size+(size%2)
            }
            assertTrue(length>32000)
            val samples=FloatArray(length/2) { buffer.getShort(start+it*2)/32768f }
            val pcm=ShortArray(length/2) { buffer.getShort(start+it*2) }
            repeat(2) { controller.saveVoice(id,samples) }
            assertNull(controller.state.value.error)
            controller.beginTurn();controller.audio(pcm)
            assertEquals("unknown",controller.finishTurn().getString("status"))
            controller.saveVoice(id,samples)
            assertNull(controller.state.value.error)
            assertEquals(3,PeopleStore(ctx,scope).read().single().voices.size)
            controller.beginTurn();controller.audio(pcm)
            val result=controller.finishTurn()
            assertEquals("recognized",result.getString("status"))
            assertEquals(id,result.getString("person_id"))
            assertEquals("Nguyễn Văn Sang",result.getString("name"))
            assertEquals("anh Sang",result.getString("address"))
            assertEquals("voice",result.getString("source"))
            controller.beginTurn();controller.audio(ShortArray(16000))
            val unknown=controller.finishTurn()
            assertEquals("unknown",unknown.getString("status"))
            assertEquals("",unknown.getString("name"))
            assertEquals("Chưa xác định",controller.state.value.speaker)
            controller.clearVoice(id)
            compose.waitUntil(10000) { controller.state.value.people.single().voices.isEmpty() }
            controller.beginTurn();controller.audio(pcm)
            assertEquals("unknown",controller.finishTurn().getString("status"))
        } finally { controller.close();PeopleStore(ctx,scope).write(emptyList()) }
    }
    @Test fun encryptedProfilesAreAccountScopedAndSurviveReload() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val scope="test-"+UUID.randomUUID();val store=PeopleStore(ctx,scope)
        val person=PeopleStore.create("Sang","anh Sang").copy(faces=listOf(floatArrayOf(1f,0f)))
        store.write(listOf(person));assertEquals(person.id,PeopleStore(ctx,scope).read().single().id)
        assertTrue(PeopleStore(ctx,scope+"-other").read().isEmpty())
        val bytes=java.io.File(ctx.noBackupFilesDir,"people-$scope.bin").readBytes()
        assertFalse(String(bytes,Charsets.ISO_8859_1).contains("Sang"))
        store.write(emptyList());assertTrue(store.read().isEmpty())
    }
    @Test fun nativeFaceAndVoiceRuntimesCoexist() {
        val i=InstrumentationRegistry.getInstrumentation();val ctx=i.targetContext
        val env=OrtEnvironment.getEnvironment()
        env.createSession(ctx.assets.open("identity/sface.onnx").use { it.readBytes() }).use { model ->
            OnnxTensor.createTensor(env,FloatBuffer.wrap(FloatArray(3*112*112) { 128f }),longArrayOf(1,3,112,112)).use { input ->
                model.run(mapOf(model.inputNames.first() to input)).use { output ->
                    @Suppress("UNCHECKED_CAST") val values=(output[0].value as Array<FloatArray>)[0]
                    assertEquals(128,values.size);assertTrue(values.all { it.isFinite() })
                }
            }
        }
        val wav=i.context.assets.open("identity-voice.wav").use { it.readBytes() }
        val buffer=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        var offset=12;var start=0;var length=0
        while(offset+8<=wav.size) {
            val size=buffer.getInt(offset+4)
            if(String(wav,offset,4,Charsets.US_ASCII)=="data") { start=offset+8;length=size;break }
            offset+=8+size+(size%2)
        }
        assertTrue(length>32000)
        val samples=FloatArray(length/2) { buffer.getShort(start+it*2)/32768f }
        VoiceEmbedding(ctx.assets).use { voice ->
            val windows=voice.windows(samples,true);assertTrue(windows.isNotEmpty())
            assertTrue(IdentityMath.cosine(windows.first(),windows.first())>.999f)
        }
    }
    @Test fun cameraRunsWithoutPreviewAndStopsOnSleep() {
        val i=InstrumentationRegistry.getInstrumentation();val ctx=i.targetContext

        val controller=IdentityController(ctx,"test-camera-"+UUID.randomUUID())
        var awake by mutableStateOf(true);var preview by mutableStateOf(false)
        try {
            compose.setContent { CameraHost(controller,awake,preview,true) }
            if(ctx.checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                compose.onNodeWithText("Cấp quyền camera để nhận diện khi Ken thức").performClick()
                grantPermissionDialog()
            }
            compose.waitUntil(45000) { controller.state.value.status.startsWith("Camera bật") && controller.snapshot()!=null }
            assertNotNull(controller.snapshot())
            compose.runOnIdle { preview=true }
            compose.waitUntil(15000) { controller.snapshot()!=null }
            compose.runOnIdle { preview=false;awake=false }
            compose.waitUntil(15000) { controller.state.value.status.contains("robot ngủ") }
            assertNull(controller.snapshot());assertTrue(controller.state.value.faces.isEmpty())
            compose.runOnIdle { awake=true }
            compose.waitUntil(20000) { controller.state.value.status.startsWith("Camera bật") && controller.snapshot()!=null }
        } finally { controller.close() }
    }
    private fun grantPermissionDialog() {
        val i=InstrumentationRegistry.getInstrumentation()
        val deadline=android.os.SystemClock.elapsedRealtime()+10000
        var granted=false
        while(!granted && android.os.SystemClock.elapsedRealtime()<deadline) {
            val root=i.uiAutomation.rootInActiveWindow
            val ids=listOf("com.android.permissioncontroller:id/permission_allow_foreground_only_button",
                "com.android.permissioncontroller:id/permission_allow_button",
                "com.google.android.permissioncontroller:id/permission_allow_foreground_only_button")
            val button=ids.firstNotNullOfOrNull { root?.findAccessibilityNodeInfosByViewId(it)?.firstOrNull() }
            if(button!=null) { granted=button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);button.recycle() }
            root?.recycle()
            if(!granted) Thread.sleep(200)
        }
        assertTrue("Could not grant permission using Android's dialog",granted)
    }
    @Test fun microphoneCapturesWhileCameraIsAnalyzing() {
        val i=InstrumentationRegistry.getInstrumentation();val ctx=i.targetContext
        val controller=IdentityController(ctx,"test-microphone-"+UUID.randomUUID())
        val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Default)
        val mic=com.robotai.robot.data.Microphone(scope)
        val count=java.util.concurrent.atomic.AtomicInteger()
        val peak=java.util.concurrent.atomic.AtomicInteger()
        val error=java.util.concurrent.atomic.AtomicReference<String?>()
        try {
            compose.setContent {
                val request=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                androidx.compose.foundation.layout.Column {
                    CameraHost(controller,true,false,true)
                    androidx.compose.material3.TextButton(onClick={request.launch(android.Manifest.permission.RECORD_AUDIO)}) { androidx.compose.material3.Text("Cấp quyền micro kiểm thử") }
                }
            }
            if(ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                compose.onNodeWithText("Cấp quyền micro kiểm thử").performClick();grantPermissionDialog()
            }
            compose.waitUntil(30000) { controller.snapshot()!=null }
            mic.start({}, {}, {error.set(it)}, {samples ->
                count.addAndGet(samples.size)
                samples.forEach { sample -> peak.accumulateAndGet(kotlin.math.abs(sample.toInt()),Math::max) }
            })
            Thread.sleep(3000)
            runBlocking { mic.stop() }
            assertNull(error.get());assertTrue("No real PCM from microphone",count.get()>=32000)
            assertNotNull(controller.snapshot())
            val result=org.json.JSONObject().put("device",android.os.Build.MODEL).put("sampleRate",16000)
                .put("pcmSamples",count.get()).put("peak",peak.get()).put("cameraStatus",controller.state.value.status)
            java.io.File(ctx.filesDir,"identity-microphone-result.json").writeText(result.toString(2))
        } finally { runBlocking { mic.stop() };scope.coroutineContext[kotlinx.coroutines.Job]?.cancel();controller.close() }
    }

}
