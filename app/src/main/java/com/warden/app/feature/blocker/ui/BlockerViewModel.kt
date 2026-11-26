package com.warden.app.feature.blocker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.warden.app.core.data.SettingsRepository
import com.warden.app.core.platform.Clock
import com.warden.app.data.models.Settings
import com.warden.app.feature.gatekeeper.domain.EncodedImage
import com.warden.app.feature.gatekeeper.domain.GatekeeperClient
import com.warden.app.feature.gatekeeper.domain.GatekeeperRequest
import com.warden.app.feature.gatekeeper.domain.GatekeeperVerdict
import com.warden.app.feature.security.domain.PasswordPolicy
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Settings dashboard ViewModel exposing reactive [Settings] state and explicit domain actions.
 */
class BlockerViewModel(
    private val settingsRepository: SettingsRepository,
    private val gatekeeperClient: GatekeeperClient,
    private val clock: Clock
) : ViewModel() {

    /** Single reactive state stream for the dashboard UI. */
    val settings: StateFlow<Settings> = settingsRepository.settings

    /** Dispatches a self-reducing [action] to [SettingsRepository]. */
    fun dispatch(action: BlockerAction) {
        viewModelScope.launch {
            val nowMs = clock.currentTimeMillis()
            settingsRepository.update { current -> action.applyTo(current, nowMs) }
        }
    }

    /** Verifies [candidate] against the stored SHA-256 password hash. */
    fun verifyPassword(candidate: String): Boolean =
        PasswordPolicy.verify(candidate, settings.value.passwordHash)

    /** Fetches available `generateContent` Gemini models and persists the refreshed list. */
    suspend fun refreshGeminiModels(): List<String> {
        val apiKey = settings.value.geminiApiKey.orEmpty()
        if (apiKey.isBlank()) return emptyList()
        val models = gatekeeperClient.fetchModels(apiKey)
        settingsRepository.update { it.copy(availableGeminiModels = models) }
        return models
    }

    /** Evaluates an AI Gatekeeper [request] using the stored API key and selected model. */
    suspend fun evaluateGatekeeper(
        request: GatekeeperRequest,
        image: EncodedImage?
    ): GatekeeperVerdict {
        val snapshot = settings.value
        val apiKey = snapshot.geminiApiKey.orEmpty()
        check(apiKey.isNotBlank()) { "Gemini API key is not configured." }
        val model = snapshot.selectedGeminiModel.ifBlank { DEFAULT_GEMINI_MODEL }
        return gatekeeperClient.evaluate(apiKey, model, request, image)
    }

    /** Serializes current settings for backup export. */
    suspend fun exportSettingsJson(includePassword: Boolean, includeApiKey: Boolean): String =
        settingsRepository.exportJson(includePassword, includeApiKey)

    /** Imports backup [json] using merge-only semantics. */
    suspend fun importSettingsJson(json: String): Boolean =
        settingsRepository.importJson(json)

    /** Factory constructing [BlockerViewModel] with explicit dependencies from `AppContainer`. */
    class Factory(
        private val settingsRepository: SettingsRepository,
        private val gatekeeperClient: GatekeeperClient,
        private val clock: Clock
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BlockerViewModel::class.java)) {
                "Unsupported ViewModel class: ${modelClass.name}"
            }
            return BlockerViewModel(settingsRepository, gatekeeperClient, clock) as T
        }
    }

    companion object {
        private const val DEFAULT_GEMINI_MODEL = "models/gemini-1.5-flash"
    }
}
