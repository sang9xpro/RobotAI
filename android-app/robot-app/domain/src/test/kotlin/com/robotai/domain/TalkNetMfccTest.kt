package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class TalkNetMfccTest {
    @Test fun matchesOfficialPythonPreprocessingReference() {
        // Generated with python_speech_features 0.6, float32 input then PCM16 scaling.
        val audio=FloatArray(16000) { i -> (.3*sin(2*PI*440*i/16000)+.07*sin(2*PI*913*i/16000)).toFloat() }
        val actual=TalkNetMfcc.compute(audio)
        val expected=javaClass.getResourceAsStream("/talknet-mfcc-reference.csv")!!.bufferedReader().use {
            it.readLines().flatMap { row->row.split(',').map(String::toFloat) }
        }
        assertEquals(expected.size,actual.size)
        actual.indices.forEach { assertEquals("MFCC coefficient $it",expected[it],actual[it],.002f) }
        assertTrue(TalkNetMfcc.compute(FloatArray(16000)).all { it.isFinite() })
    }
}
