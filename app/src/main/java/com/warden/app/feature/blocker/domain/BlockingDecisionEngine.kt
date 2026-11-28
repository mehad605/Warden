package com.warden.app.feature.blocker.domain

import com.warden.app.data.models.Settings
import com.warden.app.feature.websites.domain.WebsiteDomainMatcher

/**
 * Thread-safe pure domain engine that holds the latest compiled [Settings] snapshot and
 * evaluates self-protection, app exemptions, keyword/phrase matches, and website domain matches.
 */
class BlockingDecisionEngine(
    initialSettings: Settings = Settings(),
    initialBuiltinDomains: Collection<String> = emptyList(),
) {
    private data class CompiledState(
        val settings: Settings,
        val matcher: KeywordMatcher,
        val websiteMatcher: WebsiteDomainMatcher,
        val ignoredApps: Set<String>,
        val builtinDomains: List<String>,
    )

    @Volatile
    private var state: CompiledState = compile(initialSettings, initialBuiltinDomains.toList())

    /** Atomically replaces the active configuration snapshot and recompiles word & domain matchers. */
    fun updateSettings(settings: Settings) {
        state = compile(settings, state.builtinDomains)
    }

    /** Supplies the shipped built-in adult domain list and recompiles the website matcher. */
    fun updateBuiltinDomains(domains: Collection<String>) {
        state = compile(state.settings, domains.toList())
    }

    /** Current configured grace period in seconds (`0` means instant block). */
    val gracePeriodSeconds: Int get() = state.settings.ignoreGracePeriodSeconds

    /** Current precompiled [KeywordMatcher]. */
    val matcher: KeywordMatcher get() = state.matcher

    /** Current precompiled [WebsiteDomainMatcher]. */
    val websiteMatcher: WebsiteDomainMatcher get() = state.websiteMatcher

    /** Evaluates whether [packageName] should be inspected for self-protection. */
    fun shouldInspectSelfProtection(packageName: String): Boolean = SelfProtectionEvaluator.shouldInspectPackage(packageName)

    /** Returns a self-protection warning message if the screen must be closed, or `null`. */
    fun evaluateSelfProtection(
        packageName: String,
        eventClassName: String,
        screenTexts: List<String>,
        nowMs: Long,
    ): String? {
        val current = state.settings
        return SelfProtectionEvaluator.evaluate(
            packageName = packageName,
            eventClassName = eventClassName,
            screenTexts = screenTexts,
            antiUninstallEnabled = current.antiUninstallEnabled,
            deviceAdminActivationRequestedAt = current.deviceAdminActivationRequestedAt,
            nowMs = nowMs,
        )
    }

    /** Returns `true` if scanning is active (keywords or websites) and [packageName] is not exempt at [nowMs]. */
    fun shouldScanPackage(
        packageName: String,
        nowMs: Long,
    ): Boolean {
        val current = state
        val hasAnyRules = current.matcher.hasBlockedKeywords || current.websiteMatcher.hasActiveRules
        val isActiveAndEnabled =
            current.settings.keywordBlockerConfig.isActive &&
                nowMs >= current.settings.blockerDisabledUntil &&
                hasAnyRules
        val isTargetPackage =
            packageName.isNotEmpty() &&
                packageName != WARDEN_PACKAGE &&
                !current.ignoredApps.contains(packageName)
        if (!isActiveAndEnabled || !isTargetPackage) return false
        val tempExpiry = current.settings.temporaryIgnoredApps[packageName]
        return tempExpiry == null || nowMs >= tempExpiry
    }

    /** Evaluates both word/phrase rules and website domain rules against [text]. */
    fun findMatch(text: String): KeywordMatch? {
        val current = state
        current.matcher.findMatch(text)?.let { return it }
        val domainHit = current.websiteMatcher.findMatchInText(text) ?: return null
        return KeywordMatch(keyword = domainHit.matchedDomain, context = domainHit.rawToken)
    }

    /** Formats the standard user-facing alert message for a [KeywordMatch]. */
    fun formatBlockedMessage(match: KeywordMatch): String = "Blocked word ${match.keyword} was found : \" ${match.context} \""

    private fun compile(
        settings: Settings,
        builtinDomains: List<String>,
    ): CompiledState =
        CompiledState(
            settings = settings,
            matcher =
                KeywordMatcher(
                    blockedEntries = settings.keywordBlockerConfig.effectiveBlockedEntries(),
                    whitelistedKeywords = settings.keywordBlockerConfig.whitelistedKeywords,
                ),
            websiteMatcher = WebsiteDomainMatcher.fromConfig(settings.websiteBlockerConfig, builtinDomains),
            ignoredApps = settings.keywordBlockerConfig.ignoredApps.toHashSet(),
            builtinDomains = builtinDomains,
        )

    companion object {
        private const val WARDEN_PACKAGE = "com.warden.app"
    }
}
