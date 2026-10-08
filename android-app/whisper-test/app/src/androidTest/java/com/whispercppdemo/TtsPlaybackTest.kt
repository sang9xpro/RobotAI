package com.whispercppdemo

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whispercppdemo.tts.TtsViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TtsPlaybackTest {
    @Test fun audioTrackPlaysWhileModelIsGenerating() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val app = inst.targetContext.applicationContext as Application
        val store = ViewModelStore()
        lateinit var vm: TtsViewModel
        inst.runOnMainSync {
            vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[TtsViewModel::class.java]
            vm.setText("Xin chào, mình là robot của bạn."); vm.speak()
        }
        try {
            val deadline = SystemClock.elapsedRealtime() + 60000
            var playedDuringGeneration = false
            while (vm.state.value.busy && SystemClock.elapsedRealtime() < deadline) {
                val state = vm.state.value
                if (state.playing && state.busy) playedDuringGeneration = true
                SystemClock.sleep(20)
            }
            assertFalse("Timed out: ${vm.state.value.status}", vm.state.value.busy)
            assertTrue("AudioTrack never played during generation", playedDuringGeneration)
            assertEquals("Phát xong", vm.state.value.status)
            assertTrue(vm.state.value.hasAudio)
            File(app.cacheDir, "tts-playback-metrics.txt").writeText(vm.state.value.metrics)
            inst.runOnMainSync { vm.setText("Xin chào bạn. Hôm nay bạn cảm thấy như thế nào?"); vm.speak() }
            SystemClock.sleep(800)
            inst.runOnMainSync { vm.stop() }
            val cancelDeadline = SystemClock.elapsedRealtime() + 15000
            while (vm.state.value.busy && SystemClock.elapsedRealtime() < cancelDeadline) SystemClock.sleep(20)
            assertFalse(vm.state.value.busy); assertFalse(vm.state.value.playing)
        } finally { inst.runOnMainSync { store.clear() } }
    }
}
