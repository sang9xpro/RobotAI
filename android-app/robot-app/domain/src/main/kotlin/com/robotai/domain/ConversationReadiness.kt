package com.robotai.domain

data class ConversationBenchmarks(val utterances: Int, val interruptions: Int, val runMinutes: Int,
    val precision: Double, val recall: Double, val firstAudioP95Ms: Long, val interruptP95Ms: Long) {
    val passed get() = utterances >= 100 && interruptions >= 50 && runMinutes >= 60 &&
        precision in .98..1.0 && recall in .90..1.0 && firstAudioP95Ms in 1..5000 && interruptP95Ms in 1..1000
}
