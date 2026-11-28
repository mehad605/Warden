package com.warden.app.feature.blocker.ui

import com.warden.app.data.models.Settings
import com.warden.app.feature.websites.domain.DomainNormalizer

/** Toggles the master on/off switch for website domain blocking. */
data class SetWebsiteBlockerEnabled(val enabled: Boolean) : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings = current.copy(websiteBlockerConfig = current.websiteBlockerConfig.copy(isEnabled = enabled))
}

/** Adds a user-specified [rawDomain] (URL or host) to the website blocklist, deduping silently. */
data class AddUserBlockedDomain(val rawDomain: String) : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings {
        val domain = DomainNormalizer.normalizeHostOrNull(rawDomain) ?: return current
        val cfg = current.websiteBlockerConfig
        val updatedDisabled = cfg.disabledBuiltinDomains - domain
        val updatedUser = if (domain in cfg.userBlockedDomains) cfg.userBlockedDomains else cfg.userBlockedDomains + domain
        return current.copy(
            websiteBlockerConfig =
                cfg.copy(
                    userBlockedDomains = updatedUser,
                    disabledBuiltinDomains = updatedDisabled,
                ),
        )
    }
}

/** Removes a user-added [domain] from the website blocklist. */
data class RemoveUserBlockedDomain(val domain: String) : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings {
        val normalized = DomainNormalizer.normalizeHostOrNull(domain) ?: domain
        val cfg = current.websiteBlockerConfig
        return current.copy(
            websiteBlockerConfig = cfg.copy(userBlockedDomains = cfg.userBlockedDomains - normalized),
        )
    }
}

/** Individually disables or re-enables a shipped built-in [domain] without modifying the asset file. */
data class SetBuiltinDomainDisabled(
    val domain: String,
    val disabled: Boolean,
) : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings {
        val normalized = DomainNormalizer.normalizeHostOrNull(domain) ?: domain
        val cfg = current.websiteBlockerConfig
        val updated =
            if (disabled) {
                if (normalized in cfg.disabledBuiltinDomains) cfg.disabledBuiltinDomains else cfg.disabledBuiltinDomains + normalized
            } else {
                cfg.disabledBuiltinDomains - normalized
            }
        return current.copy(websiteBlockerConfig = cfg.copy(disabledBuiltinDomains = updated))
    }
}

/** Restores all individually disabled built-in adult domains. */
data object ResetDisabledBuiltinDomains : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings = current.copy(websiteBlockerConfig = current.websiteBlockerConfig.copy(disabledBuiltinDomains = emptyList()))
}

/** Adds a whitelisted [rawDomain] that overrides both built-in and user website blocklists. */
data class AddWhitelistedDomain(val rawDomain: String) : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings {
        val domain = DomainNormalizer.normalizeHostOrNull(rawDomain) ?: return current
        val cfg = current.websiteBlockerConfig
        if (domain in cfg.whitelistedDomains) return current
        return current.copy(
            websiteBlockerConfig = cfg.copy(whitelistedDomains = cfg.whitelistedDomains + domain),
        )
    }
}

/** Removes a [domain] from the website whitelist. */
data class RemoveWhitelistedDomain(val domain: String) : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings {
        val normalized = DomainNormalizer.normalizeHostOrNull(domain) ?: domain
        val cfg = current.websiteBlockerConfig
        return current.copy(
            websiteBlockerConfig = cfg.copy(whitelistedDomains = cfg.whitelistedDomains - normalized),
        )
    }
}
