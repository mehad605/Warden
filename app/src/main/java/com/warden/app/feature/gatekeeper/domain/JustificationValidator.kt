package com.warden.app.feature.gatekeeper.domain

import java.util.Locale

/**
 * Enforces local justification quality rules before making a network call to the AI Gatekeeper.
 *
 * Running validation on-device prevents trivial bypass attempts, saves API quota, and gives
 * immediate feedback to the user.
 */
object JustificationValidator {

    private const val MIN_SENTENCES = 7
    private const val MIN_WORDS_PER_SENTENCE = 5

    private val SENTENCE_SPLIT_REGEX = Regex("[.!?]+")
    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Validates [text] against the gatekeeper justification rules.
     *
     * @param text User-entered justification.
     * @param isRemoval When `true`, validation is bypassed because re-enabling blocking is encouraged.
     * @return An error message if invalid, or `null` when valid.
     */
    fun validate(text: String, isRemoval: Boolean = false): String? {
        if (isRemoval) return null

        val sentences = text.split(SENTENCE_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sentences.size < MIN_SENTENCES) {
            return "Your reasoning must contain at least $MIN_SENTENCES sentences (currently: ${sentences.size})."
        }

        sentences.forEachIndexed { index, sentence ->
            val error = validateSentence(sentence, index + 1)
            if (error != null) return error
        }
        return null
    }

    private fun validateSentence(sentence: String, number: Int): String? {
        val words = sentence.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (words.size < MIN_WORDS_PER_SENTENCE) {
            return "Sentence $number is too short (\"$sentence\"). Each sentence must contain at least $MIN_WORDS_PER_SENTENCE words."
        }
        val distinctWords = words.map { it.lowercase(Locale.ROOT) }.distinct()
        if (distinctWords.size <= words.size / 2) {
            return "Sentence $number is repetitive or list-like. Please write a natural explanation."
        }
        return null
    }
}
