package com.warden.app.data.models

data class KeywordBlocker(
    val isActive: Boolean = false,
    val blockedKeywords: List<String> = emptyList(),
    val ignoredApps: List<String> = emptyList()
)
