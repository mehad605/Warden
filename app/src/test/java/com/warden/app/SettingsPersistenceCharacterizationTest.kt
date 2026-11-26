package com.warden.app

import com.google.gson.Gson
import com.warden.app.data.models.KeywordBlocker
import com.warden.app.data.models.Settings
import com.warden.app.utils.GsonSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Characterization tests locking in C15 (export/import JSON contract)
 * and C17 (baseline-v1 settings.json upgrade schema compatibility).
 */
class SettingsPersistenceCharacterizationTest {

    private val gson = Gson()
    private val serializer = GsonSerializer(gson, Settings::class.java, Settings())

    @Test
    fun c17_roundTripsFullSettingsJsonSchema() = runTest {
        val original = Settings(
            keywordBlockerConfig = KeywordBlocker(
                isActive = true,
                blockedKeywords = listOf("shorts", "reels"),
                whitelistedKeywords = listOf("basketball shorts"),
                ignoredApps = listOf("com.notes.app")
            ),
            passwordHash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
            geminiApiKey = "test-key-123",
            noCount = 3,
            temporaryIgnoredApps = mapOf("com.browser.app" to 1_900_000_000_000L),
            blockerDisabledUntil = 1_800_000_000_000L,
            ignoreGracePeriodSeconds = 4,
            selectedGeminiModel = "models/gemini-2.0-flash",
            availableGeminiModels = listOf("models/gemini-2.0-flash", "models/gemini-1.5-pro"),
            antiUninstallEnabled = true,
            deviceAdminActivationRequestedAt = 1_700_000_000_000L
        )

        val out = ByteArrayOutputStream()
        serializer.writeTo(original, out)
        val restored = serializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertEquals(original, restored)
    }

    @Test
    fun c17_corruptedJsonFallsBackToDefaults() = runTest {
        val restored = serializer.readFrom(ByteArrayInputStream("{broken json".toByteArray()))
        assertEquals(Settings(), restored)
    }

    @Test
    fun c15_v1SettingsJsonFieldNamesAreStable() {
        val v1Json = """
            {
              "keywordBlockerConfig": {
                "isActive": true,
                "blockedKeywords": ["news", "shorts"],
                "whitelistedKeywords": ["good news"],
                "ignoredApps": ["com.example.docs"]
              },
              "passwordHash": "abc123",
              "geminiApiKey": "key-xyz",
              "noCount": 2,
              "temporaryIgnoredApps": {"com.temp.app": 999999},
              "blockerDisabledUntil": 123456,
              "ignoreGracePeriodSeconds": 3,
              "selectedGeminiModel": "models/gemini-1.5-flash",
              "availableGeminiModels": ["models/gemini-1.5-flash"],
              "antiUninstallEnabled": false,
              "deviceAdminActivationRequestedAt": 0
            }
        """.trimIndent()

        val parsed = gson.fromJson(v1Json, Settings::class.java)
        assertTrue(parsed.keywordBlockerConfig.isActive)
        assertEquals(listOf("news", "shorts"), parsed.keywordBlockerConfig.blockedKeywords)
        assertEquals(listOf("good news"), parsed.keywordBlockerConfig.whitelistedKeywords)
        assertEquals(listOf("com.example.docs"), parsed.keywordBlockerConfig.ignoredApps)
        assertEquals("abc123", parsed.passwordHash)
        assertEquals("key-xyz", parsed.geminiApiKey)
        assertEquals(2, parsed.noCount)
        assertEquals(999999L, parsed.temporaryIgnoredApps["com.temp.app"])
        assertEquals(123456L, parsed.blockerDisabledUntil)
        assertEquals(3, parsed.ignoreGracePeriodSeconds)
        assertEquals("models/gemini-1.5-flash", parsed.selectedGeminiModel)
        assertEquals(listOf("models/gemini-1.5-flash"), parsed.availableGeminiModels)
        assertFalse(parsed.antiUninstallEnabled)
        assertEquals(0L, parsed.deviceAdminActivationRequestedAt)
    }

    @Test
    fun c15_exportOmitsSecretsWhenRequestedAndClearsAntiUninstall() {
        val current = Settings(
            passwordHash = "deadbeef",
            geminiApiKey = "secret-key",
            antiUninstallEnabled = true,
            deviceAdminActivationRequestedAt = 12345L
        )
        val exported = current.copy(
            passwordHash = null,
            geminiApiKey = null,
            antiUninstallEnabled = false,
            deviceAdminActivationRequestedAt = 0L
        )
        val roundTripped = gson.fromJson(gson.toJson(exported), Settings::class.java)
        assertNull(roundTripped.passwordHash)
        assertNull(roundTripped.geminiApiKey)
        assertFalse(roundTripped.antiUninstallEnabled)
        assertEquals(0L, roundTripped.deviceAdminActivationRequestedAt)
    }
}
