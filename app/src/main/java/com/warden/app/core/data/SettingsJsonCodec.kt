package com.warden.app.core.data

import com.warden.app.data.models.BlockedEntry
import com.warden.app.data.models.KeywordBlocker
import com.warden.app.data.models.MatchMode
import com.warden.app.data.models.Settings
import com.warden.app.data.models.WebsiteBlockerConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Reflection-free JSON encoder/decoder for [Settings] backed by platform `org.json`.
 *
 * Preserves exact field names and defaults from `baseline-v1` (`datastore/settings.json`)
 * and automatically migrates legacy flat `blockedKeywords` into `MatchMode.EXACT` [BlockedEntry]
 * items without changing behavior or breaking backups.
 */
object SettingsJsonCodec {
    private const val KEY_BLOCKER_CONFIG = "keywordBlockerConfig"
    private const val KEY_WEBSITE_CONFIG = "websiteBlockerConfig"
    private const val KEY_IS_ACTIVE = "isActive"
    private const val KEY_BLOCKED_KEYWORDS = "blockedKeywords"
    private const val KEY_BLOCKED_ENTRIES = "blockedEntries"
    private const val KEY_WHITELISTED_KEYWORDS = "whitelistedKeywords"
    private const val KEY_IGNORED_APPS = "ignoredApps"
    private const val KEY_TERM = "term"
    private const val KEY_MODE = "mode"
    private const val KEY_ADDED_AT = "addedAt"
    private const val KEY_WEBSITE_ENABLED = "isEnabled"
    private const val KEY_USER_BLOCKED_DOMAINS = "userBlockedDomains"
    private const val KEY_DISABLED_BUILTIN_DOMAINS = "disabledBuiltinDomains"
    private const val KEY_WHITELISTED_DOMAINS = "whitelistedDomains"
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

