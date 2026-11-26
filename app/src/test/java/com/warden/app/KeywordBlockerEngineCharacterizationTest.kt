package com.warden.app

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.warden.app.core.platform.Clock
import com.warden.app.data.models.KeywordBlocker
import com.warden.app.data.models.Settings
import com.warden.app.feature.blocker.domain.BlockingDecisionEngine
import com.warden.app.feature.blocker.service.BlockerCoordinator
import com.warden.app.feature.blocker.service.GracePeriodController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Characterization tests locking in C6 (keyword matching & context),
 * C7 (blocking execution & grace period), and C9 (self-protection)
 * against the modular [BlockerCoordinator] and [BlockingDecisionEngine].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class KeywordBlockerEngineCharacterizationTest {
    private class FakeClock(var nowMs: Long = 1_000_000L) : Clock {
        override fun currentTimeMillis(): Long = nowMs

        override fun uptimeMillis(): Long = nowMs
    }

    private lateinit var clock: FakeClock
    private lateinit var coordinator: BlockerCoordinator
    private var rootNodeProvider: (() -> AccessibilityNodeInfo?)? = null
    private var homePressCount = 0
    private val toasts = mutableListOf<String>()

    @Before
    fun setUp() {
        clock = FakeClock()
        homePressCount = 0
        toasts.clear()
        coordinator =
            BlockerCoordinator(
                rootNodeProvider = { rootNodeProvider?.invoke() },
                pressHome = { homePressCount++ },
                showToast = { toasts.add(it) },
                engine = BlockingDecisionEngine(),
                graceController = GracePeriodController(Handler(Looper.getMainLooper())),
                clock = clock,
            )
        configureBlocker(
            isTurnedOn = true,
            blocked = listOf("shorts", "breaking news"),
            whitelisted = listOf("basketball shorts"),
            ignoredApps = listOf("com.ignored.app"),
            graceSeconds = 0,
        )
    }

    private fun configureBlocker(
        isTurnedOn: Boolean = true,
        blocked: List<String> = emptyList(),
        whitelisted: List<String> = emptyList(),
        ignoredApps: List<String> = emptyList(),
        tempIgnoredApps: Map<String, Long> = emptyMap(),
        disabledUntil: Long = 0L,
        graceSeconds: Int = 0,
        antiUninstall: Boolean = false,
        adminRequestedAt: Long = 0L,
    ) {
        coordinator.onSettingsUpdated(
            Settings(
                keywordBlockerConfig =
                    KeywordBlocker(
                        isActive = isTurnedOn,
                        blockedKeywords = blocked,
                        whitelistedKeywords = whitelisted,
                        ignoredApps = ignoredApps,
                    ),
                temporaryIgnoredApps = tempIgnoredApps,
                blockerDisabledUntil = disabledUntil,
                ignoreGracePeriodSeconds = graceSeconds,
                antiUninstallEnabled = antiUninstall,
                deviceAdminActivationRequestedAt = adminRequestedAt,
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun makeNode(
        pkg: String,
        text: String? = null,
        contentDesc: String? = null,
        children: List<AccessibilityNodeInfo> = emptyList(),
    ): AccessibilityNodeInfo {
        val node = AccessibilityNodeInfo.obtain()
        node.packageName = pkg
        if (text != null) node.text = text
        if (contentDesc != null) node.contentDescription = contentDesc
        val shadow = shadowOf(node)
        children.forEach { shadow.addChild(it) }
        return node
    }

    @Suppress("DEPRECATION")
    private fun makeEvent(
        pkg: String,
        type: Int = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        className: String? = null,
    ): AccessibilityEvent {
        val event = AccessibilityEvent.obtain(type)
        event.packageName = pkg
        if (className != null) event.className = className
        return event
    }

    @Test
    fun c6_matchesWholeWordCaseInsensitiveAndShowsSingleContextToast() {
        val text = "one two three four five six seven eight SHORTS nine ten eleven twelve thirteen fourteen fifteen sixteen"
        rootNodeProvider = { makeNode("com.youtube.app", text = text) }

        coordinator.onAccessibilityEvent(makeEvent("com.youtube.app"))

        assertEquals(1, homePressCount)
        assertEquals("Instant block must emit exactly 1 toast (fixing baseline duplicate)", 1, toasts.size)
        val toast = toasts.last()
        assertTrue("Expected blocked word toast, got: $toast", toast.contains("Blocked word shorts was found"))
        assertTrue(
            "Expected 7 words before/after context, got: $toast",
            toast.contains("two three four five six seven eight SHORTS nine ten eleven twelve thirteen fourteen fifteen"),
        )
        assertFalse("Should not include 8th word before", toast.contains("one two"))
        assertFalse("Should not include 8th word after", toast.contains("fifteen sixteen"))
    }

    @Test
    fun c6_doesNotMatchSubstringInsideAnotherWord() {
        rootNodeProvider = { makeNode("com.browser.app", text = "shortstack and unshortened") }

        coordinator.onAccessibilityEvent(makeEvent("com.browser.app"))

        assertEquals(0, homePressCount)
    }

    @Test
    fun c6_whitelistedPhrasePreventsFalsePositive() {
        rootNodeProvider = { makeNode("com.shop.app", text = "Buy new Basketball Shorts today") }

        coordinator.onAccessibilityEvent(makeEvent("com.shop.app"))

        assertEquals(0, homePressCount)
    }

    @Test
    fun c6_scansContentDescriptionInChildNodes() {
        rootNodeProvider = {
            val child = makeNode("com.social.app", contentDesc = "Watch Breaking News live")
            makeNode("com.social.app", text = "Home", children = listOf(child))
        }

        coordinator.onAccessibilityEvent(makeEvent("com.social.app"))

        assertEquals(1, homePressCount)
    }

    @Test
    fun c7_skipsWhenInactiveOrDisabledUntilOrSelfOrIgnoredOrTempIgnored() {
        // Inactive
        configureBlocker(isTurnedOn = false, blocked = listOf("shorts"))
        rootNodeProvider = { makeNode("com.app.one", text = "shorts") }
        coordinator.onAccessibilityEvent(makeEvent("com.app.one"))
        assertEquals(0, homePressCount)

        // Disabled until future
        configureBlocker(isTurnedOn = true, blocked = listOf("shorts"), disabledUntil = clock.nowMs + 60_000L)
        rootNodeProvider = { makeNode("com.app.one", text = "shorts") }
        coordinator.onAccessibilityEvent(makeEvent("com.app.one"))
        assertEquals(0, homePressCount)

        // Self package
        configureBlocker(isTurnedOn = true, blocked = listOf("shorts"))
        rootNodeProvider = { makeNode("com.warden.app", text = "shorts") }
        coordinator.onAccessibilityEvent(makeEvent("com.warden.app"))
        assertEquals(0, homePressCount)

        // Permanent ignored app
        configureBlocker(isTurnedOn = true, blocked = listOf("shorts"), ignoredApps = listOf("com.ignored.app"))
        rootNodeProvider = { makeNode("com.ignored.app", text = "shorts") }
        coordinator.onAccessibilityEvent(makeEvent("com.ignored.app"))
        assertEquals(0, homePressCount)

        // Active temporary ignored app
        configureBlocker(
            isTurnedOn = true,
            blocked = listOf("shorts"),
            tempIgnoredApps = mapOf("com.temp.app" to clock.nowMs + 60_000L),
        )
        rootNodeProvider = { makeNode("com.temp.app", text = "shorts") }
        coordinator.onAccessibilityEvent(makeEvent("com.temp.app"))
        assertEquals(0, homePressCount)

        // Expired temporary ignored app -> SHOULD block
        configureBlocker(
            isTurnedOn = true,
            blocked = listOf("shorts"),
            tempIgnoredApps = mapOf("com.temp.app" to clock.nowMs - 1_000L),
        )
        rootNodeProvider = { makeNode("com.temp.app", text = "shorts") }
        coordinator.onAccessibilityEvent(makeEvent("com.temp.app"))
        assertEquals(1, homePressCount)
    }

    @Test
    fun c7_gracePeriodDelaysHomePressAndRechecks() {
        configureBlocker(isTurnedOn = true, blocked = listOf("shorts"), graceSeconds = 2)
        rootNodeProvider = { makeNode("com.video.app", text = "Watch shorts now") }

        coordinator.onAccessibilityEvent(makeEvent("com.video.app"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Should not press home before grace period elapses", 0, homePressCount)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals("Should press home after grace period if keyword still visible", 1, homePressCount)
    }

    @Test
    fun c7_gracePeriodCancelledWhenUserLeavesContent() {
        configureBlocker(isTurnedOn = true, blocked = listOf("shorts"), graceSeconds = 3)
        rootNodeProvider = { makeNode("com.video.app", text = "Watch shorts now") }

        coordinator.onAccessibilityEvent(makeEvent("com.video.app"))
        rootNodeProvider = { makeNode("com.video.app", text = "Clean documentation page") }
        coordinator.onAccessibilityEvent(makeEvent("com.video.app"))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertEquals("Grace period should be cancelled when keyword disappears", 0, homePressCount)
    }

    @Test
    fun c9_selfProtectionBlocksAppInfoUninstallAndAccessibilityDisable() {
        configureBlocker(isTurnedOn = false, antiUninstall = false)

        rootNodeProvider = {
            makeNode(
                "com.android.settings",
                children =
                    listOf(
                        makeNode("com.android.settings", text = "Warden"),
                        makeNode("com.android.settings", text = "Force stop"),
                    ),
            )
        }
        coordinator.onAccessibilityEvent(makeEvent("com.android.settings"))
        assertEquals(1, homePressCount)

        rootNodeProvider = {
            makeNode(
                "com.android.settings",
                children =
                    listOf(
                        makeNode("com.android.settings", text = "Stop Warden?"),
                    ),
            )
        }
        coordinator.onAccessibilityEvent(makeEvent("com.android.settings"))
        assertEquals(2, homePressCount)
    }

    @Test
    fun c9_selfProtectionBlocksDeviceAdminDeactivateOnlyOutsideGraceWindow() {
        configureBlocker(
            isTurnedOn = false,
            antiUninstall = true,
            adminRequestedAt = clock.nowMs - 10_000L,
        )
        rootNodeProvider = {
            makeNode(
                "com.android.settings",
                children =
                    listOf(
                        makeNode("com.android.settings", text = "Warden"),
                        makeNode("com.android.settings", text = "Device admin app"),
                        makeNode("com.android.settings", text = "Deactivate"),
                    ),
            )
        }
        coordinator.onAccessibilityEvent(
            makeEvent("com.android.settings", className = "com.android.settings.DeviceAdminAdd"),
        )
        assertEquals("Should allow Device Admin screen during 180s activation window", 0, homePressCount)

        configureBlocker(
            isTurnedOn = false,
            antiUninstall = true,
            adminRequestedAt = clock.nowMs - 200_000L,
        )
        coordinator.onAccessibilityEvent(
            makeEvent("com.android.settings", className = "com.android.settings.DeviceAdminAdd"),
        )
        assertEquals("Should block Device Admin deactivation after 180s window", 1, homePressCount)
    }

    @Test
    fun c9_safePackagesExemptFromSelfProtection() {
        configureBlocker(isTurnedOn = false, antiUninstall = true)
        rootNodeProvider = {
            makeNode(
                "com.android.chrome",
                children =
                    listOf(
                        makeNode("com.android.chrome", text = "How to uninstall Warden app"),
                    ),
            )
        }
        coordinator.onAccessibilityEvent(makeEvent("com.android.chrome"))
        assertEquals("Browsers in safe list must not trigger self-protection", 0, homePressCount)
    }
}
