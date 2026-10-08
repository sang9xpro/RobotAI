package com.whispercppdemo

import com.whispercppdemo.speaker.VoiceMath
import org.junit.Assert.*
import org.junit.Test

class VoiceMathTest {
    @Test fun sameDirectionMatchesButDifferentSpeakerWindowRejectsTurn() {
        assertEquals(1f, VoiceMath.cosine(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f)), 0.0001f)
        assertEquals(0f, VoiceMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.0001f)
        assertFalse(VoiceMath.accepts(listOf(0.95f, 0.2f), 0.6f))
        assertTrue(VoiceMath.accepts(listOf(0.6f, 0.8f), 0.6f))
        assertFalse(VoiceMath.accepts(emptyList(), 0.6f))
        assertFalse(VoiceMath.accepts(listOf(Float.NaN), 0.6f))
    }

    @Test fun enrollmentDoesNotLetLouderEmbeddingDominate() {
        val mean = VoiceMath.centroid(listOf(floatArrayOf(100f, 0f), floatArrayOf(0f, 1f)))
        assertEquals(mean[0], mean[1], 0.0001f)
    }

    @Test fun windowsRetainShortTail() {
        val audio = FloatArray(16000 * 7) { it.toFloat() }
        val windows = VoiceMath.windows(audio)
        assertEquals(audio.size, windows.sumOf { it.size })
        assertEquals(audio.last(), windows.last().last(), 0f)
        assertTrue(windows.all { it.size >= 16000 * 2 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun silenceCannotEnrollOrVerify() { VoiceMath.prepareAudio(FloatArray(16000 * 5)) }

    @Test(expected = IllegalArgumentException::class)
    fun zeroEmbeddingCannotMatch() { VoiceMath.normalize(floatArrayOf(0f, 0f)) }
}
