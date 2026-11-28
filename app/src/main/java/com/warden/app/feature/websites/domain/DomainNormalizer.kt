package com.warden.app.feature.websites.domain

import java.net.IDN
import java.text.Normalizer
import java.util.Locale

/** Validation outcome for a user-supplied URL or domain string. */
sealed class DomainValidationResult {
    /** Input parsed into a clean normalized [domain]. */
    data class Valid(val domain: String) : DomainValidationResult()

    /** Input was rejected with a user-readable [reason]. */
    data class Invalid(val reason: String) : DomainValidationResult()
}

/**
 * Pure Kotlin URL/domain host extractor and normalizer.
 *
 * Strips scheme, userinfo, `www.`, port, path, query, fragment, and trailing dots; converts
 * punycode labels (`xn--...`) to Unicode; applies Unicode NFKC and lowercasing; and validates
 * host syntax cleanly without Android framework dependencies.
 */
object DomainNormalizer {
    private val SCHEME_REGEX = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://""")
    private val VALID_LABEL_REGEX = Regex("""^[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,61}[\p{L}\p{N}])?$""")
    private val VALID_TLD_REGEX = Regex("""^[\p{L}]{2,63}$""")

    /** Validates [rawInput] and extracts a normalized host or returns a clear error reason. */
    fun validateUserInput(rawInput: String): DomainValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return DomainValidationResult.Invalid("Enter a domain or URL.")
        if (trimmed.any { it.isWhitespace() }) {
            return DomainValidationResult.Invalid("Domains cannot contain spaces.")
        }
        val normalized = normalizeHostOrNull(trimmed)
        return if (normalized != null) {
            DomainValidationResult.Valid(normalized)
        } else {
            DomainValidationResult.Invalid("Enter a valid domain like example.com.")
        }
    }

    /**
     * Normalizes a raw URL or bare host into a canonical lowercase Unicode domain, or returns
     * `null` if [rawInput] is not a valid domain.
     */
    fun normalizeHostOrNull(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null

        val withoutScheme = SCHEME_REGEX.replaceFirst(trimmed, "")
        val authority =
            withoutScheme
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('@')
        val hostPart = stripPort(authority).trimEnd('.')
        if (hostPart.isEmpty() || !hostPart.contains('.')) return null

        val unicodeHost = decodePunycodeSafe(hostPart) ?: return null
        val nfkcLower = Normalizer.normalize(unicodeHost, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val withoutWww = stripLeadingWww(nfkcLower)
        return if (isValidHost(withoutWww)) withoutWww else null
    }

    private fun stripPort(authority: String): String {
        val colonIdx = authority.lastIndexOf(':')
        if (colonIdx <= 0) return authority
        val maybePort = authority.substring(colonIdx + 1)
        return if (maybePort.isNotEmpty() && maybePort.all { it.isDigit() }) {
            authority.substring(0, colonIdx)
        } else {
            authority
        }
    }

    private fun decodePunycodeSafe(host: String): String? =
        try {
            IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun stripLeadingWww(host: String): String {
        var current = host
        while (current.startsWith("www.") && current.count { it == '.' } >= 2) {
            current = current.removePrefix("www.")
        }
        return current
    }

    private fun isValidHost(host: String): Boolean {
        if (host.length > MAX_HOST_LENGTH || host.contains("..")) return false
        if (host.startsWith('.') || host.endsWith('.')) return false
        val labels = host.split('.')
        if (labels.size < 2 || !VALID_TLD_REGEX.matches(labels.last())) return false
        return labels.all { VALID_LABEL_REGEX.matches(it) }
    }

    private const val MAX_HOST_LENGTH = 253
}
