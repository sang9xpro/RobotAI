package com.robotai.robot

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.domain.*
import com.robotai.robot.companion.GestureEffects
import com.robotai.robot.motion.*
import com.robotai.robot.data.StreamPlayer
import io.github.jaredmdobson.concentus.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sin

class KenAnimationPlatformTest {
    @get:Rule val compose=createComposeRule()

    @Test fun sharedRendererWorksInBothOrientationsAndAllConversationPhases() {
        var input by mutableStateOf(KenMotionInput())
        compose.mainClock.autoAdvance=false
        compose.setContent {
            MaterialTheme { KenMotionHost(input) {
                Column(Modifier.fillMaxSize().background(KenColors.Background).padding(16.dp)) {
                    KenFace(input,Modifier.fillMaxWidth().height(240.dp).testTag("native-landscape"))
                    KenFace(input,Modifier.width(150.dp).height(210.dp).testTag("native-portrait"))
                }
            } }
        }
        var awakePixels=0
        for(phase in Phase.values()) {
            compose.runOnIdle { input=KenMotionInput(phase,if(phase==Phase.SPEAKING)Mood.HAPPY else Mood.CALM,speechLevel=.7f) }
            compose.mainClock.advanceTimeBy(1500)
            compose.onNodeWithTag("native-portrait").assertIsDisplayed()
            val bitmap=compose.onNodeWithTag("native-landscape").captureToImage().asAndroidBitmap()
            try {
                save(bitmap,"ken-native-${phase.name.lowercase()}.png")
                val pixels=IntArray(bitmap.width*bitmap.height)
                bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                val lit=pixels.count { android.graphics.Color.green(it)>110 && android.graphics.Color.blue(it)>80 }
                if(phase==Phase.IDLE){awakePixels=lit;assertTrue(lit>1000)}
                if(phase==Phase.SLEEPING)assertTrue("Sleeping face should be dim and closed",lit<awakePixels/10)
            } finally { bitmap.recycle() }
        }
    }

    @Test fun vectorReactionsExpireAndDoNotBlockConversationControls() {
        var reaction by mutableStateOf<GestureReaction?>(null)
        var taps=0
        compose.mainClock.autoAdvance=false
        compose.setContent {
            val input=KenMotionInput(Phase.SPEAKING,Mood.CARING,speechLevel=.65f,
                reaction=reaction?.let { KenReaction.valueOf(it.kind.name) },reactionAtMs=reaction?.atMs ?: -1L)
            MaterialTheme { KenMotionHost(input) {
                Box(Modifier.fillMaxSize().background(KenColors.Background)) {
                    Column {
                        KenFace(input,Modifier.fillMaxWidth().height(300.dp).testTag("reacting-face"))
                        Button(onClick={taps++},modifier=Modifier.testTag("conversation-control")) { Text("Ngắt lời") }
                    }
                    GestureEffects(reaction,"Người duyệt")
                }
            } }
        }
        for(kind in SocialGesture.values()) {
            compose.runOnIdle { reaction=GestureReaction(kind,"review",SystemClock.elapsedRealtime()) }
            compose.mainClock.advanceTimeBy(650)
            compose.onNodeWithTag("gesture-effect-${kind.name}").assertExists()
            compose.onNodeWithTag("conversation-control").performClick()
            if(kind==SocialGesture.TWO_HAND_HEART) {
                val bitmap=compose.onNodeWithTag("reacting-face").captureToImage().asAndroidBitmap()
                try {
                    save(bitmap,"ken-native-heart.png")
                    val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                    assertTrue("Heart eyes must be pink",pixels.count { android.graphics.Color.red(it)>150 && android.graphics.Color.blue(it)>100 }>1000)
                } finally { bitmap.recycle() }
            }
        }
        assertEquals(12,taps)
        compose.mainClock.advanceTimeBy(3200)
        compose.onNodeWithTag("gesture-effect-ROCK_ON").assertDoesNotExist()
    }

    @Test fun audioTrackMeterFollowsSoundAndSilenceAndClearsOnStop() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val player=StreamPlayer(scope)
        try {
            for(rate in listOf(16000,24000)) {
                player.configure(rate)
                val done=CountDownLatch(1);val failure=AtomicReference<String?>(null)
                val audible=AtomicBoolean(false);val silentAfterSound=AtomicBoolean(false)
                val peak=AtomicReference(0f)
                val collector=scope.launch(Dispatchers.IO) { player.speechLevel.collect { level->
                    peak.set(maxOf(peak.get(),level))
                    if(level>.2f)audible.set(true)
                    if(audible.get() && level<.02f)silentAfterSound.set(true)
                } }
                player.start({}, {done.countDown()}, {failure.set(it);done.countDown()})
                // Opus packets are rate independent. Generate the fixture at 16 kHz;
                // Concentus's 24 kHz encoder attenuates this tone before decoding.
                val sourceRate=16000
                val encoder=OpusEncoder(sourceRate,1,OpusApplication.OPUS_APPLICATION_VOIP)
                val verifier=OpusDecoder(rate,1);var decodedPeak=0f
                val bytes=ByteArray(4096)
                repeat(20) { seq->
                    val pcm=ShortArray(sourceRate*60/1000) { if(seq<12)(sin((seq*sourceRate*.06+it)*2*Math.PI*260/sourceRate)*3000).toInt().toShort() else 0 }
                    val n=encoder.encode(pcm,0,pcm.size,bytes,0,bytes.size)
                    val decoded=ShortArray(rate*120/1000)
                    val count=verifier.decode(bytes,0,n,decoded,0,decoded.size,false)
                    decodedPeak=maxOf(decodedPeak,PcmSpeechEnvelope.level(decoded,0,count))
                    val packet=bytes.copyOf(n);val deadline=SystemClock.elapsedRealtime()+3000
                    while(!player.offer(packet)) { assertTrue("Audio queue stalled",SystemClock.elapsedRealtime()<deadline);Thread.sleep(10) }
                }
                assertTrue("Audio fixture must contain sound",decodedPeak>.2f)
                assertTrue(player.finish());assertTrue(done.await(5,TimeUnit.SECONDS));assertNull(failure.get())
                assertTrue("PCM sound was never presented: rate=$rate decoded=$decodedPeak observed=${peak.get()}",audible.get());assertTrue("Silent PCM should close mouth",silentAfterSound.get())
                collector.cancel();player.stop();assertEquals(0f,player.speechLevel.value,0f)
                assertFalse(player.offer(byteArrayOf(1)))
            }
        } finally { player.stop();scope.cancel() }
    }

    private fun save(bitmap:Bitmap,name:String) {
        val directory=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"ken-motion")
        directory.mkdirs()
        File(directory,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
