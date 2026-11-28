package com.warden.app.core.di

import android.content.Context
import com.warden.app.core.data.JsonSettingsRepository
import com.warden.app.core.data.SettingsRepository
import com.warden.app.core.platform.AccessibilityPermissionChecker
import com.warden.app.core.platform.AndroidAccessibilityPermissionChecker
import com.warden.app.core.platform.Clock
import com.warden.app.core.platform.CrashLogger
import com.warden.app.core.platform.DeviceAdminController
import com.warden.app.core.platform.InstalledAppsProvider
import com.warden.app.core.platform.SystemClockProvider
import com.warden.app.feature.gatekeeper.data.HttpGeminiGatekeeperClient
import com.warden.app.feature.gatekeeper.domain.GatekeeperClient
import com.warden.app.feature.websites.data.PornDomainRepository
import java.io.File

/**
 * Implemented by the [android.app.Application] subclass to expose the lazy [AppContainer]
 * without creating circular dependencies back to the root `com.warden.app` package.
 */
interface AppContainerProvider {
    /** Process-scoped dependency container. */
    val container: AppContainer
}

/**
 * Lightweight manual dependency container owned by `Warden`.
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
            onError = { crashLogger.logNonFatalError(it) },
        )
    }

    /** AI Gatekeeper transport client backed by platform `HttpURLConnection`. */
    val gatekeeperClient: GatekeeperClient by lazy {
        HttpGeminiGatekeeperClient(
            onError = { crashLogger.logNonFatalError(it) },
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

    /** Lazy repository loading the shipped MIT-licensed adult domain blocklist. */
    val pornDomainRepository: PornDomainRepository by lazy {
        PornDomainRepository(appContext)
    }
}
