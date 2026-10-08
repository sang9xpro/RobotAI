package com.whispercppdemo.tts

import org.json.JSONObject
import java.text.Normalizer

/** Byte-level BPE using the model's exact vocabulary and merge ranks. */
internal class PhoneTokenizer(json: String) {
    private val model = JSONObject(json).getJSONObject("model")
    private val vocab = model.getJSONObject("vocab")
    private val ranks = mutableMapOf<Pair<String, String>, Int>()
    private val bytes = Array(256) { "" }
    private val pieces = Regex("(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+")
    init {
        val visible = ((33..126) + (161..172) + (174..255)).toSet()
        var extra = 0
        for (b in 0..255) bytes[b] = (if (b in visible) b else 256 + extra++).toChar().toString()
        val merges = model.getJSONArray("merges")
        for (i in 0 until merges.length()) {
            val pair = merges.getJSONArray(i)
            ranks[pair.getString(0) to pair.getString(1)] = i
        }
    }
    fun encode(phones: String): IntArray {
        val output = mutableListOf<Int>()
        for (piece in pieces.findAll(Normalizer.normalize(phones, Normalizer.Form.NFC))) {
            val symbols = piece.value.toByteArray(Charsets.UTF_8).map { bytes[it.toInt() and 255] }.toMutableList()
            while (symbols.size > 1) {
                var best = Int.MAX_VALUE; var at = -1
                for (i in 0 until symbols.lastIndex) {
                    val rank = ranks[symbols[i] to symbols[i + 1]] ?: Int.MAX_VALUE
                    if (rank < best) { best = rank; at = i }
                }
                if (at < 0) break
                val left = symbols[at]; val right = symbols[at + 1]
                var i = 0
                while (i < symbols.lastIndex) {
                    if (symbols[i] == left && symbols[i + 1] == right) {
                        symbols[i] += symbols[i + 1]; symbols.removeAt(i + 1)
                    }
                    i++
                }
            }
            output += symbols.map { require(vocab.has(it)) { "Phoneme token không được hỗ trợ" }; vocab.getInt(it) }
        }
        return output.toIntArray()
    }
}
