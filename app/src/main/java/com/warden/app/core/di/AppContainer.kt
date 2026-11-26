package com.warden.app.core.di

import android.content.Context
import com.warden.app.CrashLogger
import com.warden.app.core.data.JsonSettingsRepository
import com.warden.app.core.data.SettingsRepository
import com.warden.app.feature.gatekeeper.data.HttpGeminiGatekeeperClient
import com.warden.app.feature.gatekeeper.domain.GatekeeperClient
import java.io.File

/**
 * Lightweight manual dependency container owned by [com.warden.app.Warden].
 *
 * Avoids reflection/codegen DI frameworks and global mutable singletons while keeping
 * initialization strictly lazy so process startup stays minimal.
 */
class AppContainer(private val appContext: Context) {

    /** Shared on-device crash logger with bounded log file handling. */
    val crashLogger: CrashLogger by lazy { CrashLogger(appContext) }

    /** Shared single-process settings repository reading `filesDir/datastore/settings.json`. */
    val settingsRepository: SettingsRepository by lazy {
        val file = File(appContext.filesDir, "datastore/settings.json")
        JsonSettingsRepository(
            settingsFile = file,
            onError = { crashLogger.logNonFatalError(it) }
        )
    }

    /** AI Gatekeeper transport client backed by platform `HttpURLConnection`. */
    val gatekeeperClient: GatekeeperClient by lazy {
        HttpGeminiGatekeeperClient(
            onError = { crashLogger.logNonFatalError(it) }
        )
    }
}
