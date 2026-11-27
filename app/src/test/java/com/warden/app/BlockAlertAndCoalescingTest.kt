package com.warden.app

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import com.warden.app.core.platform.Clock
import com.warden.app.data.models.KeywordBlocker
import com.warden.app.data.models.Settings
import com.warden.app.feature.blocker.domain.BlockingDecisionEngine
import com.warden.app.feature.blocker.service.BlockAlertPresenter
import com.warden.app.feature.blocker.service.BlockerCoordinator
import com.warden.app.feature.blocker.service.GracePeriodController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Tests locking in non-stacking alert presentation, keyboard/IME backspace coalescing,
 * immediate alert dismissal on clearance, and instant re-triggering upon returning to a blocked app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class BlockAlertAndCoalescingTest {
    private class FakeClock(var nowMs: Long = 1_000_000L) : Clock {
        override fun currentTimeMillis(): Long = nowMs

        override fun uptimeMillis(): Long = nowMs
    }

    private lateinit var clock: FakeClock
    private lateinit var coordinator: BlockerCoordinator
    private var rootNodeProvider: (() -> AccessibilityNodeInfo?)? = null
    private var homePressCount = 0
    private var dismissCount = 0
    private val alerts = mutableListOf<String>()

    @Before
    fun setUp() {
        clock = FakeClock()
        homePressCount = 0
        dismissCount = 0
        alerts.clear()
        coordinator =
            BlockerCoordinator(
                rootNodeProvider = { rootNodeProvider?.invoke() },
                pressHome = { homePressCount++ },
                showToast = { alerts.add(it) },
                dismissToast = { dismissCount++ },
                engine = BlockingDecisionEngine(),
                graceController = GracePeriodController(Handler(Looper.getMainLooper())),
                clock = clock,
            )
    }

    private fun configure(
        blocked: List<String>,
        graceSeconds: Int,
    ) {
        coordinator.onSettingsUpdated(
            Settings(
                keywordBlockerConfig = KeywordBlocker(isActive = true, blockedKeywords = blocked),
                ignoreGracePeriodSeconds = graceSeconds,
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun makeNode(
        pkg: String,
        text: String,
    ): AccessibilityNodeInfo {
        val node = AccessibilityNodeInfo.obtain()
        node.packageName = pkg
        node.text = text
        return node
    }

    @Suppress("DEPRECATION")
    private fun makeEvent(pkg: String): AccessibilityEvent {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.packageName = pkg
        return event
    }

    @Test
    fun backspacingWithAlternatingKeyboardEventsDoesNotSpamAlertsAndDismissesWhenCleared() {
        configure(blocked = listOf("anime"), graceSeconds = 2)

        rootNodeProvider = { makeNode("com.android.chrome", "anime heaven") }
        coordinator.onAccessibilityEvent(makeEvent("com.android.chrome"))
        assertEquals(1, alerts.size)

        // Rapid backspaces alternating between keyboard IME and browser events
        rootNodeProvider = { makeNode("com.android.chrome", "anime heave") }
        coordinator.onAccessibilityEvent(makeEvent("com.google.android.inputmethod.latin"))
        rootNodeProvider = { makeNode("com.android.chrome", "anime heav") }
        coordinator.onAccessibilityEvent(makeEvent("com.android.chrome"))
        rootNodeProvider = { makeNode("com.android.chrome", "anime hea") }
        coordinator.onAccessibilityEvent(makeEvent("com.google.android.inputmethod.latin"))

        assertEquals("Backspacing must not emit duplicate alerts while grace timer runs", 1, alerts.size)
        assertEquals(0, homePressCount)

        // User clears the blocked word before grace expires -> alert dismisses immediately
        rootNodeProvider = { makeNode("com.android.chrome", "anim") }
        coordinator.onAccessibilityEvent(makeEvent("com.android.chrome"))
        assertEquals("Clearing blocked word should dismiss the active alert immediately", 1, dismissCount)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals("Cleared word must not kick user Home", 0, homePressCount)
    }

    @Test
    fun instantBlockCoalescesHomeTransitionButRetriggersImmediatelyOnReturn() {
        configure(blocked = listOf("anime"), graceSeconds = 0)

        rootNodeProvider = { makeNode("com.android.chrome", "anime heaven") }
        coordinator.onAccessibilityEvent(makeEvent("com.android.chrome"))
        assertEquals(1, homePressCount)
        assertEquals(1, alerts.size)

        // Trailing events during Home animation (150ms later) are coalesced
        clock.nowMs += 150L
        rootNodeProvider = { makeNode("com.android.chrome", "anime heave") }
        coordinator.onAccessibilityEvent(makeEvent("com.android.chrome"))
        assertEquals("Trailing transition events must not spam extra Home/alerts", 1, homePressCount)
        assertEquals(1, alerts.size)

        // Home launcher appears -> clears transition cooldown immediately
        clock.nowMs += 200L
        rootNodeProvider = { makeNode("com.android.launcher", "Search") }
        coordinator.onAccessibilityEvent(makeEvent("com.android.launcher"))

        // Returning to browser while blocked word is still present -> kicks Home & alerts again immediately!
        clock.nowMs += 150L
        rootNodeProvider = { makeNode("com.android.chrome", "anime heave") }
        coordinator.onAccessibilityEvent(makeEvent("com.android.chrome"))
        assertEquals("Returning to blocked screen must kick Home again immediately", 2, homePressCount)
        assertEquals("Returning to blocked screen must show alert again", 2, alerts.size)
    }

    @Test
    fun alertPresenterParsesBlockedAndWarningCopyCleanly() {
        val presenter = BlockAlertPresenter(ApplicationProvider.getApplicationContext())
        val blocked = presenter.parseMessage("""Blocked word anime was found : " anime heaven """")
        assertTrue(blocked.title.contains("anime"))
        assertTrue(blocked.detail.contains("anime heaven"))

        val warning = presenter.parseMessage("Anti-uninstall protection is active.")
        assertEquals("Warden Shield", warning.title)
        assertEquals("Anti-uninstall protection is active.", warning.detail)
        presenter.shutdown()
    }
}
