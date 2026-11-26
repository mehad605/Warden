package com.warden.app

import com.warden.app.core.data.JsonSettingsRepository
import com.warden.app.core.data.SettingsJsonCodec
import com.warden.app.data.models.KeywordBlocker
import com.warden.app.data.models.Settings
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Characterization & upgrade-compatibility tests locking in C15 (export/import JSON contract)
 * and C17 (baseline-v1 settings.json upgrade schema compatibility).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SettingsPersistenceCharacterizationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun c17_roundTripsFullSettingsJsonSchema() {
        val original =
            Settings(
                keywordBlockerConfig =
                    KeywordBlocker(
                        isActive = true,
                        blockedKeywords = listOf("shorts", "reels"),
                        whitelistedKeywords = listOf("basketball shorts"),
                        ignoredApps = listOf("com.notes.app"),
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
                deviceAdminActivationRequestedAt = 1_700_000_000_000L,
            )

        val encoded = SettingsJsonCodec.encode(original)
        val restored = SettingsJsonCodec.decodeOrNull(encoded)

        assertEquals(original, restored)
    }

    @Test
    fun c17_corruptedJsonFallsBackToDefaultsOnLoad() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val file =
                File(tempFolder.root, "datastore/settings.json").apply {
                    parentFile?.mkdirs()
                    writeText("{broken json")
                }
            val repo = JsonSettingsRepository(file, ioDispatcher = dispatcher)
            assertEquals(Settings(), repo.awaitLoaded())
        }

    @Test
    fun c17_readsLegacyBaselineV1SettingsJsonFromDiskIncludingMissingOptionalFields() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val legacyJson =
                """
                {
                  "keywordBlockerConfig": {
                    "isActive": true,
                    "blockedKeywords": ["news", "shorts"]
                  },
                  "passwordHash": "5b11618c2e44027877d0cd0921ed166b9f176f50587fc91e7534dd2946db77d6",
                  "noCount": 2,
                  "ignoreGracePeriodSeconds": 3
                }
                """.trimIndent()
            val file =
                File(tempFolder.root, "datastore/settings.json").apply {
                    parentFile?.mkdirs()
                    writeText(legacyJson)
                }

            val repo = JsonSettingsRepository(file, ioDispatcher = dispatcher)
            val loaded = repo.awaitLoaded()

            assertTrue(loaded.keywordBlockerConfig.isActive)
            assertEquals(listOf("news", "shorts"), loaded.keywordBlockerConfig.blockedKeywords)
            assertEquals(emptyList<String>(), loaded.keywordBlockerConfig.whitelistedKeywords)
            assertEquals(emptyList<String>(), loaded.keywordBlockerConfig.ignoredApps)
            assertEquals("5b11618c2e44027877d0cd0921ed166b9f176f50587fc91e7534dd2946db77d6", loaded.passwordHash)
            assertNull(loaded.geminiApiKey)
            assertEquals(2, loaded.noCount)
            assertEquals(3, loaded.ignoreGracePeriodSeconds)
            assertFalse(loaded.antiUninstallEnabled)
        }

    @Test
    fun c15_exportOmitsSecretsWhenRequestedAndClearsAntiUninstall() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val file = File(tempFolder.root, "datastore/settings.json")
            val repo = JsonSettingsRepository(file, ioDispatcher = dispatcher)
            repo.update {
                it.copy(
                    passwordHash = "deadbeef",
                    geminiApiKey = "secret-key",
                    antiUninstallEnabled = true,
                    deviceAdminActivationRequestedAt = 12_345L,
                )
            }

            val exportedWithoutSecrets = repo.exportJson(includePassword = false, includeApiKey = false)
            val decodedNoSecrets = SettingsJsonCodec.decodeOrNull(exportedWithoutSecrets)
            assertNotNull(decodedNoSecrets)
            assertNull(decodedNoSecrets!!.passwordHash)
            assertNull(decodedNoSecrets.geminiApiKey)
            assertFalse(decodedNoSecrets.antiUninstallEnabled)
            assertEquals(0L, decodedNoSecrets.deviceAdminActivationRequestedAt)

            val exportedWithSecrets = repo.exportJson(includePassword = true, includeApiKey = true)
            val decodedWithSecrets = SettingsJsonCodec.decodeOrNull(exportedWithSecrets)
            assertEquals("deadbeef", decodedWithSecrets!!.passwordHash)
            assertEquals("secret-key", decodedWithSecrets.geminiApiKey)
            assertFalse(decodedWithSecrets.antiUninstallEnabled)
        }

    @Test
    fun c15_importIsMergeOnlyForBlockerStateKeywordsWhitelistAndIgnoredApps() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val file = File(tempFolder.root, "datastore/settings.json")
            val repo = JsonSettingsRepository(file, ioDispatcher = dispatcher)
            repo.update {
                it.copy(
                    keywordBlockerConfig =
                        KeywordBlocker(
                            isActive = true,
                            blockedKeywords = listOf("shorts", "reels"),
                            whitelistedKeywords = listOf("gym shorts"),
                            ignoredApps = listOf("com.app.a"),
                        ),
                )
            }

            val incomingJson =
                SettingsJsonCodec.encode(
                    Settings(
                        keywordBlockerConfig =
                            KeywordBlocker(
                                isActive = false,
                                blockedKeywords = listOf("reels", "news"),
                                whitelistedKeywords = listOf("good news"),
                                ignoredApps = listOf("com.app.a", "com.app.b"),
                            ),
                        ignoreGracePeriodSeconds = 5,
                        noCount = 4,
                    ),
                )

            assertTrue(repo.importJson(incomingJson))
            assertFalse(repo.importJson("not-json"))

            val after = repo.settings.value
            assertTrue("Active state must remain true (OR merge)", after.keywordBlockerConfig.isActive)
            assertEquals(listOf("shorts", "reels", "news"), after.keywordBlockerConfig.blockedKeywords)
            assertEquals(listOf("gym shorts", "good news"), after.keywordBlockerConfig.whitelistedKeywords)
            assertEquals(listOf("com.app.a", "com.app.b"), after.keywordBlockerConfig.ignoredApps)
            assertEquals(5, after.ignoreGracePeriodSeconds)
            assertEquals(4, after.noCount)
        }
}
