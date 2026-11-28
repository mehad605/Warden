package com.warden.app.feature.blocker.ui

import com.warden.app.data.models.BlockedEntry
import com.warden.app.data.models.MatchMode
import com.warden.app.data.models.Settings
import com.warden.app.feature.blocker.domain.IgnoredAppsFormatter
import com.warden.app.feature.blocker.domain.TextNormalizer
import com.warden.app.feature.security.domain.PasswordPolicy

/**
 * Unidirectional, self-reducing actions mutating persisted Warden [Settings].
 *
 * Each action encapsulates its own pure state transition ([applyTo]), eliminating a giant central
 * `when` block and allowing new settings or features to add actions without modifying existing code.
 */
sealed class BlockerAction {
    /** Produces the next [Settings] state given [current] and wall-clock timestamp [nowMs]. */
    abstract fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings

    /** Enables or disables keyword scanning. */
    data class SetBlockerActive(val active: Boolean) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings = current.copy(keywordBlockerConfig = current.keywordBlockerConfig.copy(isActive = active))
    }

    /**
     * Adds [keyword] (normalized) with [mode], or updates the existing entry's [mode] in place
     * without creating a duplicate.
     */
    data class AddBlockedKeyword(
        val keyword: String,
        val mode: MatchMode = MatchMode.EXACT,
    ) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            val normalized = TextNormalizer.normalizeTerm(keyword)
            if (normalized.isEmpty() || normalized.length > TextNormalizer.MAX_TERM_LENGTH) return current
            val cfg = current.keywordBlockerConfig
            val existing = cfg.effectiveBlockedEntries()
            val idx = existing.indexOfFirst { it.term == normalized }
            val updated =
                if (idx >= 0) {
                    existing.toMutableList().apply {
                        this[idx] = this[idx].copy(mode = mode)
                    }
                } else {
                    existing + BlockedEntry(term = normalized, mode = mode, addedAt = nowMs)
                }
            return current.copy(
                keywordBlockerConfig =
                    cfg.copy(
                        blockedKeywords = updated.map { it.term },
                        blockedEntries = updated,
                    ),
            )
        }
    }

    /** Restores a previously deleted [entry] (used for 5-second undo). */
    data class RestoreBlockedEntry(val entry: BlockedEntry) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            val cfg = current.keywordBlockerConfig
            val existing = cfg.effectiveBlockedEntries()
            if (existing.any { it.term == entry.term }) return current
            val updated = existing + entry
            return current.copy(
                keywordBlockerConfig =
                    cfg.copy(
                        blockedKeywords = updated.map { it.term },
                        blockedEntries = updated,
                    ),
            )
        }
    }

    /** Updates the match [mode] of an existing blocked [term] inline. */
    data class SetBlockedKeywordMode(
        val term: String,
        val mode: MatchMode,
    ) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            val target = TextNormalizer.normalizeTerm(term)
            val cfg = current.keywordBlockerConfig
            val updated =
                cfg.effectiveBlockedEntries().map { entry ->
                    if (entry.term == target) entry.copy(mode = mode) else entry
                }
            return current.copy(
                keywordBlockerConfig =
                    cfg.copy(
                        blockedKeywords = updated.map { it.term },
                        blockedEntries = updated,
                    ),
            )
        }
    }

    /** Removes [keyword] from the blocked list. */
    data class RemoveBlockedKeyword(val keyword: String) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            val target = TextNormalizer.normalizeTerm(keyword)
            val cfg = current.keywordBlockerConfig
            val updated = cfg.effectiveBlockedEntries().filterNot { it.term == target }
            return current.copy(
                keywordBlockerConfig =
                    cfg.copy(
                        blockedKeywords = updated.map { it.term },
                        blockedEntries = updated,
                    ),
            )
        }
    }

    /** Adds [keyword] (normalized) to the whitelist. */
    data class AddWhitelistedKeyword(val keyword: String) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            val normalized = TextNormalizer.normalizeTerm(keyword)
            val cfg = current.keywordBlockerConfig
            if (normalized.isEmpty() || normalized.length > TextNormalizer.MAX_TERM_LENGTH || normalized in cfg.whitelistedKeywords) {
                return current
            }
            return current.copy(
                keywordBlockerConfig = cfg.copy(whitelistedKeywords = cfg.whitelistedKeywords + normalized),
            )
        }
    }

    /** Removes [keyword] from the whitelist. */
    data class RemoveWhitelistedKeyword(val keyword: String) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            val target = TextNormalizer.normalizeTerm(keyword)
            val cfg = current.keywordBlockerConfig
            return current.copy(keywordBlockerConfig = cfg.copy(whitelistedKeywords = cfg.whitelistedKeywords - target))
        }
    }

    /** Sets the grace period delay in seconds (`0..5`). */
    data class SetGracePeriodSeconds(val seconds: Int) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings = current.copy(ignoreGracePeriodSeconds = seconds)
    }

    /** Sets or clears the Gemini API key, resetting cached model list when changed. */
    data class SetGeminiApiKey(val apiKey: String?) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            if (current.geminiApiKey == apiKey) return current
            return current.copy(
                geminiApiKey = apiKey,
                availableGeminiModels = emptyList(),
                selectedGeminiModel = if (apiKey == null) "" else current.selectedGeminiModel,
            )
        }
    }

    /** Selects the active Gemini model identifier. */
    data class SelectGeminiModel(val model: String) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings = current.copy(selectedGeminiModel = model)
    }

    /** Updates the password hash from plaintext [newPassword], or clears it when `null`. */
    data class SetPassword(val newPassword: String?) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings = current.copy(passwordHash = newPassword?.let(PasswordPolicy::hashSha256))
    }

    /** Persists whether Anti-Uninstall Device Admin protection is active. */
    data class SetAntiUninstallEnabled(val enabled: Boolean) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings = current.copy(antiUninstallEnabled = enabled)
    }

    /** Records the timestamp when Device Admin activation was requested (180s grace window). */
    data object RecordAdminActivationRequest : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings = current.copy(deviceAdminActivationRequestedAt = nowMs)
    }

    /** Grants a temporary app exemption lasting [durationMinutes]. */
    data class GrantTempExemption(val packageName: String, val durationMinutes: Int) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings =
            current.copy(
                temporaryIgnoredApps =
                    current.temporaryIgnoredApps +
                        (packageName to nowMs + durationMinutes * MS_PER_MINUTE),
            )
    }

    /** Extends an active temporary exemption by [additionalMinutes]. */
    data class ExtendTempExemption(val packageName: String, val additionalMinutes: Int) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            val base = current.temporaryIgnoredApps[packageName] ?: nowMs
            return current.copy(
                temporaryIgnoredApps =
                    current.temporaryIgnoredApps +
                        (packageName to base + additionalMinutes * MS_PER_MINUTE),
            )
        }
    }

    /** Sets a shorter remaining duration (`<= 0` removes the temporary exemption). */
    data class DecreaseTempExemption(val packageName: String, val newMinutes: Int) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            val updated = current.temporaryIgnoredApps.toMutableMap()
            if (newMinutes <= 0) {
                updated.remove(packageName)
            } else {
                updated[packageName] = nowMs + newMinutes * MS_PER_MINUTE
            }
            return current.copy(temporaryIgnoredApps = updated)
        }
    }

    /** Removes [packageName] from both permanent and temporary ignore lists. */
    data class RemoveAppFromIgnoreLists(val packageName: String) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings =
            current.copy(
                keywordBlockerConfig =
                    current.keywordBlockerConfig.copy(
                        ignoredApps = current.keywordBlockerConfig.ignoredApps.filter { it != packageName },
                    ),
                temporaryIgnoredApps = current.temporaryIgnoredApps - packageName,
            )
    }

    /** Prunes any expired entries from `temporaryIgnoredApps`. */
    data object PruneExpiredTempApps : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            val pruned = IgnoredAppsFormatter.pruneExpired(current.temporaryIgnoredApps, nowMs)
            return if (pruned != null) current.copy(temporaryIgnoredApps = pruned) else current
        }
    }

    /** Increments the historical AI Gatekeeper rejection count (`noCount`). */
    data object IncrementRejectionCount : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings = current.copy(noCount = current.noCount + 1)
    }

    companion object {
        private const val MS_PER_MINUTE = 60_000L
    }
}
