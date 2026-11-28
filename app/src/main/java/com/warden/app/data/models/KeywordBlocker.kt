package com.warden.app.data.models

/**
 * Persisted configuration for the real-time keyword blocker.
 *
 * Field names match the `baseline-v1` JSON schema verbatim for seamless upgrades,
 * extended with per-entry [blockedEntries] preserving match mode and timestamps.
 *
 * @property isActive Whether keyword scanning is currently enabled.
 * @property blockedKeywords Legacy flat list of blocked words kept in sync for backward compatibility.
 * @property whitelistedKeywords Phrases allowed even when overlapping blocked terms.
 * @property ignoredApps Package names permanently exempt from keyword scanning.
 * @property blockedEntries Structured blocked entries carrying per-term [MatchMode] and `addedAt`.
 */
data class KeywordBlocker(
    val isActive: Boolean = false,
    val blockedKeywords: List<String> = emptyList(),
    val whitelistedKeywords: List<String> = emptyList(),
    val ignoredApps: List<String> = emptyList(),
    val blockedEntries: List<BlockedEntry> =
        blockedKeywords.map { BlockedEntry(term = it, mode = MatchMode.EXACT, addedAt = 0L) },
) {
    /**
     * Returns the canonical list of blocked entries, migrating legacy [blockedKeywords]
     * to [MatchMode.EXACT] automatically if [blockedEntries] has not been populated yet.
     */
    fun effectiveBlockedEntries(): List<BlockedEntry> =
        if (blockedEntries.isNotEmpty()) {
            blockedEntries
        } else {
            blockedKeywords.map { BlockedEntry(term = it, mode = MatchMode.EXACT, addedAt = 0L) }
        }
}
