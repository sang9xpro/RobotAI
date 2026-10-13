package com.robotai.domain

import java.text.Normalizer
import java.util.Locale

object DirectRobotCall {
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim().replace(Regex("\\s+"), " ")
    fun matches(name: String, text: String): Boolean {
        val robot = normalize(name)
        if (robot.isEmpty()) return false
        val clean = normalize(text)
        return clean == robot || clean.matches(Regex("(?:chào |xin chào |này )?${Regex.escape(robot)} ơi(?: .*)?")) ||
            clean.matches(Regex(".+ ${Regex.escape(robot)} ơi"))
    }
    fun onlyCall(name: String, text: String): Boolean = normalize(text).matches(
        Regex("(?:chào |xin chào |này )?${Regex.escape(normalize(name))}(?: ơi)?"))
}
