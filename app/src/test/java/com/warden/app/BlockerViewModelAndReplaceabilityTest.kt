package com.warden.app

import com.warden.app.core.data.SettingsJsonCodec
import com.warden.app.core.data.SettingsRepository
import com.warden.app.core.platform.Clock
import com.warden.app.data.models.KeywordBlocker
import com.warden.app.data.models.Settings
import com.warden.app.feature.blocker.ui.BlockerAction
import com.warden.app.feature.blocker.ui.BlockerViewModel
import com.warden.app.feature.gatekeeper.domain.EncodedImage
import com.warden.app.feature.gatekeeper.domain.GatekeeperClient
import com.warden.app.feature.gatekeeper.domain.GatekeeperRequest
import com.warden.app.feature.gatekeeper.domain.GatekeeperVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies BlockerViewModel parity across C3, C4, C5, C8, C9, C10, C11, C13, C14, C15 AND serves as
 * the S5.3 Replaceability Proof by swapping BOTH [SettingsRepository] (in-memory backend) and
 * [GatekeeperClient] (offline deterministic policy engine) behind their public interfaces without
 * modifying any production code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class BlockerViewModelAndReplaceabilityTest {
    /** Alternative #1 (S5.3): Zero-disk in-memory [SettingsRepository] replacement. */
    private class InMemorySettingsRepository(initial: Settings = Settings()) : SettingsRepository {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<Settings> = state.asStateFlow()

        override suspend fun update(transform: (Settings) -> Settings): Settings {
            val next = transform(state.value)
            state.value = next
            return next
        }

        override suspend fun exportJson(
            includePassword: Boolean,
            includeApiKey: Boolean,
        ): String = SettingsJsonCodec.encode(SettingsJsonCodec.prepareForExport(state.value, includePassword, includeApiKey))

        override suspend fun importJson(json: String): Boolean {
            val parsed = SettingsJsonCodec.decodeOrNull(json) ?: return false
            state.value = SettingsJsonCodec.mergeImported(state.value, parsed)
            return true
        }
    }

    /** Alternative #2 (S5.3): Zero-network deterministic [GatekeeperClient] replacement. */
    private class LocalPolicyGatekeeperClient : GatekeeperClient {
        override suspend fun fetchModels(apiKey: String): List<String> =
            if (apiKey.isBlank()) emptyList() else listOf("models/local-strict-v1")

        override suspend fun evaluate(
            apiKey: String,
            modelName: String,
            request: GatekeeperRequest,
            image: EncodedImage?,
        ): GatekeeperVerdict =
            when (request) {
                is GatekeeperRequest.RemoveFromIgnore ->
                    GatekeeperVerdict(true, 0, "removal", "Removed from ignore list.")
                is GatekeeperRequest.PauseBlocker -> {
                    val hasProof = image != null || request.userPrompt.contains("http") || request.userPrompt.contains("@")
                    GatekeeperVerdict(hasProof, if (hasProof) 10 else 0, "policy", "Evaluated locally.")
                }
                else -> GatekeeperVerdict(true, 15, "approved", "Granted 15 minutes.")
            }
    }

    private class MutableClock(var nowMs: Long = 1_000_000L) : Clock {
        override fun currentTimeMillis(): Long = nowMs

        override fun uptimeMillis(): Long = nowMs
    }

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var clock: MutableClock
    private lateinit var repository: InMemorySettingsRepository
    private lateinit var gatekeeper: LocalPolicyGatekeeperClient
    private lateinit var viewModel: BlockerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        clock = MutableClock()
        repository = InMemorySettingsRepository()
        gatekeeper = LocalPolicyGatekeeperClient()
        viewModel = BlockerViewModel(repository, gatekeeper, clock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun c3_c4_c5_c8_managesToggleKeywordsWhitelistAndGracePeriod() =
        runTest(testDispatcher) {
            viewModel.dispatch(BlockerAction.SetBlockerActive(true))
            viewModel.dispatch(BlockerAction.AddBlockedKeyword("  Shorts "))
            viewModel.dispatch(BlockerAction.AddBlockedKeyword("shorts"))
            viewModel.dispatch(BlockerAction.AddWhitelistedKeyword("Basketball Shorts"))
            viewModel.dispatch(BlockerAction.SetGracePeriodSeconds(4))
            advanceUntilIdle()

            val state = viewModel.settings.value
            assertTrue(state.keywordBlockerConfig.isActive)
            assertEquals(listOf("shorts"), state.keywordBlockerConfig.blockedKeywords)
            assertEquals(listOf("basketball shorts"), state.keywordBlockerConfig.whitelistedKeywords)
            assertEquals(4, state.ignoreGracePeriodSeconds)

            viewModel.dispatch(BlockerAction.RemoveBlockedKeyword("SHORTS"))
            viewModel.dispatch(BlockerAction.RemoveWhitelistedKeyword("basketball shorts"))
            advanceUntilIdle()

            assertTrue(viewModel.settings.value.keywordBlockerConfig.blockedKeywords.isEmpty())
            assertTrue(viewModel.settings.value.keywordBlockerConfig.whitelistedKeywords.isEmpty())
        }

    @Test
    fun c9_c10_managesPasswordHashAndAntiUninstallTimestamps() =
        runTest(testDispatcher) {
            assertTrue("No password set should allow verify", viewModel.verifyPassword("anything"))

            viewModel.dispatch(BlockerAction.SetPassword("secret1"))
            viewModel.dispatch(BlockerAction.RecordAdminActivationRequest)
            viewModel.dispatch(BlockerAction.SetAntiUninstallEnabled(true))
            advanceUntilIdle()

            assertTrue(viewModel.verifyPassword("secret1"))
            assertFalse(viewModel.verifyPassword("wrong1"))
            assertEquals(clock.nowMs, viewModel.settings.value.deviceAdminActivationRequestedAt)
            assertTrue(viewModel.settings.value.antiUninstallEnabled)

            viewModel.dispatch(BlockerAction.SetPassword(null))
            advanceUntilIdle()
            assertNull(viewModel.settings.value.passwordHash)
        }

    @Test
    fun c11_c13_c14_replaceabilityProofForGatekeeperAndTempExemptions() =
        runTest(testDispatcher) {
            viewModel.dispatch(BlockerAction.SetGeminiApiKey("local-key"))
            advanceUntilIdle()

            val models = viewModel.refreshGeminiModels()
            assertEquals(listOf("models/local-strict-v1"), models)
            viewModel.dispatch(BlockerAction.SelectGeminiModel(models.first()))
            advanceUntilIdle()

            val rejectedPause =
                viewModel.evaluateGatekeeper(
                    GatekeeperRequest.PauseBlocker("No proof provided here.", rejectionCount = 0),
                    image = null,
                )
            assertFalse(rejectedPause.approved)
            viewModel.dispatch(BlockerAction.IncrementRejectionCount)

            val approvedExemption =
                viewModel.evaluateGatekeeper(
                    GatekeeperRequest.AppExemption("Browser", "com.browser.app", "Urgent work", rejectionCount = 1),
                    image = null,
                )
            assertTrue(approvedExemption.approved)
            viewModel.dispatch(BlockerAction.GrantTempExemption("com.browser.app", approvedExemption.durationMinutes))
            advanceUntilIdle()

            val expectedExpiry = clock.nowMs + 15 * 60_000L
            assertEquals(expectedExpiry, viewModel.settings.value.temporaryIgnoredApps["com.browser.app"])
            assertEquals(1, viewModel.settings.value.noCount)

            viewModel.dispatch(BlockerAction.ExtendTempExemption("com.browser.app", 10))
            advanceUntilIdle()
            assertEquals(expectedExpiry + 10 * 60_000L, viewModel.settings.value.temporaryIgnoredApps["com.browser.app"])

            viewModel.dispatch(BlockerAction.DecreaseTempExemption("com.browser.app", 5))
            advanceUntilIdle()
            assertEquals(clock.nowMs + 5 * 60_000L, viewModel.settings.value.temporaryIgnoredApps["com.browser.app"])

            clock.nowMs += 6 * 60_000L
            viewModel.dispatch(BlockerAction.PruneExpiredTempApps)
            advanceUntilIdle()
            assertFalse(viewModel.settings.value.temporaryIgnoredApps.containsKey("com.browser.app"))
        }

    @Test
    fun c13_c15_removeFromIgnoreListsAndBackupExportImport() =
        runTest(testDispatcher) {
            repository.update {
                it.copy(
                    keywordBlockerConfig = KeywordBlocker(isActive = true, ignoredApps = listOf("com.notes.app")),
                    temporaryIgnoredApps = mapOf("com.notes.app" to clock.nowMs + 60_000L),
                    passwordHash = "hash123",
                    geminiApiKey = "key123",
                )
            }
            viewModel.dispatch(BlockerAction.RemoveAppFromIgnoreLists("com.notes.app"))
            advanceUntilIdle()

            assertFalse(viewModel.settings.value.keywordBlockerConfig.ignoredApps.contains("com.notes.app"))
            assertFalse(viewModel.settings.value.temporaryIgnoredApps.containsKey("com.notes.app"))

            val exported = viewModel.exportSettingsJson(includePassword = false, includeApiKey = false)
            assertFalse(exported.contains("hash123"))
            assertFalse(exported.contains("key123"))
            assertTrue(viewModel.importSettingsJson(exported))
        }
}
