package com.warden.app

import android.app.Application

class Warden: Application() {
  override fun onCreate() {
    Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))
    super.onCreate()
  }
}
