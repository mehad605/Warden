package com.warden.app.feature.blocker.domain

import com.warden.app.data.models.Settings

/**
 * Thread-safe pure domain engine that holds the latest compiled [Settings] snapshot and
 * evaluates self-protection, app exemptions, and keyword matches.
 */
class BlockingDecisionEngine(initialSettings: Settings = Settings()) {
    private data class CompiledState(
        val settings: Settings,
        val matcher: KeywordMatcher,
        val ignoredApps: Set<String>,
    )

    @Volatile
    private var state: CompiledState = compile(initialSettings)

    /** Atomically replaces the active configuration snapshot and recompiles the keyword regex. */
    fun updateSettings(settings: Settings) {
        state = compile(settings)
    }

    /** Current configured grace period in seconds (`0` means instant block). */
    val gracePeriodSeconds: Int get() = state.settings.ignoreGracePeriodSeconds

    /** Current precompiled [KeywordMatcher]. */
    val matcher: KeywordMatcher get() = state.matcher

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

    /** Returns `true` if keyword scanning is active and [packageName] is not exempt at [nowMs]. */
    fun shouldScanPackage(
        packageName: String,
        nowMs: Long,
    ): Boolean {
        val current = state
        val isActiveAndEnabled =
            current.settings.keywordBlockerConfig.isActive &&
                nowMs >= current.settings.blockerDisabledUntil &&
                current.matcher.hasBlockedKeywords
        val isTargetPackage =
            packageName.isNotEmpty() &&
                packageName != WARDEN_PACKAGE &&
                !current.ignoredApps.contains(packageName)
        if (!isActiveAndEnabled || !isTargetPackage) return false
        val tempExpiry = current.settings.temporaryIgnoredApps[packageName]
        return tempExpiry == null || nowMs >= tempExpiry
    }

    /** Formats the standard user-facing toast message for a [KeywordMatch]. */
    fun formatBlockedMessage(match: KeywordMatch): String = "Blocked word ${match.keyword} was found : \" ${match.context} \""

    private fun compile(settings: Settings): CompiledState =
        CompiledState(
            settings = settings,
            matcher =
                KeywordMatcher(
                    blockedKeywords = settings.keywordBlockerConfig.blockedKeywords,
                    whitelistedKeywords = settings.keywordBlockerConfig.whitelistedKeywords,
                ),
            ignoredApps = settings.keywordBlockerConfig.ignoredApps.toHashSet(),
        )

    companion object {
        private const val WARDEN_PACKAGE = "com.warden.app"
    }
}
