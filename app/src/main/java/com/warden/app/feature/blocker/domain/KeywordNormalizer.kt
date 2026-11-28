package com.warden.app.feature.blocker.domain

/**
 * Pure domain result of normalizing a user-entered keyword or whitelist entry.
 *
 * @property normalized Unicode-normalized, trimmed, collapsed-whitespace term.
 * @property wasUrl True if input looked like a web URL and prefixes were stripped.
 * @property isRisky True if the normalized keyword matches the risky-word warning list (`"sex"`).
 */
data class NormalizedKeyword(
    val normalized: String,
    val wasUrl: Boolean,
    val isRisky: Boolean,
)

/** Validation result when adding a blocked word/phrase or safe phrase. */
sealed class TermValidationResult {
    /** Input was empty or whitespace-only after normalization. */
    data object Empty : TermValidationResult()

    /** Input exceeded [maxLength] characters after trimming/collapsing whitespace. */
    data class TooLong(val maxLength: Int) : TermValidationResult()

    /** Input passed validation and produced [value]. */
    data class Valid(val value: NormalizedKeyword) : TermValidationResult()
}

/**
 * Pure Kotlin keyword/phrase normalization, length validation, and risky-word checks.
 */
object KeywordNormalizer {
    private val URL_REGEX =
        Regex(
            pattern = "^(?:https?://|www\\.)[^\\s]+$|^[a-z0-9-]+\\.[a-z]{2,}(?:/[^\\s]*)?$",
            option = RegexOption.IGNORE_CASE,
        )
    private const val RISKY_WORD_SEX = "sex"

    /** Validates and normalizes [rawInput], rejecting blank terms and terms over 100 chars. */
    fun validateAndNormalize(rawInput: String): TermValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return TermValidationResult.Empty

        val wasUrl = URL_REGEX.matches(trimmed)
        val stripped =
            if (wasUrl) {
                stripUrlPrefix(trimmed)
            } else {
                trimmed
            }
        val normalized = TextNormalizer.normalizeTerm(stripped)
        if (normalized.isEmpty()) return TermValidationResult.Empty
        if (normalized.length > TextNormalizer.MAX_TERM_LENGTH) {
            return TermValidationResult.TooLong(TextNormalizer.MAX_TERM_LENGTH)
        }
        return TermValidationResult.Valid(
            NormalizedKeyword(
                normalized = normalized,
                wasUrl = wasUrl,
                isRisky = normalized == RISKY_WORD_SEX,
            ),
        )
    }

    /** Normalizes [rawInput], returning `null` if blank or longer than 100 characters. */
    fun normalize(rawInput: String): NormalizedKeyword? =
        (validateAndNormalize(rawInput) as? TermValidationResult.Valid)?.value

    private fun stripUrlPrefix(value: String): String {
        var result = value
        val lower = result.lowercase()
        when {
            lower.startsWith("https://") -> result = result.substring("https://".length)
            lower.startsWith("http://") -> result = result.substring("http://".length)
        }
        if (result.lowercase().startsWith("www.")) {
            result = result.substring("www.".length)
        }
        return result
    }
}
