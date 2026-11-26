package com.warden.app.services

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.warden.app.Warden
import com.warden.app.core.data.JsonSettingsRepository
import com.warden.app.core.data.SettingsRepository
import java.io.File

/**
 * Base [AccessibilityService] exposing shared settings access and global navigation helpers.
 */
open class BaseBlockingService : AccessibilityService() {

    /** Shared settings repository resolved from [Warden.container] (or standalone fallback in tests). */
    val settingsRepository: SettingsRepository by lazy {
        val app = application as? Warden
        app?.container?.settingsRepository
            ?: JsonSettingsRepository(File(filesDir, "datastore/settings.json"))
    }

    var lastBackPressTimeStamp: Long = SystemClock.uptimeMillis()

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onInterrupt() {
    }

    fun isDelayOver(lastTimestamp: Long, delay: Int): Boolean {
        val currentTime = SystemClock.uptimeMillis().toFloat()
        return currentTime - lastTimestamp > delay
    }

    fun pressHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        lastBackPressTimeStamp = SystemClock.uptimeMillis()
    }

    fun pressBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        lastBackPressTimeStamp = SystemClock.uptimeMillis()
    }
}
