package com.robotai.robot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.domain.*
import com.robotai.robot.identity.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher

class PersonTrackingPlatformTest {
    @Test fun realFaceModelEnrollsReloadsAndKeepsBodyWhenFaceResultsDisappear() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val ctx=instrumentation.targetContext
        val scope="tracking-face-pipeline-"+UUID.randomUUID()
        val bitmap=instrumentation.context.assets.open("tracking-face.jpg").use { BitmapFactory.decodeStream(it) }
        val detector=com.google.mlkit.vision.face.FaceDetection.getClient(com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
            .setLandmarkMode(com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_ALL).enableTracking().build())
        val controller=IdentityController(ctx,scope)
        var reloaded: IdentityController?=null
        val store=PeopleStore(ctx,scope)
        try {
            val faces=com.google.android.gms.tasks.Tasks.await(detector.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap,0)),5,java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(1,faces.size)
            controller.add("Người trong ảnh mẫu","bạn")
            repeat(100) { if(controller.state.value.people.isEmpty()) delay(20) }
            val id=controller.state.value.people.single().id
            controller.enroll(id)
            val epoch=controller.startCamera()
            repeat(8) { controller.analyze(bitmap,faces,epoch);delay(550) }
            assertEquals("Enrollment pipeline must persist eight original templates",8,store.read().single().faces.size)
            repeat(4) { controller.analyze(bitmap,faces,epoch);delay(250) }
            assertEquals(id,controller.state.value.trackedPeople.single { it.visible }.personId)
            repeat(8) { controller.analyze(bitmap,emptyList(),epoch);delay(250) }
            val body=controller.state.value.trackedPeople.single { it.visible }
            assertEquals(id,body.personId);assertEquals(TrackingSource.BODY,body.source)
            assertTrue(controller.state.value.faces.isEmpty())
            reloaded=IdentityController(ctx,scope)
            val loaded=reloaded!!
            val nextEpoch=loaded.startCamera()
            repeat(4) { loaded.analyze(bitmap,faces,nextEpoch);delay(250) }
            assertEquals(id,loaded.state.value.trackedPeople.single { it.visible }.personId)
            assertNull(loaded.state.value.error)
        } finally { reloaded?.close();controller.close();detector.close();bitmap.recycle();store.write(emptyList()) }
    }
    @Test fun realBodyAndPoseModelsFeedContinuousTrackingWithoutFace() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val ctx=instrumentation.targetContext
        val bitmap=instrumentation.context.assets.open("tracking-person.jpg").use { BitmapFactory.decodeStream(it) }
        try { SceneVision(ctx).use { vision ->
            val tracker=PersonTracker()
            val report=JSONObject()
            for(index in 0..6) {
                val started=android.os.SystemClock.elapsedRealtime()
                val scene=vision.detect(bitmap,1000L+index*500)
                assertEquals("Expected the person in Google's pose sample",1,scene.people.size)
                if(index % 2 == 0) assertTrue("Fresh pose should locate a wrist",scene.people.single().wrists.isNotEmpty())
                assertEquals(72,scene.people.single().appearance.size)
                assertFalse(bitmap.isRecycled)
                val observations=scene.people.map { body -> body.copy(face=if(index<3)
                    FaceEvidence(1,TrackBox(body.box.left,body.box.top,body.box.right,body.box.top+body.box.height*.25f),IdentityMatch("registered",.95f)) else null) }
                val person=tracker.update(observations,1000L+index*500,setOf("registered"),scene.changed).single { it.visible }
                if(index>=2) assertEquals("registered",person.personId)
                if(index>=3) assertEquals(TrackingSource.BODY,person.source)
                report.put("people",scene.people.size).put("wrists",scene.people.single().wrists.size)
                    .put("analysisMs",android.os.SystemClock.elapsedRealtime()-started).put("source",person.source.name)
            }
            File(ctx.filesDir,"tracking-model-result.json").writeText(report.toString())
        } } finally { bitmap.recycle() }
    }
    @Test fun emptyFrameDoesNotCreatePersonAndAppearanceUsesBodyColor() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val black=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val blue=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try {
            SceneVision(ctx).use { assertTrue(it.detect(black,0).people.isEmpty()) }
            val b=TrackBox(0f,0f,1f,1f)
            assertEquals(1f,IdentityMath.cosine(SceneVision.appearance(black,b),SceneVision.appearance(black,b)),.001f)
            assertTrue(IdentityMath.cosine(SceneVision.appearance(black,b),SceneVision.appearance(blue,b))<.2f)
        } finally { black.recycle();blue.recycle() }
    }
    @Test fun encryptedLearningPreservesAnchorsAndCanRollback() = runBlocking {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val scope="tracking-learning-"+UUID.randomUUID()
        val original=floatArrayOf(1f,0f)
        val newer=floatArrayOf(.9f,.43f)
        val person=Person("person","Sang","anh Sang",faces=listOf(original),
            learnedFaces=listOf(newer),previousLearnedFaces=emptyList(),learningRevision=1,learnedAt=100,canUndoLearning=true)
        val store=PeopleStore(ctx,scope)
        store.write(listOf(person))
        val controller=IdentityController(ctx,scope)
        try {
            repeat(100) { if(controller.state.value.people.isEmpty()) delay(20) }
            assertEquals(1,controller.state.value.people.single().learnedFaces.size)
            assertArrayEquals(original,store.read().single().faces.single(),.00001f)
            controller.rollbackLearning(person.id)
            repeat(100) { if(controller.state.value.people.single().learningRevision==1) delay(20) }
            assertTrue(store.read().single().learnedFaces.isEmpty())
            assertFalse(store.read().single().canUndoLearning)
            assertArrayEquals(original,store.read().single().faces.single(),.00001f)
        } finally { controller.close();store.write(emptyList()) }
    }
    @Test fun existingEncryptedVersionOneMigratesWithoutChangingEnrollment() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val scope="tracking-migration-"+UUID.randomUUID()
        val store=PeopleStore(ctx,scope)
        store.write(listOf(Person("legacy","Người quen","bạn",faces=listOf(floatArrayOf(1f,0f)),voices=listOf(floatArrayOf(0f,1f)))))
        try {
            val file=File(ctx.noBackupFilesDir,"people-$scope.bin")
            val key=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey("robotai-people-$scope",null)
            val legacy=JSONObject().put("version",1).put("faceModel",PeopleStore.FACE_MODEL)
                .put("people",org.json.JSONArray().put(JSONObject().put("id","legacy").put("name","Người quen").put("address","bạn")
                    .put("faces",org.json.JSONArray("[[1,0]]")).put("voices",org.json.JSONArray("[[0,1]]"))))
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key) }
            file.writeBytes(cipher.iv+cipher.doFinal(legacy.toString().toByteArray()))
            val loaded=store.read().single()
            assertEquals("Người quen",loaded.name);assertTrue(loaded.learnedFaces.isEmpty())
            store.write(listOf(loaded.copy(learnedFaces=listOf(floatArrayOf(.9f,.43f)),learningRevision=1)))
            assertArrayEquals(floatArrayOf(1f,0f),store.read().single().faces.single(),.00001f)
            assertArrayEquals(floatArrayOf(0f,1f),store.read().single().voices.single(),.00001f)
        } finally { store.write(emptyList()) }
    }
}
