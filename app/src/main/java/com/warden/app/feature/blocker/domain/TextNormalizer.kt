package com.warden.app.feature.blocker.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Normalized text alongside an index map back to character positions in the original string.
 *
 * @property text NFKC-normalized, diacritic-stripped, zero-width-free, lowercased text.
 * @property originalIndex For each index `i` in [text], the corresponding char offset in the source.
 */
data class IndexedNormalizedText(
    val text: String,
    val originalIndex: IntArray,
)

/**
 * Pure Kotlin Unicode text normalizer shared by term storage, blocklist matching, and whitelist matching.
 *
 * Applies NFKC compatibility normalization, strips zero-width characters, removes combining
 * diacritical marks (`\p{Mn}`), and lowercases using [Locale.ROOT]. Uses an ASCII fast path so
 * 10k-character screen scans stay well under 2 ms.
 */
object TextNormalizer {
    /** Maximum allowed character length for a single normalized user term. */
    const val MAX_TERM_LENGTH: Int = 100

    private val WHITESPACE_RUN_REGEX = Regex("""\s+""")

    /** Normalizes [raw] for storage as a word or phrase (trim + collapse internal whitespace). */
    fun normalizeTerm(raw: String): String = normalize(raw).trim().replace(WHITESPACE_RUN_REGEX, " ")

    /** Normalizes [input] (NFKC, zero-width stripped, diacritics stripped, lowercased). */
    fun normalize(input: String): String = normalizeWithIndexMap(input).text

    /**
     * Normalizes [input] while preserving a char-offset mapping back to [input] so context
     * snippets can be sliced directly from the original user-visible text.
     */
    fun normalizeWithIndexMap(input: String): IndexedNormalizedText {
        val len = input.length
        if (len == 0) return IndexedNormalizedText("", IntArray(0))

        val sb = StringBuilder(len)
        var map = IntArray(len)
        var outLen = 0
        var i = 0

        while (i < len) {
            val ch = input[i]
            if (ch.code < ASCII_LIMIT) {
                val lower = if (ch in 'A'..'Z') (ch.code + ASCII_CASE_DELTA).toChar() else ch
                if (outLen >= map.size) map = map.copyOf(map.size * 2)
                sb.append(lower)
                map[outLen++] = i
                i++
                continue
            }

            val cp = Character.codePointAt(input, i)
            val charCount = Character.charCount(cp)
            if (!isZeroWidth(cp)) {
                val folded = foldNonAsciiCodePoint(cp)
                for (k in folded.indices) {
                    if (outLen >= map.size) map = map.copyOf(map.size * 2)
                    sb.append(folded[k])
                    map[outLen++] = i
                }
            }
            i += charCount
        }
        return IndexedNormalizedText(sb.toString(), map.copyOf(outLen))
    }

    private fun foldNonAsciiCodePoint(cp: Int): String {
        val raw = String(Character.toChars(cp))
        val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        val nfd = Normalizer.normalize(nfkc, Normalizer.Form.NFD)
        val out = StringBuilder(nfd.length)
        var idx = 0
        while (idx < nfd.length) {
            val subCp = Character.codePointAt(nfd, idx)
            val step = Character.charCount(subCp)
            if (!isZeroWidth(subCp) && Character.getType(subCp) != Character.NON_SPACING_MARK.toInt()) {
                out.appendCodePoint(subCp)
            }
            idx += step
        }
        return out.toString().lowercase(Locale.ROOT)
    }

    private fun isZeroWidth(cp: Int): Boolean =
        cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0x2060 || cp == 0xFEFF || cp == 0x00AD

    private const val ASCII_LIMIT = 128
    private const val ASCII_CASE_DELTA = 32
}
