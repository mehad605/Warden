package com.warden.app.feature.blocker.domain

import com.warden.app.data.models.BlockedEntry
import com.warden.app.data.models.MatchMode
import java.util.regex.Pattern

/**
 * Result of matching a blocked keyword, phrase, or domain inside visible UI text.
 *
 * @property keyword The matched normalized keyword, phrase, or domain.
 * @property context Up to 7 words before and after the match from the original text.
 */
data class KeywordMatch(
    val keyword: String,
    val context: String,
)

/**
 * Immutable, precompiled word and phrase matcher supporting per-entry `exact` and `contains`
 * modes, Unicode NFKC / diacritic / zero-width normalization, multi-word phrase separators
 * (`[\s\-_.]+`), script-agnostic word boundaries, and span-expanding whitelist semantics.
 */
class KeywordMatcher(
    blockedEntries: List<BlockedEntry>,
    whitelistedKeywords: Collection<String>,
) {
    /** Backward-compatible constructor treating every term in [blockedKeywords] as [MatchMode.EXACT]. */
    constructor(
        blockedKeywords: Collection<String>,
        whitelistedKeywords: Collection<String>,
    ) : this(
        blockedEntries = blockedKeywords.map { BlockedEntry(term = it, mode = MatchMode.EXACT) },
        whitelistedKeywords = whitelistedKeywords,
    )

    private val whitelistedWords: Set<String>
    private val whitelistedPhrasePattern: Pattern?
    private val exactPattern: Pattern?
    private val containsPattern: Pattern?

    init {
        val normalizedWhitelist =
            whitelistedKeywords
                .asSequence()
                .map(TextNormalizer::normalizeTerm)
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()
        whitelistedWords = normalizedWhitelist.filter { !it.contains(' ') }.toHashSet()
        whitelistedPhrasePattern = compileExactPattern(normalizedWhitelist.filter { it.contains(' ') })

        val dedupedByTerm = LinkedHashMap<String, MatchMode>()
        blockedEntries.forEach { entry ->
            val norm = TextNormalizer.normalizeTerm(entry.term)
            if (norm.isNotEmpty()) {
                dedupedByTerm[norm] = entry.mode
            }
        }
        val exactTerms = dedupedByTerm.filterValues { it == MatchMode.EXACT }.keys
        val containsTerms = dedupedByTerm.filterValues { it == MatchMode.CONTAINS }.keys
        exactPattern = compileExactPattern(exactTerms)
        containsPattern = compileContainsPattern(containsTerms)
    }

    /** Returns `true` when at least one non-blank blocked term is configured. */
    val hasBlockedKeywords: Boolean get() = exactPattern != null || containsPattern != null

    /**
     * Searches [text] for the first non-whitelisted blocked word or phrase match.
     *
     * Every candidate blocked span is expanded to its surrounding full word; if that full word
     * or any exact whitelisted phrase covering the span is whitelisted, the match is discarded.
     */
    fun findMatch(text: String): KeywordMatch? {
        if (!hasBlockedKeywords || text.isBlank()) return null

        val indexed = TextNormalizer.normalizeWithIndexMap(text)
        val norm = indexed.text
        if (norm.isBlank()) return null

        var whitelistPhraseSpans: List<IntRange>? = null
        val resolvePhraseSpans: () -> List<IntRange> = {
            whitelistPhraseSpans ?: collectWhitelistPhraseSpans(norm).also { whitelistPhraseSpans = it }
        }

        val hit =
            findFirstAllowedHit(norm, exactPattern, resolvePhraseSpans)
                ?: findFirstAllowedHit(norm, containsPattern, resolvePhraseSpans)
                ?: return null

        val origStart = indexed.originalIndex[hit.first]
        val origEndExclusive = (indexed.originalIndex[hit.last] + 1).coerceAtMost(text.length)
        val context = extractContext(text, origStart, origEndExclusive - origStart)
        return KeywordMatch(keyword = norm.substring(hit.first, hit.last + 1), context = context)
    }

    private fun findFirstAllowedHit(
        normText: String,
        pattern: Pattern?,
        phraseSpansProvider: () -> List<IntRange>,
    ): IntRange? {
        pattern ?: return null
        val matcher = pattern.matcher(normText)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (!isSpanWhitelisted(normText, start, end, phraseSpansProvider)) {
                return start until end
            }
        }
        return null
    }

    private fun isSpanWhitelisted(
        normText: String,
        start: Int,
        end: Int,
        phraseSpansProvider: () -> List<IntRange>,
    ): Boolean {
        val wordRange = expandToSurroundingWord(normText, start, end)
        if (whitelistedWords.isNotEmpty() && normText.substring(wordRange.first, wordRange.last + 1) in whitelistedWords) {
            return true
        }
        if (whitelistedPhrasePattern != null) {
            val spans = phraseSpansProvider()
            if (spans.any { it.first <= start && it.last >= end - 1 }) {
                return true
            }
        }
        return false
    }

    private fun expandToSurroundingWord(
        text: String,
        start: Int,
        end: Int,
    ): IntRange {
        var left = start
        while (left > 0) {
            val prevCp = Character.codePointBefore(text, left)
            if (!Character.isLetterOrDigit(prevCp)) break
            left -= Character.charCount(prevCp)
        }
        var right = end
        while (right < text.length) {
            val nextCp = Character.codePointAt(text, right)
            if (!Character.isLetterOrDigit(nextCp)) break
            right += Character.charCount(nextCp)
        }
        return left until right.coerceAtLeast(left + 1)
    }

    private fun collectWhitelistPhraseSpans(normText: String): List<IntRange> {
        val pattern = whitelistedPhrasePattern ?: return emptyList()
        val matcher = pattern.matcher(normText)
        val spans = mutableListOf<IntRange>()
        while (matcher.find()) {
            spans.add(matcher.start() until matcher.end())
        }
        return spans
    }

    private fun compileExactPattern(terms: Collection<String>): Pattern? {
        val alternation = buildAlternation(terms) ?: return null
        // Android ART ICU enables Unicode character classes by default and throws
        // IllegalArgumentException("Unsupported flags: 256") for Pattern.UNICODE_CHARACTER_CLASS.
        return Pattern.compile("""(?<![\p{L}\p{N}])(?:$alternation)(?![\p{L}\p{N}])""")
    }

    private fun compileContainsPattern(terms: Collection<String>): Pattern? {
        val alternation = buildAlternation(terms) ?: return null
        return Pattern.compile("(?:$alternation)")
    }

    private fun buildAlternation(terms: Collection<String>): String? {
        val parts =
            terms
                .asSequence()
                .filter { it.isNotBlank() }
                .distinct()
                .sortedByDescending { it.length }
                .map(::termToRegex)
                .toList()
        return if (parts.isEmpty()) null else parts.joinToString("|")
    }

    private fun termToRegex(normalizedTerm: String): String {
        val tokens = normalizedTerm.split(' ').filter { it.isNotEmpty() }
        return tokens.joinToString(PHRASE_SEPARATOR_REGEX) { Pattern.quote(it) }
    }

    private fun extractContext(
        text: String,
        startIndex: Int,
        length: Int,
    ): String {
        val safeStart = startIndex.coerceIn(0, text.length)
        val safeEnd = (safeStart + length).coerceIn(safeStart, text.length)
        val beforeText = text.substring(0, safeStart)
        val afterText = text.substring(safeEnd)

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
        val actualKeyword = text.substring(safeStart, safeEnd)

        return listOf(wordsBefore, actualKeyword, wordsAfter)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    companion object {
        private const val CONTEXT_WORD_WINDOW = 7
        private const val PHRASE_SEPARATOR_REGEX = """[\s\-_.]+"""
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
