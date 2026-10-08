package com.robotai.robot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.domain.*
import com.robotai.robot.companion.*
import com.robotai.robot.dock.DockFace
import com.robotai.robot.identity.SocialGestureDetector
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class GestureInteractionPlatformTest {
    @get:Rule val compose = createComposeRule()

    @Test fun videoTrackingReleasesLikeWhenHandLeavesFrame() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val thumb=instrumentation.context.assets.open("gesture-thumb-up.jpg").use { BitmapFactory.decodeStream(it) }
        val empty=Bitmap.createBitmap(thumb.width,thumb.height,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
        try { SocialGestureDetector(instrumentation.targetContext).use { detector ->
            var hands=emptyList<GestureHand>()
            repeat(3) { hands=detector.detect(thumb,1000L+it*300) }
            assertTrue(hands.any { it.category=="Thumb_Up" && it.score>=SocialGestureEngine.MIN_THUMB_SCORE })
            repeat(4) { hands=detector.detect(empty,2200L+it*300) }
            assertTrue("A removed hand must not remain in VIDEO tracking",hands.isEmpty())
            assertFalse(thumb.isRecycled);assertFalse(empty.isRecycled)
        } } finally { thumb.recycle();empty.recycle() }
    }

    @Test fun realThumbModelEventRendersLikeOnKenAndDock() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=instrumentation.context.assets.open("gesture-thumb-up.jpg").use { BitmapFactory.decodeStream(it) }
        val hands=try { SocialGestureDetector(instrumentation.targetContext).use { it.detect(bitmap) } } finally { bitmap.recycle() }
        val engine=SocialGestureEngine()
        val owner=GestureOwner("fixture",.25f,.02f,.75f,.35f)
        val now=SystemClock.elapsedRealtime()
        assertNull(engine.observe(listOf(owner),hands,now-750))
        val event=engine.observe(listOf(owner),hands,now)
        assertEquals(SocialGesture.THUMBS_UP,event?.kind)
        var docked by mutableStateOf(false)
        var reaction by mutableStateOf(event)
        compose.mainClock.autoAdvance=false
        compose.setContent {
            MaterialTheme { Box(Modifier.fillMaxSize().background(Color(0xff08111b))) {
                if(docked) DockFace(Phase.IDLE,0f,Modifier.fillMaxSize(),happy=true)
                else CompanionFace(Mood.HAPPY,Phase.IDLE,0f,Modifier.fillMaxWidth().fillMaxHeight(.65f))
                GestureEffects(reaction,"Người kiểm thử")
            } }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("gesture-effect-THUMBS_UP").assertIsDisplayed()
        compose.onNodeWithText("👍").assertIsDisplayed()
        compose.onNodeWithText("Cảm ơn Người kiểm thử!").assertIsDisplayed()
        screenshot("gesture-ken-like.png")
        compose.runOnIdle { docked=true;reaction=event!!.copy(atMs=SystemClock.elapsedRealtime()) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("👍").assertIsDisplayed()
        screenshot("gesture-dock-like.png")
    }

    @Test fun allReactionsShowNamesAndExpireWithoutBlockingTouches() {
        var reaction by mutableStateOf<GestureReaction?>(null)
        var personName by mutableStateOf<String?>("Nguyễn Văn Sang")
        var taps = 0
        compose.mainClock.autoAdvance=false
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color(0xff08111b))) {
                    Button(onClick={taps++},modifier=Modifier.align(Alignment.Center).testTag("underlying-button")) { Text("Chạm") }
                    GestureEffects(reaction,personName)
                }
            }
        }
        for (kind in SocialGesture.values()) {
            compose.runOnIdle { reaction=GestureReaction(kind,"sang",SystemClock.elapsedRealtime()) }
            compose.mainClock.advanceTimeBy(150)
            compose.onNodeWithTag("gesture-effect-${kind.name}").assertExists()
            compose.onNodeWithText(gestureCaption(kind,"Nguyễn Văn Sang")).assertIsDisplayed()
            compose.onNodeWithTag("underlying-button").performClick()
        }
        assertEquals(SocialGesture.values().size,taps)
        compose.runOnIdle { personName=null }
        compose.mainClock.advanceTimeBy(32)
        compose.onAllNodes(hasTestTag("gesture-effect-FIST_BUMP")).assertCountEquals(0)
        compose.runOnIdle { personName="Nguyễn Văn Sang";reaction=GestureReaction(SocialGesture.FINGER_HEART,"sang",SystemClock.elapsedRealtime()) }
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithTag("gesture-effect-FINGER_HEART").assertExists()
        compose.mainClock.advanceTimeBy(3200)
        compose.onNodeWithTag("gesture-effect-FINGER_HEART").assertDoesNotExist()
    }

    @Test fun heartEffectWorksOnKenAndDockFaces() {
        var docked by mutableStateOf(false)
        var reaction by mutableStateOf(GestureReaction(SocialGesture.FINGER_HEART,"sang",SystemClock.elapsedRealtime()))
        compose.mainClock.autoAdvance=false
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color(0xff08111b))) {
                    if(docked) DockFace(Phase.IDLE,0f,Modifier.fillMaxSize().testTag("dock-face"),loving=true,happy=true)
                    else CompanionFace(Mood.HAPPY,Phase.IDLE,0f,Modifier.fillMaxWidth().fillMaxHeight(.65f).testTag("companion-face"),loving=true)
                    GestureEffects(reaction,"Nguyễn Văn Sang")
                }
            }
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("companion-face").assertIsDisplayed()
        compose.onNodeWithTag("gesture-effect-FINGER_HEART").assertExists()
        screenshot("gesture-ken-heart.png")
        compose.runOnIdle { docked=true;reaction=GestureReaction(SocialGesture.TWO_HAND_HEART,"sang",SystemClock.elapsedRealtime()) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("dock-face").assertIsDisplayed()
        compose.onNodeWithTag("gesture-effect-TWO_HAND_HEART").assertExists()
        screenshot("gesture-dock-heart.png")
    }

    @Test fun bundledModelRecognizesPhotoAndSupportsTwoHandsWithoutRecyclingInput() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        SocialGestureDetector(instrumentation.targetContext).use { detector ->
            val thumb=instrumentation.context.assets.open("gesture-thumb-up.jpg").use { BitmapFactory.decodeStream(it) }
            try {
                val hands=detector.detect(thumb)
                assertEquals(1,hands.size)
                assertEquals("Thumb_Up",hands.single().category)
                File(instrumentation.targetContext.filesDir,"gesture-model-score.txt").writeText(hands.single().score.toString())
                assertTrue("Thumb_Up score=${hands.single().score}",hands.single().score>=.65f)
                assertEquals(21,hands.single().points.size)
                assertFalse(thumb.isRecycled)
                assertFalse(SocialGestureGeometry.fingerHeart(hands.single()))
                val engine=SocialGestureEngine()
                val owner=GestureOwner("sang",.25f,.02f,.75f,.35f)
                listOf(0L,100L,200L).forEach { assertNull(engine.observe(listOf(owner),hands,it)) }
                assertEquals(SocialGesture.THUMBS_UP,engine.observe(listOf(owner),hands,750)?.kind)
            } finally { thumb.recycle() }
            val two=instrumentation.context.assets.open("gesture-two-hands.jpg").use { BitmapFactory.decodeStream(it) }
            try {
                val hands=detector.detect(two)
                assertEquals(2,hands.size)
                assertTrue(hands.all { it.points.size==21 })
                assertFalse(SocialGestureGeometry.twoHandHeart(hands))
                assertFalse(two.isRecycled)
            } finally { two.recycle() }
        }
    }

    private fun screenshot(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        try { File(instrumentation.targetContext.filesDir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { bitmap.recycle() }
    }
}
