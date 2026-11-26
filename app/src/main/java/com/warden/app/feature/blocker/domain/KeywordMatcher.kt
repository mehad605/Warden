package com.warden.app.feature.blocker.domain

import java.util.Locale

/**
 * Result of matching a blocked keyword inside visible UI text.
 *
 * @property keyword The matched lowercased keyword substring.
 * @property context Up to 7 words before and after the match from the original text.
 */
data class KeywordMatch(
    val keyword: String,
    val context: String,
)

/**
 * Immutable, precompiled keyword matcher that masks whitelisted phrases before matching
 * whole-word blocked keywords.
 *
 * Compiling the regex and pre-lowercasing whitelist terms once per settings update avoids
 * repeated regex compilation and allocation on every accessibility event.
 */
class KeywordMatcher(
    blockedKeywords: Collection<String>,
    whitelistedKeywords: Collection<String>,
) {
    private val lowerWhitelist: List<String> =
        whitelistedKeywords
            .map { it.lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }

    private val blockedPattern: Regex? = compilePattern(blockedKeywords)

    /** Returns `true` when at least one non-blank blocked keyword is configured. */
    val hasBlockedKeywords: Boolean get() = blockedPattern != null

    /**
     * Searches [text] for a blocked keyword after masking any whitelisted phrases with spaces
     * of identical length so indices into the original [text] remain exact.
     */
    fun findMatch(text: String): KeywordMatch? {
        val pattern = blockedPattern ?: return null
        if (text.isBlank()) return null

        val masked = maskWhitelisted(text.lowercase(Locale.ROOT))
        val match = pattern.find(masked) ?: return null
        val context = extractContext(text, match.range.first, match.value.length)
        return KeywordMatch(keyword = match.value, context = context)
    }

    private fun maskWhitelisted(lowerText: String): String {
        var result = lowerText
        for (white in lowerWhitelist) {
            if (result.contains(white)) {
                result = result.replace(white, " ".repeat(white.length))
            }
        }
        return result
    }

    private fun extractContext(
        text: String,
        startIndex: Int,
        length: Int,
    ): String {
        val beforeText = text.substring(0, startIndex)
        val afterText = text.substring(startIndex + length)

        val wordsBefore =
            beforeText.split(WHITESPACE_REGEX)
                .filter { it.isNotBlank() }
                .takeLast(CONTEXT_WORD_WINDOW)
                .joinToString(" ")
        val wordsAfter =
            afterText.split(WHITESPACE_REGEX)
                .filter { it.isNotBlank() }
                .take(CONTEXT_WORD_WINDOW)
                .joinToString(" ")
        val actualKeyword = text.substring(startIndex, startIndex + length)

        return listOf(wordsBefore, actualKeyword, wordsAfter)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun compilePattern(keywords: Collection<String>): Regex? {
        val escaped =
            keywords
                .asSequence()
                .filter { it.isNotBlank() }
                .map { Regex.escape(it.lowercase(Locale.ROOT)) }
                .distinct()
                .sortedByDescending { it.length }
                .toList()
        if (escaped.isEmpty()) return null
        val alternation = escaped.joinToString("|")
        return Regex("""(?<!\w)(?:$alternation)+(?!\w)""")
    }

    companion object {
        private const val CONTEXT_WORD_WINDOW = 7
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
