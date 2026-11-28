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
 * User customizations for the porn/adult website blocker, stored separately from the shipped
 * built-in domain list so updating built-ins never erases user data.
 *
 * @property isEnabled Master on/off toggle for website domain blocking.
 * @property userBlockedDomains Normalized domains added by the user.
 * @property disabledBuiltinDomains Normalized built-in domains individually disabled by the user.
 * @property whitelistedDomains Normalized domains that override both built-in and user blocklists.
 */
data class WebsiteBlockerConfig(
    val isEnabled: Boolean = true,
    val userBlockedDomains: List<String> = emptyList(),
    val disabledBuiltinDomains: List<String> = emptyList(),
    val whitelistedDomains: List<String> = emptyList(),
)
