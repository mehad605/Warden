package com.warden.app

import com.warden.app.utils.GeminiManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests locking in C12 (AI Gatekeeper local justification validation).
 */
class GeminiGatekeeperCharacterizationTest {

    private fun validJustification(): String = listOf(
        "I need to access my university portal to submit the final compiler assignment.",
        "The deadline closes in twenty minutes and my laptop battery just died completely.",
        "My professor requires a PDF upload directly through the mobile browser interface.",
        "I have already prepared the finished report document inside my downloads folder.",
        "Without submitting this file now I will lose twenty percent of my grade.",
        "I promise to close the browser tab immediately after receiving the confirmation receipt.",
        "Please grant a brief temporary exemption so I can finish this urgent academic task."
    ).joinToString(" ")

    @Test
    fun c12_removalRequestBypassesValidation() {
        assertNull(GeminiManager.validateUserText("remove", isRemoval = true))
        assertNull(GeminiManager.validateUserText("", isRemoval = true))
    }

    @Test
    fun c12_rejectsFewerThanSevenSentences() {
        val text = "This is one complete valid sentence. Here is a second complete valid sentence."
        val error = GeminiManager.validateUserText(text, isRemoval = false)
        assertNotNull(error)
        assertTrue(error!!.contains("at least 7 sentences"))
    }

    @Test
    fun c12_rejectsSentenceWithFewerThanFiveWords() {
        val text = listOf(
            "I need this app for urgent school work.",
            "Too short here.",
            "My professor requires a PDF upload right away.",
            "I prepared the finished report document already now.",
            "Without submitting this file I lose grade points.",
            "I will close the browser right after upload.",
            "Please grant a short exemption for this task."
        ).joinToString(" ")
        val error = GeminiManager.validateUserText(text, isRemoval = false)
        assertNotNull(error)
        assertTrue(error!!.contains("at least 5 words"))
    }

    @Test
    fun c12_rejectsRepetitiveSentence() {
        val text = listOf(
            "I need this app for urgent school work.",
            "please please please please please please.",
            "My professor requires a PDF upload right away.",
            "I prepared the finished report document already now.",
            "Without submitting this file I lose grade points.",
            "I will close the browser right after upload.",
            "Please grant a short exemption for this task."
        ).joinToString(" ")
        val error = GeminiManager.validateUserText(text, isRemoval = false)
        assertNotNull(error)
        assertTrue(error!!.contains("repetitive"))
    }

    @Test
    fun c12_acceptsValidSevenSentenceJustification() {
        assertNull(GeminiManager.validateUserText(validJustification(), isRemoval = false))
    }
}
