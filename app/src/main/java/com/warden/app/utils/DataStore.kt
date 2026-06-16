package com.warden.app.utils

import android.content.Context
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import com.google.gson.Gson
import com.warden.app.data.models.Settings
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type
import kotlinx.coroutines.flow.first

class GsonSerializer<T>(
    private val gson: Gson,
    private val type: Type,
    override val defaultValue: T
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T {
        return try {
            gson.fromJson(input.readBytes().decodeToString(), type) ?: defaultValue
        } catch (e: Exception) {
            e.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(gson.toJson(t).toByteArray())
    }
}

class DataStoreManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        @Volatile
        private var INSTANCE: androidx.datastore.core.DataStore<Settings>? = null

        fun getSettingsDataStore(context: Context, gson: Gson): androidx.datastore.core.DataStore<Settings> {
            return INSTANCE ?: synchronized(this) {
                val instance = MultiProcessDataStoreFactory.create(
                    serializer = GsonSerializer(
                        gson = gson,
                        type = Settings::class.java,
                        defaultValue = Settings()
                    ),
                    produceFile = { File(context.applicationContext.filesDir, "datastore/settings.json") }
                )
                INSTANCE = instance
                instance
            }
        }
    }

    private val settingsDataStore = getSettingsDataStore(context, gson)

    val settings = settingsDataStore.data

    suspend fun updateKeywordBlockerConfig(config: com.warden.app.data.models.KeywordBlocker) {
        settingsDataStore.updateData { it.copy(keywordBlockerConfig = config) }
    }

    suspend fun updatePasswordHash(hash: String?) {
        settingsDataStore.updateData { it.copy(passwordHash = hash) }
    }

    suspend fun updateGeminiApiKey(key: String?) {
        settingsDataStore.updateData { it.copy(geminiApiKey = key) }
    }

    suspend fun updateNoCount(count: Int) {
        settingsDataStore.updateData { it.copy(noCount = count) }
    }

    suspend fun updateTemporaryIgnoredApps(apps: Map<String, Long>) {
        settingsDataStore.updateData { it.copy(temporaryIgnoredApps = apps) }
    }

    suspend fun updateBlockerDisabledUntil(timestamp: Long) {
        settingsDataStore.updateData { it.copy(blockerDisabledUntil = timestamp) }
    }

    suspend fun updateIgnoreGracePeriodSeconds(seconds: Int) {
        settingsDataStore.updateData { it.copy(ignoreGracePeriodSeconds = seconds) }
    }

    suspend fun updateSelectedGeminiModel(model: String) {
        settingsDataStore.updateData { it.copy(selectedGeminiModel = model) }
    }

    suspend fun updateAvailableGeminiModels(models: List<String>) {
        settingsDataStore.updateData { it.copy(availableGeminiModels = models) }
    }

    suspend fun updateAntiUninstallEnabled(enabled: Boolean) {
        settingsDataStore.updateData { it.copy(antiUninstallEnabled = enabled) }
    }

    suspend fun updateDeviceAdminActivationRequestedAt(timestamp: Long) {
        settingsDataStore.updateData { it.copy(deviceAdminActivationRequestedAt = timestamp) }
    }

    suspend fun getSettingsJson(includePassword: Boolean = true, includeApiKey: Boolean = true): String {
        val currentSettings = settingsDataStore.data.first()
        val exportSettings = currentSettings.copy(
            passwordHash = if (includePassword) currentSettings.passwordHash else null,
            geminiApiKey = if (includeApiKey) currentSettings.geminiApiKey else null,
            antiUninstallEnabled = false,
            deviceAdminActivationRequestedAt = 0L
        )
        return gson.toJson(exportSettings)
    }

    suspend fun importSettingsJson(json: String): Boolean {
        return try {
            val importedSettings = gson.fromJson(json, Settings::class.java) ?: return false
            
            // Fix nullability caused by Gson bypassing Kotlin constructors
            val safeKeywordBlockerConfig = importedSettings.keywordBlockerConfig?.let {
                it.copy(
                    blockedKeywords = it.blockedKeywords ?: emptyList(),
                    whitelistedKeywords = it.whitelistedKeywords ?: emptyList(),
                    ignoredApps = it.ignoredApps ?: emptyList()
                )
            } ?: com.warden.app.data.models.KeywordBlocker()
            
            val safeTempIgnoredApps = importedSettings.temporaryIgnoredApps ?: emptyMap()
            val safeAvailableGeminiModels = importedSettings.availableGeminiModels ?: emptyList()
            val safeSelectedGeminiModel = importedSettings.selectedGeminiModel ?: ""

            settingsDataStore.updateData { current ->
                val mergedKeywordConfig = current.keywordBlockerConfig.copy(
                    isActive = current.keywordBlockerConfig.isActive || safeKeywordBlockerConfig.isActive,
                    blockedKeywords = (current.keywordBlockerConfig.blockedKeywords + safeKeywordBlockerConfig.blockedKeywords).distinct(),
                    whitelistedKeywords = (current.keywordBlockerConfig.whitelistedKeywords + safeKeywordBlockerConfig.whitelistedKeywords).distinct(),
                    ignoredApps = (current.keywordBlockerConfig.ignoredApps + safeKeywordBlockerConfig.ignoredApps).distinct()
                )
                importedSettings.copy(
                    keywordBlockerConfig = mergedKeywordConfig,
                    temporaryIgnoredApps = safeTempIgnoredApps,
                    availableGeminiModels = safeAvailableGeminiModels,
                    selectedGeminiModel = safeSelectedGeminiModel
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
