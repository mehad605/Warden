package com.warden.app.core.data

import com.warden.app.data.models.Settings
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for persisted Warden configuration.
 *
 * Implementations must be thread-safe, preserve the v1 JSON schema on disk,
 * and avoid blocking the main thread.
 */
interface SettingsRepository {
    /** Reactive stream of the current [Settings] snapshot. */
    val settings: StateFlow<Settings>

    /** Atomically transforms and persists [Settings]. */
    suspend fun update(transform: (Settings) -> Settings): Settings

    /** Serializes current [Settings] for user export, optionally excluding secrets. */
    suspend fun exportJson(includePassword: Boolean = true, includeApiKey: Boolean = true): String

    /**
     * Parses [json] and merges it into current [Settings] using merge-only semantics
     * for blocker activation, blocked keywords, whitelisted keywords, and ignored apps.
     *
     * @return `true` if the JSON was valid and applied, `false` otherwise.
     */
    suspend fun importJson(json: String): Boolean
}
