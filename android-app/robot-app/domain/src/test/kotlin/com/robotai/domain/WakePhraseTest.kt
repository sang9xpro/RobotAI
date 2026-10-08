package com.robotai.domain

import org.junit.Assert.*
import org.junit.Test

class WakePhraseTest {
    @Test fun kenAndKenOiWakeButIncidentalMentionsDoNot() {
        assertTrue(WakePhrase.matches("Ken", listOf("KEN!")))
        assertTrue(WakePhrase.matches("Ken", listOf("Ken ơi")))
        assertFalse(WakePhrase.matches("Ken", listOf("Tôi đang nói chuyện với Ken")))
        assertFalse(WakePhrase.matches("Ken", listOf("Kent")))
        assertFalse(WakePhrase.matches("Ken", listOf("Khen")))
        assertFalse(WakePhrase.matches("", listOf("")))
    }
    @Test fun handlesSpacingAndUnicode() {
        assertTrue(WakePhrase.matches("Ken", listOf("  Ken,   ơi! ")))
        assertTrue(WakePhrase.matches("Bông", listOf("Bo\u0302ng")))
    }
}
