package com.warden.app.feature.websites.domain

import com.warden.app.data.models.WebsiteBlockerConfig
import java.util.regex.Pattern

/**
 * Result of matching a blocked website domain inside visible UI text or a URL bar.
 *
 * @property matchedDomain The normalized rule domain that triggered the block (e.g. `example.com`).
 * @property rawToken The URL or host token extracted from the text.
 */
data class DomainMatch(
    val matchedDomain: String,
    val rawToken: String,
)

/**
 * Pure Kotlin website domain matcher kept separate from the word/phrase matcher.
 *
 * Supports O(labels) suffix lookup over 46,000+ domains so `example.com` blocks `example.com`
 * and `cdn.example.com` while allowing `notexample.com` and `example.com.other.org`.
 * Whitelisted domains always override blocked domains.
 */
class WebsiteDomainMatcher(
    private val isEnabled: Boolean,
    blockedDomains: Set<String>,
    whitelistedDomains: Set<String>,
) {
    private val normalizedBlocked: Set<String> =
        blockedDomains.mapNotNull(DomainNormalizer::normalizeHostOrNull).toHashSet()
    private val normalizedWhitelisted: Set<String> =
        whitelistedDomains.mapNotNull(DomainNormalizer::normalizeHostOrNull).toHashSet()

    /** Returns `true` when website blocking is enabled and at least one domain is active. */
    val hasActiveRules: Boolean get() = isEnabled && normalizedBlocked.isNotEmpty()

    /**
     * Checks whether a single URL or hostname [rawHostOrUrl] is blocked.
     *
     * @return The matched blocked domain rule, or `null` if allowed or whitelisted.
     */
    fun matchHost(rawHostOrUrl: String): String? {
        if (!hasActiveRules) return null
        val host = DomainNormalizer.normalizeHostOrNull(rawHostOrUrl) ?: return null
        if (findMatchingSuffix(host, normalizedWhitelisted) != null) return null
        return findMatchingSuffix(host, normalizedBlocked)
    }

    /**
     * Scans visible [text] (such as a browser address bar or link) for a blocked domain.
     *
     * Uses an O(1) dot check fast-path so ordinary prose without dots incurs zero regex overhead.
     */
    fun findMatchInText(text: String): DomainMatch? {
        if (!hasActiveRules || !text.contains('.')) return null
        val matcher = URL_TOKEN_PATTERN.matcher(text)
        while (matcher.find()) {
            val token = matcher.group().trimEnd('.', ',', ';', ')', ']', '>', '"', '\'')
            val blockedRule = matchHost(token)
            if (blockedRule != null) {
                return DomainMatch(matchedDomain = blockedRule, rawToken = token)
            }
        }
        return null
    }

    private fun findMatchingSuffix(
        host: String,
        domainSet: Set<String>,
    ): String? {
        if (domainSet.isEmpty()) return null
        var candidate = host
        while (true) {
            if (candidate in domainSet) return candidate
            val dotIdx = candidate.indexOf('.')
            if (dotIdx < 0 || dotIdx == candidate.lastIndex) return null
            candidate = candidate.substring(dotIdx + 1)
            if (!candidate.contains('.')) return null
        }
    }

    companion object {
        private val URL_TOKEN_PATTERN: Pattern =
            Pattern.compile(
                """(?:[a-zA-Z][a-zA-Z0-9+.-]*://)?(?:[\p{L}\p{N}-]+\.)+[\p{L}]{2,63}\.?(?::\d{1,5})?(?:[/?#][^\s]*)?""",
                Pattern.UNICODE_CHARACTER_CLASS,
            )

        /** Builds a [WebsiteDomainMatcher] by combining [builtinDomains] with user [config]. */
        fun fromConfig(
            config: WebsiteBlockerConfig,
            builtinDomains: Collection<String>,
        ): WebsiteDomainMatcher {
            if (!config.isEnabled) {
                return WebsiteDomainMatcher(isEnabled = false, blockedDomains = emptySet(), whitelistedDomains = emptySet())
            }
            val disabled = config.disabledBuiltinDomains.mapNotNull(DomainNormalizer::normalizeHostOrNull).toHashSet()
            val combined = HashSet<String>(builtinDomains.size + config.userBlockedDomains.size)
            builtinDomains.forEach { domain ->
                if (domain !in disabled) combined.add(domain)
            }
            config.userBlockedDomains.mapNotNullTo(combined, DomainNormalizer::normalizeHostOrNull)
            return WebsiteDomainMatcher(
                isEnabled = true,
                blockedDomains = combined,
                whitelistedDomains = config.whitelistedDomains.toSet(),
            )
        }
    }
}
