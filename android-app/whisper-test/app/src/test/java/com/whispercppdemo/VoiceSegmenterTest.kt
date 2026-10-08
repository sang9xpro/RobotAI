package com.whispercppdemo

import com.whispercppdemo.recorder.VoiceSegmenter
import org.junit.Assert.*
import org.junit.Test

class VoiceSegmenterTest {
    @Test fun silenceDoesNotCreateWork() {
        val chunks = mutableListOf<FloatArray>(); val s = VoiceSegmenter { chunks += it }
        s.accept(FloatArray(16000 * 4)); s.finish(); assertTrue(chunks.isEmpty())
    }
    @Test fun pausesEmitBeforeRecordingEnds() {
        val chunks = mutableListOf<FloatArray>(); val s = VoiceSegmenter { chunks += it }
        s.accept(FloatArray(48000) { 0.1f }); s.accept(FloatArray(9600))
        assertEquals(1, chunks.size); assertEquals(57600, chunks[0].size)
        s.accept(FloatArray(16000) { 0.2f }); s.finish()
        assertEquals(2, chunks.size); assertEquals(16000, chunks[1].size)
    }
    @Test fun longSpeechIsNotDroppedOrDuplicated() {
        val chunks = mutableListOf<FloatArray>(); val s = VoiceSegmenter { chunks += it }
        val original = FloatArray(400001) { 0.1f + (it % 100) * 0.00001f }
        for (part in original.asList().chunked(997)) s.accept(part.toFloatArray())
        s.finish()
        assertEquals(3, chunks.size)
        assertArrayEquals(original, chunks.flatMap { it.toList() }.toFloatArray(), 0f)
    }
}
