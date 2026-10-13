package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class SpeakerAudioWindowsTest {
    private fun speech(seconds: Float, value: Float = .2f) = FloatArray((seconds * 16000).toInt()) { value }

    @Test fun naturalPausesDoNotRejectAnEnrollmentWithEnoughTotalSpeech() {
        val parts = listOf(speech(.8f), speech(.9f), speech(.7f), speech(1f))
        val windows = SpeakerAudioWindows.prepare(parts, true)
        assertEquals(54400, windows.sumOf { it.size })
        assertTrue(windows.all { it.size in 32000..80000 })
    }
    @Test fun conversationDoesNotIgnoreABriefPotentialSecondSpeaker() {
        try {
            SpeakerAudioWindows.prepare(listOf(speech(3f, .4f), speech(.4f)), false)
            fail("Only enrollment may combine fragments from one declared speaker")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun enrollmentKeepsTheSpeechInShortTailsAndBalancedLongWindows() {
        val parts = listOf(speech(5f, .3f), speech(.4f, .5f))
        val windows = SpeakerAudioWindows.prepare(parts, true)
        assertTrue(windows.all { it.size in 32000..80000 })
        val actual = windows.flatMap { it.toList() }
        assertEquals(86400, actual.size)
        assertEquals(.3f, actual.first(), 0f)
        assertEquals(.5f, actual.last(), 0f)
    }
    @Test fun tooLittleVoicedAudioStillCannotEnroll() {
        try {
            SpeakerAudioWindows.prepare(listOf(speech(.8f), speech(1f), FloatArray(0)), true)
            fail("Silent recording time must not count toward the speech minimum")
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("3 giây")) }
    }
    @Test fun missingSpeechAndOnlyBriefConversationFragmentsCannotIdentifyAnyone() {
        for (parts in listOf(emptyList(), listOf(speech(.4f), speech(.5f)))) {
            try { SpeakerAudioWindows.prepare(parts, false); fail("Insufficient speaker evidence") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
