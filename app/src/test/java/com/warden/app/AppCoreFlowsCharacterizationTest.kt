package com.warden.app

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.test.core.app.ApplicationProvider
import com.warden.app.core.platform.AndroidAccessibilityPermissionChecker
import com.warden.app.feature.blocker.domain.IgnoredAppsFormatter
import com.warden.app.feature.blocker.domain.KeywordNormalizer
import com.warden.app.feature.security.domain.PasswordPolicy
import com.warden.app.receivers.WardenDeviceAdminReceiver
import com.warden.app.services.WardenService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

/**
 * Characterization tests locking in C1 (disclaimer prefs), C2 (accessibility checker),
 * C4/C5 (keyword normalization & risky check), C9 (device admin receiver),
 * C10 (password strength & SHA-256 hash format), C14 (ignored apps countdown/pruning),
 * and C16 (CrashLogger output, rotation, and delegation).
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
        File(app.filesDir, "crash_log.prev.txt").delete()
    }

    @Test
    fun c2_accessibilityServiceEnabledParsesSecureSetting() {
        val checker = AndroidAccessibilityPermissionChecker(app)
        AndroidSettings.Secure.putString(
            app.contentResolver,
            AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ""
        )
        assertFalse(checker.isServiceEnabled(WardenService::class.java))

        val component = ComponentName(app, WardenService::class.java).flattenToString()
        AndroidSettings.Secure.putString(
            app.contentResolver,
            AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.other.app/.OtherService:$component"
        )
        assertTrue(checker.isServiceEnabled(WardenService::class.java))
    }

    @Test
    fun c2_openAccessibilityServiceScreenStartsSettingsIntent() {
        val checker = AndroidAccessibilityPermissionChecker(app)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        checker.openAccessibilitySettings(activity, WardenService::class.java)
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS, started.action)
        val expected = ComponentName(activity, WardenService::class.java).flattenToString()
        assertEquals(expected, started.getStringExtra(":settings:fragment_args_key"))
    }

    @Test
    fun c4_c5_keywordNormalizerStripsUrlsAndFlagsRiskySexWord() {
        assertNull(KeywordNormalizer.normalize("   "))

        val urlNorm = KeywordNormalizer.normalize("https://www.Reddit.com/r/all")
        assertNotNull(urlNorm)
        assertEquals("reddit.com/r/all", urlNorm!!.normalized)
        assertTrue(urlNorm.wasUrl)
        assertFalse(urlNorm.isRisky)

        val risky = KeywordNormalizer.normalize("  SeX ")
        assertNotNull(risky)
        assertEquals("sex", risky!!.normalized)
        assertFalse(risky.wasUrl)
        assertTrue(risky.isRisky)
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
    fun c10_passwordPolicyStrengthAndSha256BaselineCompatibility() {
        assertFalse(PasswordPolicy.isStrong("abc12"))
        assertFalse(PasswordPolicy.isStrong("abcdef"))
        assertFalse(PasswordPolicy.isStrong("123456"))
        assertTrue(PasswordPolicy.isStrong("secret1"))

        val hash = PasswordPolicy.hashSha256("secret1")
        assertEquals("5b11618c2e44027877d0cd0921ed166b9f176f50587fc91e7534dd2946db77d6", hash)
        assertTrue(PasswordPolicy.verify("secret1", hash))
        assertFalse(PasswordPolicy.verify("wrong1", hash))
        assertTrue(PasswordPolicy.verify("anything", null))
    }

    @Test
    fun c14_ignoredAppsFormatterBuildsRowsPrunesExpiredAndFormatsCountdown() {
        val now = 100_000L
        val temp = mapOf(
            "com.active.app" to now + 125_000L,
            "com.expired.app" to now - 5_000L
        )
        val rows = IgnoredAppsFormatter.buildActiveRows(listOf("com.perm.app"), temp, now)
        assertEquals(2, rows.size)
        assertEquals("com.perm.app", rows[0].packageName)
        assertFalse(rows[0].isTemporary)
        assertEquals("com.active.app", rows[1].packageName)
        assertTrue(rows[1].isTemporary)
        assertEquals("2m 05s", IgnoredAppsFormatter.formatRemaining(now + 125_000L, now))
        assertEquals("0m 00s", IgnoredAppsFormatter.formatRemaining(now - 1L, now))

        val pruned = IgnoredAppsFormatter.pruneExpired(temp, now)
        assertNotNull(pruned)
        assertEquals(setOf("com.active.app"), pruned!!.keys)
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
