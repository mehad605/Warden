package com.warden.app

import android.app.Application
import android.os.StrictMode
import com.warden.app.core.di.AppContainer
import com.warden.app.core.di.AppContainerProvider

/**
 * Application entry point owning the lazy [AppContainer] and default crash handler.
 *
 * Keeps `onCreate()` minimal (zero synchronous disk or network I/O) and enables [StrictMode]
 * in debug builds to surface main-thread I/O or resource leaks immediately.
 */
class Warden : Application(), AppContainerProvider {
    /** Process-scoped dependency container initialized lazily on first access. */
    override val container: AppContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            enableDebugStrictMode()
        }
        Thread.setDefaultUncaughtExceptionHandler(container.crashLogger)
    }

    private fun enableDebugStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }
}
