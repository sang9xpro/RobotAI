package com.whispercppdemo.recorder

import kotlin.math.sqrt

/** Experimental energy endpointing at 16 kHz. Every emitted speech sample is retained once. */
class VoiceSegmenter(private val emit: (FloatArray) -> Unit) {
    private val pending = ArrayList<Float>()
    private val preroll = ArrayDeque<Float>()
    private val frame = ArrayList<Float>(320)
    private var active = false
    private var silent = 0
    fun accept(samples: FloatArray) {
        samples.forEach { x ->
            frame += x
            if (frame.size == 320) { process(frame.toFloatArray()); frame.clear() }
        }
    }
    private fun process(data: FloatArray) {
        val speech = sqrt(data.sumOf { (it * it).toDouble() } / data.size) >= 0.008
        if (!active) {
            if (!speech) {
                data.forEach { preroll.addLast(it) }
                while (preroll.size > 3200) preroll.removeFirst()
                return
            }
            pending.addAll(preroll); preroll.clear(); active = true
        }
        pending.addAll(data.toList())
        silent = if (speech) 0 else silent + data.size
        if ((silent >= 9600 && pending.size >= 48000) || pending.size >= 192000) flush()
    }
    fun finish() { if (frame.isNotEmpty()) { process(frame.toFloatArray()); frame.clear() }; flush() }
    private fun flush() {
        if (active && pending.size >= 1600) emit(pending.toFloatArray())
        pending.clear(); active = false; silent = 0
    }
}
