package com.robotai.robot

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.robot.data.ConversationReadiness
import com.robotai.robot.identity.TalkNetActiveSpeaker
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndividualConversationPlatformTest {
    @Test fun exportedModelRunsOnAndroidRuntime() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ConversationReadiness.calibrationSession().use {
            TalkNetActiveSpeaker(context).use { model ->
                assertTrue(model.calibrated)
                // Shape/runtime validation only. This is never written as recognition accuracy.
                model.speaking(FloatArray(16000),List(25) { FloatArray(112*112) { 110f } })
            }
        }
        assertFalse(ConversationReadiness.passed(context,"not_a_capability"))
    }
    @Test fun interactiveAcceptanceSession() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue("Manual, supervised device benchmark",args.getString("conversation_calibration")=="true")
        val minutes=(args.getString("minutes")?.toIntOrNull() ?: 60).coerceIn(1,120)
        ConversationReadiness.calibrationSession().use {
            ActivityScenario.launch(MainActivity::class.java).use {
                // Operator supplies the 100 labelled utterances and 50 interruption attempts.
                // This keeps calibration enabled for measurement; it does not approve a device.
                repeat(minutes*6) { Thread.sleep(10000) }
            }
        }
    }
}
