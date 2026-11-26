package com.warden.app

import android.app.Application
import com.warden.app.core.di.AppContainer

/**
 * Application entry point owning the lazy [AppContainer] and default crash handler.
 */
class Warden : Application() {

    /** Process-scoped dependency container initialized lazily on first access. */
    val container: AppContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(container.crashLogger)
    }
}
