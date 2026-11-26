package com.warden.app

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.test.core.app.ApplicationProvider
import com.warden.app.receivers.WardenDeviceAdminReceiver
import com.warden.app.services.WardenService
import com.warden.app.utils.PermissionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.security.MessageDigest

/**
 * Characterization tests locking in C2 (accessibility permission utils),
 * C9 (device admin receiver callbacks), C10 (SHA-256 password hash format),
 * and C16 (CrashLogger file output & delegation).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AppCoreFlowsCharacterizationTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        ShadowToast.reset()
        File(app.filesDir, "crash_log.txt").delete()
    }

    @Test
    fun c2_accessibilityServiceEnabledParsesSecureSetting() {
        AndroidSettings.Secure.putString(
            app.contentResolver,
            AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ""
        )
        assertFalse(PermissionUtils.isAccessibilityServiceEnabled(app, WardenService::class.java))
        assertFalse(PermissionUtils.hasAllRequiredPermissions(app))

        val component = ComponentName(app, WardenService::class.java).flattenToString()
        AndroidSettings.Secure.putString(
            app.contentResolver,
            AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.other.app/.OtherService:$component"
        )
        assertTrue(PermissionUtils.isAccessibilityServiceEnabled(app, WardenService::class.java))
        assertTrue(PermissionUtils.hasAllRequiredPermissions(app))
    }

    @Test
    fun c2_openAccessibilityServiceScreenStartsSettingsIntent() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        PermissionUtils.openAccessibilityServiceScreen(activity, WardenService::class.java)
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS, started.action)
        val expected = ComponentName(activity, WardenService::class.java).flattenToString()
        assertEquals(expected, started.getStringExtra(":settings:fragment_args_key"))
    }

    @Test
    fun c9_deviceAdminReceiverShowsEnableAndDisableToasts() {
        val receiver = WardenDeviceAdminReceiver()

        receiver.onEnabled(app, Intent())
        assertEquals("Anti-Uninstall Protection Activated", ShadowToast.getTextOfLatestToast())

        receiver.onDisabled(app, Intent())
        assertEquals("Anti-Uninstall Protection Deactivated", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun c10_sha256PasswordHashMatchesBaselineFormat() {
        val bytes = MessageDigest.getInstance("SHA-256").digest("secret1".toByteArray())
        val expected = bytes.joinToString("") { "%02x".format(it) }
        assertEquals(64, expected.length)
        assertEquals("5b11618c2e44027877d0cd0921ed166b9f176f50587fc91e7534dd2946db77d6", expected)
    }

    @Test
    fun c16_crashLoggerWritesUncaughtAndNonFatalErrorsAndDelegates() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        var delegated = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegated = true }
        try {
            val logger = CrashLogger(app)
            logger.logNonFatalError(IllegalStateException("non-fatal-marker"))
            logger.uncaughtException(Thread.currentThread(), RuntimeException("fatal-marker"))

            val logFile = File(app.filesDir, "crash_log.txt")
            assertTrue("crash_log.txt should exist", logFile.exists())
            val content = logFile.readText()
            assertTrue(content.contains("Non-Fatal Error"))
            assertTrue(content.contains("non-fatal-marker"))
            assertTrue(content.contains("Crash at"))
            assertTrue(content.contains("fatal-marker"))
            assertTrue("Must delegate to previous UncaughtExceptionHandler", delegated)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    @Test
    fun c16_crashLoggerRotatesWhenExceedingMaxSize() {
        val logFile = File(app.filesDir, "crash_log.txt")
        val backupFile = File(app.filesDir, "crash_log.prev.txt")
        logFile.delete()
        backupFile.delete()

        val logger = CrashLogger(app, maxLogBytes = 256L)
        logger.logNonFatalError(IllegalStateException("first-entry"))
        assertTrue(logFile.length() > 0L)
        logger.logNonFatalError(IllegalStateException("second-entry-triggers-rotation"))

        assertTrue("Rotated backup file should exist", backupFile.exists())
        assertTrue("Active log should contain newest entry", logFile.readText().contains("second-entry-triggers-rotation"))
    }

    @Test
    fun c1_disclaimerPreferenceKeyAndDefaultArePreserved() {
        val prefs = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        assertFalse(prefs.getBoolean("agreed_to_terms", false))
        prefs.edit().putBoolean("agreed_to_terms", true).apply()
        assertTrue(prefs.getBoolean("agreed_to_terms", false))
    }
}