    /** Serializes [settings] to a UTF-8 JSON string compatible with v1 and extended schemas. */
    fun encode(settings: Settings): String {
        val entries = settings.keywordBlockerConfig.effectiveBlockedEntries()
        val entriesJson =
            JSONArray().apply {
                entries.forEach { entry ->
                    put(
                        JSONObject().apply {
                            put(KEY_TERM, entry.term)
                            put(KEY_MODE, entry.mode.wireValue)
                            put(KEY_ADDED_AT, entry.addedAt)
                        },
                    )
                }
            }
        val blockerJson =
            JSONObject().apply {
                put(KEY_IS_ACTIVE, settings.keywordBlockerConfig.isActive)
                put(KEY_BLOCKED_KEYWORDS, JSONArray(entries.map { it.term }))
                put(KEY_BLOCKED_ENTRIES, entriesJson)
                put(KEY_WHITELISTED_KEYWORDS, JSONArray(settings.keywordBlockerConfig.whitelistedKeywords))
                put(KEY_IGNORED_APPS, JSONArray(settings.keywordBlockerConfig.ignoredApps))
            }
        val website = settings.websiteBlockerConfig
        val websiteJson =
            JSONObject().apply {
                put(KEY_WEBSITE_ENABLED, website.isEnabled)
                put(KEY_USER_BLOCKED_DOMAINS, JSONArray(website.userBlockedDomains))
                put(KEY_DISABLED_BUILTIN_DOMAINS, JSONArray(website.disabledBuiltinDomains))
                put(KEY_WHITELISTED_DOMAINS, JSONArray(website.whitelistedDomains))
            }
        val tempIgnoredJson =
            JSONObject().apply {
                settings.temporaryIgnoredApps.forEach { (pkg, expiry) -> put(pkg, expiry) }
            }
        return JSONObject().apply {
            put(KEY_BLOCKER_CONFIG, blockerJson)
            put(KEY_WEBSITE_CONFIG, websiteJson)
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
     * Missing or null fields fall back to [Settings] defaults; legacy `blockedKeywords` migrate to `exact`.
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
        val config = decodeKeywordBlocker(blockerObj)
        val websiteConfig = decodeWebsiteConfig(root.optJSONObject(KEY_WEBSITE_CONFIG))
        return Settings(
            keywordBlockerConfig = config,
            websiteBlockerConfig = websiteConfig,
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
     * so importing a backup cannot weaken existing protection.
     */
    fun mergeImported(
        current: Settings,
        imported: Settings,
    ): Settings {
        val mergedEntries = mergeEntries(current.keywordBlockerConfig.effectiveBlockedEntries(), imported.keywordBlockerConfig.effectiveBlockedEntries())
        val mergedConfig =
            current.keywordBlockerConfig.copy(
                isActive = current.keywordBlockerConfig.isActive || imported.keywordBlockerConfig.isActive,
                blockedKeywords = mergedEntries.map { it.term },
                blockedEntries = mergedEntries,
                whitelistedKeywords = (current.keywordBlockerConfig.whitelistedKeywords + imported.keywordBlockerConfig.whitelistedKeywords).distinct(),
                ignoredApps = (current.keywordBlockerConfig.ignoredApps + imported.keywordBlockerConfig.ignoredApps).distinct(),
            )
        val curWeb = current.websiteBlockerConfig
        val impWeb = imported.websiteBlockerConfig
        val mergedWebsite =
            WebsiteBlockerConfig(
                isEnabled = curWeb.isEnabled || impWeb.isEnabled,
                userBlockedDomains = (curWeb.userBlockedDomains + impWeb.userBlockedDomains).distinct(),
                disabledBuiltinDomains = curWeb.disabledBuiltinDomains.intersect(impWeb.disabledBuiltinDomains.toSet()).toList(),
                whitelistedDomains = (curWeb.whitelistedDomains + impWeb.whitelistedDomains).distinct(),
            )
        return imported.copy(keywordBlockerConfig = mergedConfig, websiteBlockerConfig = mergedWebsite)
    }

    private fun decodeKeywordBlocker(blockerObj: JSONObject?): KeywordBlocker {
        val legacyTerms = blockerObj?.optJSONArray(KEY_BLOCKED_KEYWORDS).toStringList()
        val parsedEntries = blockerObj?.optJSONArray(KEY_BLOCKED_ENTRIES).toBlockedEntries()
        val entries =
            if (parsedEntries != null) {
                parsedEntries
            } else {
                legacyTerms.distinct().map { BlockedEntry(term = it, mode = MatchMode.EXACT, addedAt = 0L) }
            }
        return KeywordBlocker(
            isActive = blockerObj?.optBoolean(KEY_IS_ACTIVE, false) ?: false,
            blockedKeywords = entries.map { it.term },
            whitelistedKeywords = blockerObj?.optJSONArray(KEY_WHITELISTED_KEYWORDS).toStringList(),
            ignoredApps = blockerObj?.optJSONArray(KEY_IGNORED_APPS).toStringList(),
            blockedEntries = entries,
        )
    }

    private fun decodeWebsiteConfig(obj: JSONObject?): WebsiteBlockerConfig {
        if (obj == null) return WebsiteBlockerConfig()
        return WebsiteBlockerConfig(
            isEnabled = obj.optBoolean(KEY_WEBSITE_ENABLED, true),
            userBlockedDomains = obj.optJSONArray(KEY_USER_BLOCKED_DOMAINS).toStringList(),
            disabledBuiltinDomains = obj.optJSONArray(KEY_DISABLED_BUILTIN_DOMAINS).toStringList(),
            whitelistedDomains = obj.optJSONArray(KEY_WHITELISTED_DOMAINS).toStringList(),
        )
    }

    private fun mergeEntries(
        current: List<BlockedEntry>,
        incoming: List<BlockedEntry>,
    ): List<BlockedEntry> {
        val byTerm = LinkedHashMap<String, BlockedEntry>()
        current.forEach { byTerm[it.term] = it }
        incoming.forEach { entry ->
            val existing = byTerm[entry.term]
            byTerm[entry.term] =
                if (existing == null) {
                    entry
                } else {
                    val mode = if (existing.mode == MatchMode.CONTAINS || entry.mode == MatchMode.CONTAINS) MatchMode.CONTAINS else MatchMode.EXACT
                    existing.copy(mode = mode, addedAt = maxOf(existing.addedAt, entry.addedAt))
                }
        }
        return byTerm.values.toList()
    }

    private fun JSONArray?.toBlockedEntries(): List<BlockedEntry>? {
        if (this == null) return null
        val seen = LinkedHashMap<String, BlockedEntry>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val term = item.optString(KEY_TERM, "").trim()
            if (term.isEmpty()) continue
            val mode = MatchMode.fromWire(item.optString(KEY_MODE, MatchMode.EXACT.wireValue))
            val addedAt = item.optLong(KEY_ADDED_AT, 0L)
            seen[term] = BlockedEntry(term = term, mode = mode, addedAt = addedAt)
        }
        return seen.values.toList()
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
