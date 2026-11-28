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
 * Performs zero-copy O(labels) suffix lookup against a shared pre-normalized built-in set
 * (avoiding re-normalizing or duplicating 46,000+ strings on every settings update) plus
 * user-added custom domains across all enabled [com.warden.app.data.models.WebsiteBlockProfile]s.
 */
class WebsiteDomainMatcher private constructor(
    private val isEnabled: Boolean,
    private val builtinEnabled: Boolean,
    private val builtinDomains: Set<String>,
    private val disabledBuiltins: Set<String>,
    private val customBlockedDomains: Set<String>,
    private val whitelistedDomains: Set<String>,
) {
    /** Convenience constructor for unit tests and direct custom-domain evaluation. */
    constructor(
        isEnabled: Boolean,
        blockedDomains: Set<String>,
        whitelistedDomains: Set<String>,
    ) : this(
        isEnabled = isEnabled,
        builtinEnabled = false,
        builtinDomains = emptySet(),
        disabledBuiltins = emptySet(),
        customBlockedDomains = blockedDomains.mapNotNull(DomainNormalizer::normalizeHostOrNull).toHashSet(),
        whitelistedDomains = whitelistedDomains.mapNotNull(DomainNormalizer::normalizeHostOrNull).toHashSet(),
    )

    /** Returns `true` when website blocking has at least one active built-in or custom domain rule. */
    val hasActiveRules: Boolean
        get() =
            isEnabled &&
                (
                    customBlockedDomains.isNotEmpty() ||
                        (builtinEnabled && builtinDomains.size > disabledBuiltins.size)
                )

    /**
     * Checks whether a single URL or hostname [rawHostOrUrl] is blocked.
     *
     * @return The matched blocked domain rule, or `null` if allowed or whitelisted.
     */
    fun matchHost(rawHostOrUrl: String): String? {
        if (!hasActiveRules) return null
        val host = DomainNormalizer.normalizeHostOrNull(rawHostOrUrl) ?: return null
        if (findMatchingSuffix(host, whitelistedDomains) != null) return null
        return findMatchingBlockedSuffix(host)
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

    private fun findMatchingBlockedSuffix(host: String): String? {
        var candidate = host
        while (true) {
            if (candidate in customBlockedDomains) return candidate
            if (builtinEnabled && candidate in builtinDomains && candidate !in disabledBuiltins) {
                return candidate
            }
            val dotIdx = candidate.indexOf('.')
            if (dotIdx < 0 || dotIdx == candidate.lastIndex) return null
            candidate = candidate.substring(dotIdx + 1)
            if (!candidate.contains('.')) return null
        }
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
        // Omit Pattern.UNICODE_CHARACTER_CLASS: Android ART ICU enables Unicode classes by default
        // and throws IllegalArgumentException("Unsupported flags: 256") when flag 256 is passed.
        private val URL_TOKEN_PATTERN: Pattern =
            Pattern.compile(
                """(?:[a-zA-Z][a-zA-Z0-9+.-]*://)?(?:[\p{L}\p{N}-]+\.)+[\p{L}]{2,63}\.?(?::\d{1,5})?(?:[/?#][^\s]*)?""",
            )

        /**
         * Builds a [WebsiteDomainMatcher] by combining pre-normalized [builtinDomains] with all
         * enabled profiles in [config] using zero-copy reference sharing for the built-in set.
         */
        fun fromConfig(
            config: WebsiteBlockerConfig,
            builtinDomains: Collection<String>,
        ): WebsiteDomainMatcher {
            val activeProfiles = config.effectiveProfiles().filter { it.isEnabled }
            if (activeProfiles.isEmpty()) return disabledMatcher()
            val builtinProfile = activeProfiles.firstOrNull { it.isBuiltin }
            val builtinEnabled = builtinProfile != null
            val sharedBuiltins = resolveSharedBuiltins(builtinEnabled, builtinDomains)
            val disabledBuiltins =
                builtinProfile?.disabledBuiltinDomains
                    ?.mapNotNull(DomainNormalizer::normalizeHostOrNull)
                    ?.toHashSet()
                    ?: emptySet()
            val customBlocked = HashSet<String>()
            activeProfiles.forEach { profile ->
                profile.domains.mapNotNullTo(customBlocked, DomainNormalizer::normalizeHostOrNull)
            }
            val whitelisted = config.whitelistedDomains.mapNotNull(DomainNormalizer::normalizeHostOrNull).toHashSet()
            return WebsiteDomainMatcher(
                isEnabled = true,
                builtinEnabled = builtinEnabled,
                builtinDomains = sharedBuiltins,
                disabledBuiltins = disabledBuiltins,
                customBlockedDomains = customBlocked,
                whitelistedDomains = whitelisted,
            )
        }

        private fun disabledMatcher(): WebsiteDomainMatcher =
            WebsiteDomainMatcher(
                isEnabled = false,
                builtinEnabled = false,
                builtinDomains = emptySet(),
                disabledBuiltins = emptySet(),
                customBlockedDomains = emptySet(),
                whitelistedDomains = emptySet(),
            )

        private fun resolveSharedBuiltins(
            builtinEnabled: Boolean,
            builtinDomains: Collection<String>,
        ): Set<String> =
            when {
                !builtinEnabled -> emptySet()
                builtinDomains is Set<String> -> builtinDomains
                else -> builtinDomains.toHashSet()
            }
    }
}
