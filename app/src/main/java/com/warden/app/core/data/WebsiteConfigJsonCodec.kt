package com.warden.app.core.data

import com.warden.app.data.models.WebsiteBlockProfile
import com.warden.app.data.models.WebsiteBlockerConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reflection-free JSON serializer, decoder, and merge-safe backup combiner for
 * [WebsiteBlockerConfig] and its [WebsiteBlockProfile] list.
 */
internal object WebsiteConfigJsonCodec {
    private const val KEY_ENABLED = "isEnabled"
    private const val KEY_USER_DOMAINS = "userBlockedDomains"
    private const val KEY_DISABLED_BUILTINS = "disabledBuiltinDomains"
    private const val KEY_WHITELISTED_DOMAINS = "whitelistedDomains"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_PROFILE_ID = "id"
    private const val KEY_PROFILE_NAME = "name"
    private const val KEY_PROFILE_BUILTIN = "isBuiltin"
    private const val KEY_PROFILE_DOMAINS = "domains"

    /** Encodes [website] and its profile list into a backward-compatible JSON object. */
    internal fun encode(website: WebsiteBlockerConfig): JSONObject {
        val builtin = website.profiles.firstOrNull { it.isBuiltin }
        val enabledFlag = builtin?.isEnabled ?: website.isEnabled
        val userDomains = builtin?.domains ?: website.userBlockedDomains
        val disabledBuiltins = builtin?.disabledBuiltinDomains ?: website.disabledBuiltinDomains
        return JSONObject().apply {
            put(KEY_ENABLED, enabledFlag)
            put(KEY_USER_DOMAINS, JSONArray(userDomains))
            put(KEY_DISABLED_BUILTINS, JSONArray(disabledBuiltins))
            put(KEY_WHITELISTED_DOMAINS, JSONArray(website.whitelistedDomains))
            if (website.profiles.isNotEmpty()) {
                put(KEY_PROFILES, encodeProfiles(website.profiles))
            }
        }
    }

    /** Decodes a persisted website blocker JSON object, defaulting safely when absent. */
    internal fun decode(obj: JSONObject?): WebsiteBlockerConfig {
        if (obj == null) return WebsiteBlockerConfig()
        val parsedProfiles = decodeProfiles(obj.optJSONArray(KEY_PROFILES))
        return WebsiteBlockerConfig(
            isEnabled = obj.optBoolean(KEY_ENABLED, true),
            userBlockedDomains = obj.optJSONArray(KEY_USER_DOMAINS).toStringList(),
            disabledBuiltinDomains = obj.optJSONArray(KEY_DISABLED_BUILTINS).toStringList(),
            whitelistedDomains = obj.optJSONArray(KEY_WHITELISTED_DOMAINS).toStringList(),
            profiles = parsedProfiles,
        )
    }

    /** Merges [imported] into [current] using merge-only semantics so imports never weaken protection. */
    internal fun mergeImported(
        current: WebsiteBlockerConfig,
        imported: WebsiteBlockerConfig,
    ): WebsiteBlockerConfig {
        val mergedProfiles = mergeProfiles(current.effectiveProfiles(), imported.effectiveProfiles())
        val builtin = mergedProfiles.firstOrNull { it.isBuiltin }
        return WebsiteBlockerConfig(
            isEnabled = builtin?.isEnabled ?: (current.isEnabled || imported.isEnabled),
            userBlockedDomains = builtin?.domains ?: (current.userBlockedDomains + imported.userBlockedDomains).distinct(),
            disabledBuiltinDomains =
                builtin?.disabledBuiltinDomains
                    ?: current.disabledBuiltinDomains.intersect(imported.disabledBuiltinDomains.toSet()).toList(),
            whitelistedDomains = (current.whitelistedDomains + imported.whitelistedDomains).distinct(),
            profiles = mergedProfiles,
        )
    }

    private fun encodeProfiles(profiles: List<WebsiteBlockProfile>): JSONArray =
        JSONArray().apply {
            profiles.forEach { profile ->
                put(
                    JSONObject().apply {
                        put(KEY_PROFILE_ID, profile.id)
                        put(KEY_PROFILE_NAME, profile.name)
                        put(KEY_ENABLED, profile.isEnabled)
                        put(KEY_PROFILE_BUILTIN, profile.isBuiltin)
                        put(KEY_PROFILE_DOMAINS, JSONArray(profile.domains))
                        put(KEY_DISABLED_BUILTINS, JSONArray(profile.disabledBuiltinDomains))
                    },
                )
            }
        }

    private fun decodeProfiles(arr: JSONArray?): List<WebsiteBlockProfile> {
        if (arr == null) return emptyList()
        val seen = LinkedHashMap<String, WebsiteBlockProfile>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString(KEY_PROFILE_ID, "").trim()
            val name = item.optString(KEY_PROFILE_NAME, "").trim()
            if (id.isNotEmpty() && name.isNotEmpty()) {
                seen[id] =
                    WebsiteBlockProfile(
                        id = id,
                        name = name,
                        isEnabled = item.optBoolean(KEY_ENABLED, true),
                        isBuiltin = item.optBoolean(KEY_PROFILE_BUILTIN, id == WebsiteBlockProfile.BUILTIN_ADULT_ID),
                        domains = item.optJSONArray(KEY_PROFILE_DOMAINS).toStringList().distinct(),
                        disabledBuiltinDomains = item.optJSONArray(KEY_DISABLED_BUILTINS).toStringList().distinct(),
                    )
            }
        }
        return seen.values.toList()
    }

    private fun mergeProfiles(
        current: List<WebsiteBlockProfile>,
        incoming: List<WebsiteBlockProfile>,
    ): List<WebsiteBlockProfile> {
        val byId = LinkedHashMap<String, WebsiteBlockProfile>()
        current.forEach { byId[it.id] = it }
        incoming.forEach { imp ->
            val cur = byId[imp.id]
            byId[imp.id] =
                if (cur == null) {
                    imp
                } else {
                    cur.copy(
                        isEnabled = cur.isEnabled || imp.isEnabled,
                        domains = (cur.domains + imp.domains).distinct(),
                        disabledBuiltinDomains = cur.disabledBuiltinDomains.intersect(imp.disabledBuiltinDomains.toSet()).toList(),
                    )
                }
        }
        return byId.values.toList()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (i in 0 until length()) {
                if (!isNull(i)) add(optString(i))
            }
        }
    }
}
