package com.warden.app.data.models

/**
 * Single immutable source of truth for all persisted Warden application settings.
 *
 * Serialized to `filesDir/datastore/settings.json` by `SettingsJsonCodec` using the exact
 * `baseline-v1` JSON field names so upgrades preserve all user configuration.
 */
data class Settings(
    val keywordBlockerConfig: KeywordBlocker = KeywordBlocker(),
    val websiteBlockerConfig: WebsiteBlockerConfig = WebsiteBlockerConfig(),
    val passwordHash: String? = null,
    val geminiApiKey: String? = null,
    val noCount: Int = 0,
    val temporaryIgnoredApps: Map<String, Long> = emptyMap(),
    val blockerDisabledUntil: Long = 0L,
    val ignoreGracePeriodSeconds: Int = 2,
    val selectedGeminiModel: String = "",
    val availableGeminiModels: List<String> = emptyList(),
    val antiUninstallEnabled: Boolean = false,
    val deviceAdminActivationRequestedAt: Long = 0L,
)
