package com.warden.app.data.models

/**
 * Matching mode for a blocked word or phrase entry.
 *
 * @property wireValue Stable JSON representation persisted in `settings.json`.
 */
enum class MatchMode(val wireValue: String) {
    /** Matches only as a whole word or whole phrase bounded by non-letters/digits. */
    EXACT("exact"),

    /** Matches anywhere in text, including inside larger words. */
    CONTAINS("contains"),
    ;

    companion object {
        /** Parses a persisted [wireValue], defaulting unknown or missing values to [EXACT]. */
        fun fromWire(wireValue: String?): MatchMode = if (wireValue.equals(CONTAINS.wireValue, ignoreCase = true)) CONTAINS else EXACT
    }
}

/**
 * Single blocked word or phrase entry with its match mode and creation timestamp.
 *
 * @property term Normalized term (Unicode NFKC, lowercased, stripped diacritics, collapsed spaces).
 * @property mode Whether the term matches as an exact word/phrase or anywhere in text.
 * @property addedAt Wall-clock epoch milliseconds when the entry was added or updated.
 */
data class BlockedEntry(
    val term: String,
    val mode: MatchMode = MatchMode.EXACT,
    val addedAt: Long = 0L,
)

/**
 * A named website-blocking list/profile (such as the default built-in Adult Sites list
 * or a user-created list like "Social Media" or "Gaming").
 *
 * @property id Stable unique identifier ([BUILTIN_ADULT_ID] for the shipped adult list).
 * @property name User-visible list title.
 * @property isEnabled Whether this list actively blocks its domains.
 * @property isBuiltin Whether this list backs the shipped MIT-licensed adult domain catalog.
 * @property domains Normalized custom domains added by the user to this list.
 * @property disabledBuiltinDomains Normalized built-in domains individually disabled in this list.
 */
data class WebsiteBlockProfile(
    val id: String,
    val name: String,
    val isEnabled: Boolean = true,
    val isBuiltin: Boolean = false,
    val domains: List<String> = emptyList(),
    val disabledBuiltinDomains: List<String> = emptyList(),
) {
    companion object {
        /** Stable identifier for the default built-in Adult Sites profile. */
        const val BUILTIN_ADULT_ID: String = "builtin_adult"

        /** Default display name for the shipped built-in Adult Sites profile. */
        const val BUILTIN_ADULT_DEFAULT_NAME: String = "Adult Sites"
    }
}

/**
 * User configuration for website blocking lists/profiles, stored separately from the shipped
 * built-in domain asset so updating built-ins never erases user data.
 *
 * Legacy v2.3.x flat fields ([isEnabled], [userBlockedDomains], [disabledBuiltinDomains]) are
 * retained for seamless migration and backward-compatible unit tests; [effectiveProfiles] always
 * returns a normalized list containing the built-in profile plus any custom user profiles.
 */
data class WebsiteBlockerConfig(
    val isEnabled: Boolean = true,
    val userBlockedDomains: List<String> = emptyList(),
    val disabledBuiltinDomains: List<String> = emptyList(),
    val whitelistedDomains: List<String> = emptyList(),
    val profiles: List<WebsiteBlockProfile> = emptyList(),
) {
    /**
     * Returns the full list of website profiles, synthesizing the default built-in "Adult Sites"
     * profile from legacy fields when [profiles] is empty or omits the built-in entry.
     */
    fun effectiveProfiles(): List<WebsiteBlockProfile> {
        if (profiles.isEmpty()) {
            return listOf(defaultBuiltinProfile())
        }
        return if (profiles.any { it.isBuiltin }) {
            profiles
        } else {
            listOf(defaultBuiltinProfile()) + profiles
        }
    }

    /** Returns `true` when the built-in Adult Sites profile is currently enabled. */
    fun isBuiltinProfileEnabled(): Boolean = effectiveProfiles().any { it.isBuiltin && it.isEnabled }

    /** Returns `true` when at least one profile (built-in or custom) is enabled. */
    fun hasAnyEnabledProfile(): Boolean = effectiveProfiles().any { it.isEnabled }

    private fun defaultBuiltinProfile(): WebsiteBlockProfile =
        WebsiteBlockProfile(
            id = WebsiteBlockProfile.BUILTIN_ADULT_ID,
            name = WebsiteBlockProfile.BUILTIN_ADULT_DEFAULT_NAME,
            isEnabled = isEnabled,
            isBuiltin = true,
            domains = userBlockedDomains,
            disabledBuiltinDomains = disabledBuiltinDomains,
        )
}
