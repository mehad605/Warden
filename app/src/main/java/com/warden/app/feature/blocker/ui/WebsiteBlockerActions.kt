package com.warden.app.feature.blocker.ui

import com.warden.app.data.models.Settings
import com.warden.app.data.models.WebsiteBlockProfile
import com.warden.app.data.models.WebsiteBlockerConfig
import com.warden.app.feature.websites.domain.DomainNormalizer

private fun WebsiteBlockerConfig.withUpdatedProfiles(updated: List<WebsiteBlockProfile>): WebsiteBlockerConfig {
    val builtin = updated.firstOrNull { it.isBuiltin }
    return copy(
        isEnabled = builtin?.isEnabled ?: isEnabled,
        userBlockedDomains = builtin?.domains ?: userBlockedDomains,
        disabledBuiltinDomains = builtin?.disabledBuiltinDomains ?: disabledBuiltinDomains,
        profiles = updated,
    )
}

/** Creates a new enabled custom website-blocking profile with [profileId] and [name]. */
data class CreateWebsiteProfile(
    val profileId: String,
    val name: String,
) : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings {
        val cleanName = name.trim().take(MAX_PROFILE_NAME_LENGTH)
        if (cleanName.isEmpty()) return current
        val existing = current.websiteBlockerConfig.effectiveProfiles()
        if (existing.any { it.id == profileId }) return current
        val newProfile =
            WebsiteBlockProfile(
                id = profileId,
                name = cleanName,
                isEnabled = true,
                isBuiltin = false,
            )
        return current.copy(
            websiteBlockerConfig = current.websiteBlockerConfig.withUpdatedProfiles(existing + newProfile),
        )
    }

    companion object {
        /** Maximum character length for a custom profile display name. */
        const val MAX_PROFILE_NAME_LENGTH: Int = 40
    }
}

/** Renames an existing website-blocking profile identified by [profileId]. */
data class RenameWebsiteProfile(
    val profileId: String,
    val newName: String,
) : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings {
        val cleanName = newName.trim().take(CreateWebsiteProfile.MAX_PROFILE_NAME_LENGTH)
        if (cleanName.isEmpty()) return current
        val updated =
            current.websiteBlockerConfig.effectiveProfiles().map { profile ->
                if (profile.id == profileId && !profile.isBuiltin) profile.copy(name = cleanName) else profile
            }
        return current.copy(websiteBlockerConfig = current.websiteBlockerConfig.withUpdatedProfiles(updated))
    }
}

/** Deletes a user-created website-blocking profile (the built-in Adult Sites profile cannot be deleted). */
data class DeleteWebsiteProfile(val profileId: String) : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings {
        val updated =
            current.websiteBlockerConfig.effectiveProfiles().filterNot { it.id == profileId && !it.isBuiltin }
        return current.copy(websiteBlockerConfig = current.websiteBlockerConfig.withUpdatedProfiles(updated))
    }
}

/** Toggles an individual website-blocking profile ON or OFF. */
data class SetWebsiteProfileEnabled(
    val profileId: String,
    val enabled: Boolean,
) : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings {
        val updated =
            current.websiteBlockerConfig.effectiveProfiles().map { profile ->
                if (profile.id == profileId) profile.copy(isEnabled = enabled) else profile
            }
        return current.copy(websiteBlockerConfig = current.websiteBlockerConfig.withUpdatedProfiles(updated))
    }
}

/** Adds a normalized URL/domain [rawDomain] to the website profile identified by [profileId]. */
data class AddDomainToProfile(
    val profileId: String,
    val rawDomain: String,
) : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings {
        val domain = DomainNormalizer.normalizeHostOrNull(rawDomain) ?: return current
        val updated =
            current.websiteBlockerConfig.effectiveProfiles().map { profile ->
                if (profile.id != profileId) {
                    profile
                } else {
                    val nextDomains = if (domain in profile.domains) profile.domains else profile.domains + domain
                    profile.copy(
                        domains = nextDomains,
                        disabledBuiltinDomains = profile.disabledBuiltinDomains - domain,
                    )
                }
            }
        return current.copy(websiteBlockerConfig = current.websiteBlockerConfig.withUpdatedProfiles(updated))
    }
}

/** Removes a user-added [domain] from the website profile identified by [profileId]. */
data class RemoveDomainFromProfile(
    val profileId: String,
    val domain: String,
) : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings {
        val normalized = DomainNormalizer.normalizeHostOrNull(domain) ?: domain
        val updated =
            current.websiteBlockerConfig.effectiveProfiles().map { profile ->
                if (profile.id == profileId) profile.copy(domains = profile.domains - normalized) else profile
            }
        return current.copy(websiteBlockerConfig = current.websiteBlockerConfig.withUpdatedProfiles(updated))
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
        val updated =
            current.websiteBlockerConfig.effectiveProfiles().map { profile ->
                if (!profile.isBuiltin) {
                    profile
                } else {
                    val nextDisabled =
                        if (disabled) {
                            if (normalized in profile.disabledBuiltinDomains) {
                                profile.disabledBuiltinDomains
                            } else {
                                profile.disabledBuiltinDomains + normalized
                            }
                        } else {
                            profile.disabledBuiltinDomains - normalized
                        }
                    profile.copy(disabledBuiltinDomains = nextDisabled)
                }
            }
        return current.copy(websiteBlockerConfig = current.websiteBlockerConfig.withUpdatedProfiles(updated))
    }
}

/** Restores all individually disabled built-in adult domains. */
data object ResetDisabledBuiltinDomains : BlockerAction() {
    override fun applyTo(
        current: Settings,
        nowMs: Long,
    ): Settings {
        val updated =
            current.websiteBlockerConfig.effectiveProfiles().map { profile ->
                if (profile.isBuiltin) profile.copy(disabledBuiltinDomains = emptyList()) else profile
            }
        return current.copy(websiteBlockerConfig = current.websiteBlockerConfig.withUpdatedProfiles(updated))
    }
}
