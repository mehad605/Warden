package com.warden.app.core.di

import android.content.Context
import com.warden.app.CrashLogger
import com.warden.app.core.data.JsonSettingsRepository
import com.warden.app.core.data.SettingsRepository
import com.warden.app.core.platform.AccessibilityPermissionChecker
import com.warden.app.core.platform.AndroidAccessibilityPermissionChecker
import com.warden.app.core.platform.Clock
import com.warden.app.core.platform.DeviceAdminController
import com.warden.app.core.platform.InstalledAppsProvider
import com.warden.app.core.platform.SystemClockProvider
import com.warden.app.feature.blocker.ui.BlockerViewModel
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

    /** Shared wall-clock and monotonic uptime source. */
    val clock: Clock by lazy { SystemClockProvider() }

    /** Shared on-device crash logger with bounded log file rotation. */
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

    /** Checks and opens Accessibility Service system settings. */
    val accessibilityChecker: AccessibilityPermissionChecker by lazy {
        AndroidAccessibilityPermissionChecker(appContext)
    }

    /** Manages Device Administrator activation/deactivation state. */
    val deviceAdminController: DeviceAdminController by lazy {
        DeviceAdminController(appContext)
    }

    /** Off-main-thread PackageManager provider with a bounded icon cache. */
    val installedAppsProvider: InstalledAppsProvider by lazy {
        InstalledAppsProvider(appContext)
    }

    /** Creates a [BlockerViewModel.Factory] wired with this container's dependencies. */
    fun blockerViewModelFactory(): BlockerViewModel.Factory = BlockerViewModel.Factory(
        settingsRepository = settingsRepository,
        gatekeeperClient = gatekeeperClient,
        clock = clock
    )
}
