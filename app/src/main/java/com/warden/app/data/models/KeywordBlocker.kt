package com.warden.app.data.models

/**
 * Persisted configuration for the real-time keyword blocker.
 *
 * Field names match the `baseline-v1` JSON schema verbatim for seamless upgrades.
 *
 * @property isActive Whether keyword scanning is currently enabled.
 * @property blockedKeywords Lowercased blocked words and phrases.
 * @property whitelistedKeywords Phrases masked before keyword matching to prevent false positives.
 * @property ignoredApps Package names permanently exempt from keyword scanning.
 */
data class KeywordBlocker(
    val isActive: Boolean = false,
    val blockedKeywords: List<String> = emptyList(),
    val whitelistedKeywords: List<String> = emptyList(),
    val ignoredApps: List<String> = emptyList(),
)
