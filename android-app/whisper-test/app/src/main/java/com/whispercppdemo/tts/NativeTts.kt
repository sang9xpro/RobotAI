package com.whispercppdemo.tts

internal object NativeTts {
    init { System.loadLibrary("robotai_tts") }
    external fun open(path: String): Long
    external fun phonemize(handle: Long, text: ByteArray): ByteArray
    external fun normalize(handle: Long, text: ByteArray): ByteArray
    external fun close(handle: Long)
    external fun logits(matrix: FloatArray, offset: Int, rows: Int, vector: FloatArray): FloatArray
}
