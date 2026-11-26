package com.warden.app.ui.fragments.main.reducers.blockertools.keywordBlocker

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.warden.app.Warden
import com.warden.app.core.data.JsonSettingsRepository
import com.warden.app.core.data.SettingsRepository
import com.warden.app.data.models.KeywordBlocker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class KeywordBlockerViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository =
        (application as? Warden)?.container?.settingsRepository
            ?: JsonSettingsRepository(File(application.filesDir, "datastore/settings.json"))
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        settingsRepository = (application as? Warden)?.container?.settingsRepository
            ?: JsonSettingsRepository(File(application.filesDir, "datastore/settings.json"))
    )

    private val _keywordBlockerConfig = MutableStateFlow(KeywordBlocker())
    val keywordBlockerConfig: StateFlow<KeywordBlocker> = _keywordBlockerConfig

    private val _passwordHash = MutableStateFlow<String?>(null)
    val passwordHash: StateFlow<String?> = _passwordHash

    private val _geminiApiKey = MutableStateFlow<String?>(null)
    val geminiApiKey: StateFlow<String?> = _geminiApiKey

    private val _noCount = MutableStateFlow(0)
    val noCount: StateFlow<Int> = _noCount

    private val _temporaryIgnoredApps = MutableStateFlow<Map<String, Long>>(emptyMap())
    val temporaryIgnoredApps: StateFlow<Map<String, Long>> = _temporaryIgnoredApps

    private val _blockerDisabledUntil = MutableStateFlow(0L)
    val blockerDisabledUntil: StateFlow<Long> = _blockerDisabledUntil

    private val _ignoreGracePeriodSeconds = MutableStateFlow(2)
    val ignoreGracePeriodSeconds: StateFlow<Int> = _ignoreGracePeriodSeconds

    private val _selectedGeminiModel = MutableStateFlow("models/gemini-1.5-flash")
    val selectedGeminiModel: StateFlow<String> = _selectedGeminiModel

    private val _availableGeminiModels = MutableStateFlow<List<String>>(emptyList())
    val availableGeminiModels: StateFlow<List<String>> = _availableGeminiModels

    private val _antiUninstallEnabled = MutableStateFlow(false)
    val antiUninstallEnabled: StateFlow<Boolean> = _antiUninstallEnabled

    private val _deviceAdminActivationRequestedAt = MutableStateFlow(0L)
    val deviceAdminActivationRequestedAt: StateFlow<Long> = _deviceAdminActivationRequestedAt

    init {
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                _keywordBlockerConfig.value = settings.keywordBlockerConfig
                _passwordHash.value = settings.passwordHash
                _geminiApiKey.value = settings.geminiApiKey
                _noCount.value = settings.noCount
                _temporaryIgnoredApps.value = settings.temporaryIgnoredApps
                _blockerDisabledUntil.value = settings.blockerDisabledUntil
                _ignoreGracePeriodSeconds.value = settings.ignoreGracePeriodSeconds
                _selectedGeminiModel.value = settings.selectedGeminiModel
                _availableGeminiModels.value = settings.availableGeminiModels
                _antiUninstallEnabled.value = settings.antiUninstallEnabled
                _deviceAdminActivationRequestedAt.value = settings.deviceAdminActivationRequestedAt
            }
        }
    }

    private fun updateConfig(newConfig: KeywordBlocker) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(keywordBlockerConfig = newConfig) }
        }
    }

    fun setIsActive(isActive: Boolean) {
        updateConfig(_keywordBlockerConfig.value.copy(isActive = isActive))
    }

    fun addKeyword(keyword: String) {
        val currentKeywords = _keywordBlockerConfig.value.blockedKeywords.toMutableList()
        val trimmed = keyword.trim().lowercase(Locale.ROOT)
        if (!currentKeywords.contains(trimmed) && trimmed.isNotBlank()) {
            currentKeywords.add(trimmed)
            updateConfig(_keywordBlockerConfig.value.copy(blockedKeywords = currentKeywords))
        }
    }

    fun removeKeyword(keyword: String) {
        val currentKeywords = _keywordBlockerConfig.value.blockedKeywords.toMutableList()
        val target = keyword.trim().lowercase(Locale.ROOT)
        if (currentKeywords.contains(target)) {
            currentKeywords.remove(target)
            updateConfig(_keywordBlockerConfig.value.copy(blockedKeywords = currentKeywords))
        }
    }

    fun addWhitelistedKeyword(keyword: String) {
        val currentKeywords = _keywordBlockerConfig.value.whitelistedKeywords.toMutableList()
        val trimmed = keyword.trim().lowercase(Locale.ROOT)
        if (!currentKeywords.contains(trimmed) && trimmed.isNotBlank()) {
            currentKeywords.add(trimmed)
            updateConfig(_keywordBlockerConfig.value.copy(whitelistedKeywords = currentKeywords))
        }
    }

    fun removeWhitelistedKeyword(keyword: String) {
        val currentKeywords = _keywordBlockerConfig.value.whitelistedKeywords.toMutableList()
        val target = keyword.trim().lowercase(Locale.ROOT)
        if (currentKeywords.contains(target)) {
            currentKeywords.remove(target)
            updateConfig(_keywordBlockerConfig.value.copy(whitelistedKeywords = currentKeywords))
        }
    }

    fun setIgnoredApps(list: List<String>) {
        updateConfig(_keywordBlockerConfig.value.copy(ignoredApps = list))
    }

    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setPassword(password: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(passwordHash = hashPassword(password)) }
        }
    }

    fun removePassword() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(passwordHash = null) }
        }
    }

    fun verifyPassword(password: String): Boolean {
        val currentHash = _passwordHash.value ?: return true
        return hashPassword(password) == currentHash
    }

    fun setGeminiApiKey(key: String?) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(geminiApiKey = key) }
        }
    }

    fun setNoCount(count: Int) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(noCount = count) }
        }
    }

    fun setTemporaryIgnoredApps(apps: Map<String, Long>) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(temporaryIgnoredApps = apps) }
        }
    }

    fun setBlockerDisabledUntil(timestamp: Long) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(blockerDisabledUntil = timestamp) }
        }
    }

    fun setIgnoreGracePeriodSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(ignoreGracePeriodSeconds = seconds) }
        }
    }

    fun setAntiUninstallEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(antiUninstallEnabled = enabled) }
        }
    }

    fun setDeviceAdminActivationRequestedAt(timestamp: Long) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(deviceAdminActivationRequestedAt = timestamp) }
        }
    }

    fun setSelectedGeminiModel(model: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(selectedGeminiModel = model) }
        }
    }

    fun setAvailableGeminiModels(models: List<String>) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(availableGeminiModels = models) }
        }
    }

    fun exportSettings(includePassword: Boolean = true, includeApiKey: Boolean = true, onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult(settingsRepository.exportJson(includePassword, includeApiKey))
        }
    }

    fun importSettings(json: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = settingsRepository.importJson(json)
            onResult(success)
        }
    }
}
