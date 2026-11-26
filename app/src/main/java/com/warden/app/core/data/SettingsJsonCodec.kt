package com.warden.app.core.data

import com.warden.app.data.models.KeywordBlocker
import com.warden.app.data.models.Settings
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Reflection-free JSON encoder/decoder for [Settings] backed by platform `org.json`.
 *
 * Preserves exact field names and defaults from `baseline-v1` (`datastore/settings.json`)
 * so upgrades and backup files remain 100% compatible without Gson or R8 keep rules.
 */
object SettingsJsonCodec {
    private const val KEY_BLOCKER_CONFIG = "keywordBlockerConfig"
    private const val KEY_IS_ACTIVE = "isActive"
    private const val KEY_BLOCKED_KEYWORDS = "blockedKeywords"
    private const val KEY_WHITELISTED_KEYWORDS = "whitelistedKeywords"
    private const val KEY_IGNORED_APPS = "ignoredApps"
    private const val KEY_PASSWORD_HASH = "passwordHash"
    private const val KEY_GEMINI_API_KEY = "geminiApiKey"
    private const val KEY_NO_COUNT = "noCount"
    private const val KEY_TEMP_IGNORED_APPS = "temporaryIgnoredApps"
    private const val KEY_DISABLED_UNTIL = "blockerDisabledUntil"
    private const val KEY_GRACE_SECONDS = "ignoreGracePeriodSeconds"
    private const val KEY_SELECTED_MODEL = "selectedGeminiModel"
    private const val KEY_AVAILABLE_MODELS = "availableGeminiModels"
    private const val KEY_ANTI_UNINSTALL = "antiUninstallEnabled"
    private const val KEY_ADMIN_REQUESTED_AT = "deviceAdminActivationRequestedAt"

    /** Serializes [settings] to a UTF-8 JSON string matching the v1 schema. */
    fun encode(settings: Settings): String {
        val blockerJson =
            JSONObject().apply {
                put(KEY_IS_ACTIVE, settings.keywordBlockerConfig.isActive)
                put(KEY_BLOCKED_KEYWORDS, JSONArray(settings.keywordBlockerConfig.blockedKeywords))
                put(KEY_WHITELISTED_KEYWORDS, JSONArray(settings.keywordBlockerConfig.whitelistedKeywords))
                put(KEY_IGNORED_APPS, JSONArray(settings.keywordBlockerConfig.ignoredApps))
            }
        val tempIgnoredJson =
            JSONObject().apply {
                settings.temporaryIgnoredApps.forEach { (pkg, expiry) -> put(pkg, expiry) }
            }
        return JSONObject().apply {
            put(KEY_BLOCKER_CONFIG, blockerJson)
            if (settings.passwordHash != null) put(KEY_PASSWORD_HASH, settings.passwordHash)
            if (settings.geminiApiKey != null) put(KEY_GEMINI_API_KEY, settings.geminiApiKey)
            put(KEY_NO_COUNT, settings.noCount)
            put(KEY_TEMP_IGNORED_APPS, tempIgnoredJson)
            put(KEY_DISABLED_UNTIL, settings.blockerDisabledUntil)
            put(KEY_GRACE_SECONDS, settings.ignoreGracePeriodSeconds)
            put(KEY_SELECTED_MODEL, settings.selectedGeminiModel)
            put(KEY_AVAILABLE_MODELS, JSONArray(settings.availableGeminiModels))
            put(KEY_ANTI_UNINSTALL, settings.antiUninstallEnabled)
            put(KEY_ADMIN_REQUESTED_AT, settings.deviceAdminActivationRequestedAt)
        }.toString()
    }

    /**
     * Parses [rawJson] into [Settings], returning `null` if [rawJson] is not a valid JSON object.
     * Missing or null fields fall back to [Settings] defaults for forward/backward compatibility.
     */
    fun decodeOrNull(rawJson: String): Settings? {
        if (rawJson.isBlank()) return null
        val root =
            try {
                JSONObject(rawJson)
            } catch (_: JSONException) {
                return null
            }
        val blockerObj = root.optJSONObject(KEY_BLOCKER_CONFIG)
        val config =
            KeywordBlocker(
                isActive = blockerObj?.optBoolean(KEY_IS_ACTIVE, false) ?: false,
                blockedKeywords = blockerObj?.optJSONArray(KEY_BLOCKED_KEYWORDS).toStringList(),
                whitelistedKeywords = blockerObj?.optJSONArray(KEY_WHITELISTED_KEYWORDS).toStringList(),
                ignoredApps = blockerObj?.optJSONArray(KEY_IGNORED_APPS).toStringList(),
            )
        return Settings(
            keywordBlockerConfig = config,
            passwordHash = root.optNullableString(KEY_PASSWORD_HASH),
            geminiApiKey = root.optNullableString(KEY_GEMINI_API_KEY),
            noCount = root.optInt(KEY_NO_COUNT, 0),
            temporaryIgnoredApps = root.optJSONObject(KEY_TEMP_IGNORED_APPS).toLongMap(),
            blockerDisabledUntil = root.optLong(KEY_DISABLED_UNTIL, 0L),
            ignoreGracePeriodSeconds = root.optInt(KEY_GRACE_SECONDS, 2),
            selectedGeminiModel = root.optNullableString(KEY_SELECTED_MODEL) ?: "",
            availableGeminiModels = root.optJSONArray(KEY_AVAILABLE_MODELS).toStringList(),
            antiUninstallEnabled = root.optBoolean(KEY_ANTI_UNINSTALL, false),
            deviceAdminActivationRequestedAt = root.optLong(KEY_ADMIN_REQUESTED_AT, 0L),
        )
    }

    /** Prepares an export snapshot, always clearing device-local anti-uninstall state. */
    fun prepareForExport(
        current: Settings,
        includePassword: Boolean,
        includeApiKey: Boolean,
    ): Settings =
        current.copy(
            passwordHash = if (includePassword) current.passwordHash else null,
            geminiApiKey = if (includeApiKey) current.geminiApiKey else null,
            antiUninstallEnabled = false,
            deviceAdminActivationRequestedAt = 0L,
        )

    /**
     * Merges [imported] into [current] using merge-only semantics for blocker state and lists
     * so importing a backup cannot weaken existing keyword protection.
     */
    fun mergeImported(
        current: Settings,
        imported: Settings,
    ): Settings {
        val mergedConfig =
            current.keywordBlockerConfig.copy(
                isActive = current.keywordBlockerConfig.isActive || imported.keywordBlockerConfig.isActive,
                blockedKeywords =
                    (
                        current.keywordBlockerConfig.blockedKeywords + imported.keywordBlockerConfig.blockedKeywords
                    ).distinct(),
                whitelistedKeywords =
                    (
                        current.keywordBlockerConfig.whitelistedKeywords + imported.keywordBlockerConfig.whitelistedKeywords
                    ).distinct(),
                ignoredApps =
                    (
                        current.keywordBlockerConfig.ignoredApps + imported.keywordBlockerConfig.ignoredApps
                    ).distinct(),
            )
        return imported.copy(keywordBlockerConfig = mergedConfig)
    }

    private fun JSONObject.optNullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (i in 0 until length()) {
                if (!isNull(i)) add(optString(i))
            }
        }
    }

    private fun JSONObject?.toLongMap(): Map<String, Long> {
        if (this == null) return emptyMap()
        return buildMap(length()) {
            val iter = keys()
            while (iter.hasNext()) {
                val key = iter.next()
                if (!isNull(key)) put(key, optLong(key, 0L))
            }
        }
    }
}
