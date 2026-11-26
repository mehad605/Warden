package com.warden.app.core.platform

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils

/**
 * Abstraction for checking and opening Android's Accessibility Service settings screen.
 */
interface AccessibilityPermissionChecker {
    /** Returns `true` if [serviceClass] is enabled in secure accessibility settings. */
    fun isServiceEnabled(serviceClass: Class<*>): Boolean

    /** Opens Accessibility Settings targeting [serviceClass], falling back to the root screen. */
    fun openAccessibilitySettings(context: Context, serviceClass: Class<*>)
}

/**
 * Android platform implementation of [AccessibilityPermissionChecker].
 */
class AndroidAccessibilityPermissionChecker(
    private val appContext: Context
) : AccessibilityPermissionChecker {

    override fun isServiceEnabled(serviceClass: Class<*>): Boolean {
        val expected = ComponentName(appContext, serviceClass)
        val enabledSetting = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledSetting) }
        while (splitter.hasNext()) {
            val component = ComponentName.unflattenFromString(splitter.next())
            if (component != null && component == expected) {
                return true
            }
        }
        return false
    }

    override fun openAccessibilitySettings(context: Context, serviceClass: Class<*>) {
        val componentKey = ComponentName(context, serviceClass).flattenToString()
        val targetedIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(EXTRA_FRAGMENT_ARGS_KEY, componentKey)
            putExtra(
                EXTRA_SHOW_FRAGMENT_ARGS,
                Bundle().apply { putString(EXTRA_FRAGMENT_ARGS_KEY, componentKey) }
            )
        }
        try {
            context.startActivity(targetedIntent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: SecurityException) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    companion object {
        private const val EXTRA_FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
        private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    }
}
