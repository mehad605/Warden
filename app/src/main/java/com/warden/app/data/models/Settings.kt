package com.warden.app.data.models

data class Settings(
    val keywordBlockerConfig: KeywordBlocker = KeywordBlocker(),
    val passwordHash: String? = null,
    val geminiApiKey: String? = null,
    val noCount: Int = 0,
    val temporaryIgnoredApps: Map<String, Long> = emptyMap(),
    val blockerDisabledUntil: Long = 0L,
    val ignoreGracePeriodSeconds: Int = 2,
    val selectedGeminiModel: String = "",
    val availableGeminiModels: List<String> = emptyList(),
    val antiUninstallEnabled: Boolean = false,
    val deviceAdminActivationRequestedAt: Long = 0L
)
