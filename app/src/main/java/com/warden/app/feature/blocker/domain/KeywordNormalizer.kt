package com.warden.app.feature.blocker.domain

import java.util.Locale

/**
 * Pure domain result of normalizing a user-entered keyword or whitelist entry.
 *
 * @property normalized Lowercased, trimmed keyword with URL prefixes stripped when applicable.
 * @property wasUrl True if input looked like a web URL and prefixes were stripped.
 * @property isRisky True if the normalized keyword matches the risky-word warning list (`"sex"`).
 */
data class NormalizedKeyword(
    val normalized: String,
    val wasUrl: Boolean,
    val isRisky: Boolean
)

/**
 * Pure Kotlin keyword normalization and risky-word checks (no Android framework dependency).
 */
object KeywordNormalizer {

    private val URL_REGEX = Regex(
        pattern = "^(?:https?://|www\\.)[^\\s]+$|^[a-z0-9-]+\\.[a-z]{2,}(?:/[^\\s]*)?$",
        option = RegexOption.IGNORE_CASE
    )
    private const val RISKY_WORD_SEX = "sex"

    /** Normalizes [rawInput], returning `null` if [rawInput] is blank. */
    fun normalize(rawInput: String): NormalizedKeyword? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val wasUrl = URL_REGEX.matches(trimmed)
        val stripped = if (wasUrl) {
            trimmed
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
        } else {
            trimmed
        }
        val lower = stripped.trim().lowercase(Locale.ROOT)
        if (lower.isEmpty()) return null
        return NormalizedKeyword(
            normalized = lower,
            wasUrl = wasUrl,
            isRisky = lower == RISKY_WORD_SEX
        )
    }
}
