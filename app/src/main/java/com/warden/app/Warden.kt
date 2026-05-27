package com.warden.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class BlockWords: Application() {
  override fun onCreate() {
    DynamicColors.applyToActivitiesIfAvailable(this)
    Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))
    super.onCreate()
  }
}
