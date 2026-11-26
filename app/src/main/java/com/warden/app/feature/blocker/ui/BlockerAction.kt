package com.warden.app.feature.blocker.ui

import com.warden.app.data.models.Settings
import com.warden.app.feature.blocker.domain.IgnoredAppsFormatter
import com.warden.app.feature.security.domain.PasswordPolicy
import java.util.Locale

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

    /** Adds [keyword] (normalized) to the blocked list. */
    data class AddBlockedKeyword(val keyword: String) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            val normalized = keyword.trim().lowercase(Locale.ROOT)
            val cfg = current.keywordBlockerConfig
            if (normalized.isEmpty() || normalized in cfg.blockedKeywords) return current
            return current.copy(keywordBlockerConfig = cfg.copy(blockedKeywords = cfg.blockedKeywords + normalized))
        }
    }

    /** Removes [keyword] from the blocked list. */
    data class RemoveBlockedKeyword(val keyword: String) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            val target = keyword.trim().lowercase(Locale.ROOT)
            val cfg = current.keywordBlockerConfig
            return current.copy(keywordBlockerConfig = cfg.copy(blockedKeywords = cfg.blockedKeywords - target))
        }
    }

    /** Adds [keyword] (normalized) to the whitelist. */
    data class AddWhitelistedKeyword(val keyword: String) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings {
            val normalized = keyword.trim().lowercase(Locale.ROOT)
            val cfg = current.keywordBlockerConfig
            if (normalized.isEmpty() || normalized in cfg.whitelistedKeywords) return current
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
            val target = keyword.trim().lowercase(Locale.ROOT)
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

    /** Sets or clears the Gemini API key. */
    data class SetGeminiApiKey(val apiKey: String?) : BlockerAction() {
        override fun applyTo(
            current: Settings,
            nowMs: Long,
        ): Settings = current.copy(geminiApiKey = apiKey)
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
