package com.warden.app

import android.accessibilityservice.AccessibilityService
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.warden.app.blockers.KeywordBlocker
import com.warden.app.services.BaseBlockingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.Duration

/**
 * Characterization tests locking in C6 (keyword matching & context),
 * C7 (blocking execution & grace period), and C9 (self-protection)
 * against the untouched baseline-v1 engine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class KeywordBlockerEngineCharacterizationTest {

    private lateinit var service: FakeBlockingService
    private lateinit var blocker: KeywordBlocker

    class FakeBlockingService : BaseBlockingService() {
        var fakeRootNodeProvider: (() -> AccessibilityNodeInfo?)? = null

        override fun getRootInActiveWindow(): AccessibilityNodeInfo? = fakeRootNodeProvider?.invoke()
    }

    private fun homePressCount(): Int =
        shadowOf(service).globalActionsPerformed.count { it == AccessibilityService.GLOBAL_ACTION_HOME }

    @Before
    fun setUp() {
        ShadowToast.reset()
        service = Robolectric.buildService(FakeBlockingService::class.java).create().get()
        blocker = KeywordBlocker()
        configureBlocker(
            isTurnedOn = true,
            blocked = setOf("shorts", "breaking news"),
            whitelisted = setOf("basketball shorts"),
            ignoredApps = setOf("com.ignored.app"),
            graceSeconds = 0
        )
    }

    private fun configureBlocker(
        isTurnedOn: Boolean = true,
        blocked: Set<String> = emptySet(),
        whitelisted: Set<String> = emptySet(),
        ignoredApps: Set<String> = emptySet(),
        tempIgnoredApps: Map<String, Long> = emptyMap(),
        disabledUntil: Long = 0L,
        graceSeconds: Int = 0,
        antiUninstall: Boolean = false,
        adminRequestedAt: Long = 0L
    ) {
        setField(blocker, "service", service)
        blocker.blockedKeyword = HashSet(blocked)
        blocker.whitelistedKeywords = HashSet(whitelisted)
        blocker.ignoredApps = HashSet(ignoredApps)
        blocker.ignoreGracePeriodSeconds = graceSeconds
        blocker.antiUninstallEnabled = antiUninstall
        blocker.deviceAdminActivationRequestedAt = adminRequestedAt
        setField(blocker, "isTurnedOn", isTurnedOn)
        setField(blocker, "blockerDisabledUntil", disabledUntil)
        setField(blocker, "temporaryIgnoredApps", tempIgnoredApps)
        setField(blocker, "lastEventTimeStamp", -10_000L)

        val valid = blocked.filter { it.isNotBlank() }.map { Regex.escape(it.lowercase()) }
        val regex = if (valid.isNotEmpty()) {
            val pat = valid.sortedByDescending { it.length }.joinToString("|")
            Regex("""(?<!\w)(?:$pat)+(?!\w)""")
        } else {
            null
        }
        setField(blocker, "blockedPattern", regex)
    }

    private fun setField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun makeNode(
        pkg: String,
        text: String? = null,
        contentDesc: String? = null,
        children: List<AccessibilityNodeInfo> = emptyList()
    ): AccessibilityNodeInfo {
        val node = AccessibilityNodeInfo.obtain()
        node.packageName = pkg
        if (text != null) node.text = text
        if (contentDesc != null) node.contentDescription = contentDesc
        val shadow = shadowOf(node)
        children.forEach { shadow.addChild(it) }
        return node
    }

    private fun makeEvent(
        pkg: String,
        type: Int = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        className: String? = null
    ): AccessibilityEvent {
        val event = AccessibilityEvent.obtain(type)
        event.packageName = pkg
        if (className != null) event.className = className
        return event
    }

    @Test
    fun c6_matchesWholeWordCaseInsensitiveAndShowsContextToast() {
        val text = "one two three four five six seven eight SHORTS nine ten eleven twelve thirteen fourteen fifteen sixteen"
        service.fakeRootNodeProvider = { makeNode("com.youtube.app", text = text) }

        blocker.checkIfUserGettingFreaky(makeEvent("com.youtube.app"))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, homePressCount())
        val toast = ShadowToast.getTextOfLatestToast()
        assertTrue("Expected blocked word toast, got: $toast", toast.contains("Blocked word shorts was found"))
        assertTrue("Expected 7 words before/after context, got: $toast", toast.contains("two three four five six seven eight SHORTS nine ten eleven twelve thirteen fourteen fifteen"))
        assertFalse("Should not include 8th word before", toast.contains("one two"))
        assertFalse("Should not include 8th word after", toast.contains("fifteen sixteen"))
    }

    @Test
    fun c6_doesNotMatchSubstringInsideAnotherWord() {
        service.fakeRootNodeProvider = { makeNode("com.browser.app", text = "shortstack and unshortened") }

        blocker.checkIfUserGettingFreaky(makeEvent("com.browser.app"))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, homePressCount())
    }

    @Test
    fun c6_whitelistedPhrasePreventsFalsePositive() {
        service.fakeRootNodeProvider = { makeNode("com.shop.app", text = "Buy new Basketball Shorts today") }

        blocker.checkIfUserGettingFreaky(makeEvent("com.shop.app"))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, homePressCount())
    }

    @Test
    fun c6_scansContentDescriptionInChildNodes() {
        service.fakeRootNodeProvider = {
            val child = makeNode("com.social.app", contentDesc = "Watch Breaking News live")
            makeNode("com.social.app", text = "Home", children = listOf(child))
        }

        blocker.checkIfUserGettingFreaky(makeEvent("com.social.app"))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, homePressCount())
    }

    @Test
    fun c7_skipsWhenInactiveOrDisabledUntilOrSelfOrIgnoredOrTempIgnored() {
        // Inactive
        configureBlocker(isTurnedOn = false, blocked = setOf("shorts"))
        service.fakeRootNodeProvider = { makeNode("com.app.one", text = "shorts") }
        blocker.checkIfUserGettingFreaky(makeEvent("com.app.one"))
        assertEquals(0, homePressCount())

        // Disabled until future
        configureBlocker(isTurnedOn = true, blocked = setOf("shorts"), disabledUntil = System.currentTimeMillis() + 60_000L)
        service.fakeRootNodeProvider = { makeNode("com.app.one", text = "shorts") }
        blocker.checkIfUserGettingFreaky(makeEvent("com.app.one"))
        assertEquals(0, homePressCount())

        // Self package
        configureBlocker(isTurnedOn = true, blocked = setOf("shorts"))
        service.fakeRootNodeProvider = { makeNode("com.warden.app", text = "shorts") }
        blocker.checkIfUserGettingFreaky(makeEvent("com.warden.app"))
        assertEquals(0, homePressCount())

        // Permanent ignored app
        configureBlocker(isTurnedOn = true, blocked = setOf("shorts"), ignoredApps = setOf("com.ignored.app"))
        service.fakeRootNodeProvider = { makeNode("com.ignored.app", text = "shorts") }
        blocker.checkIfUserGettingFreaky(makeEvent("com.ignored.app"))
        assertEquals(0, homePressCount())

        // Active temporary ignored app
        configureBlocker(
            isTurnedOn = true,
            blocked = setOf("shorts"),
            tempIgnoredApps = mapOf("com.temp.app" to System.currentTimeMillis() + 60_000L)
        )
        service.fakeRootNodeProvider = { makeNode("com.temp.app", text = "shorts") }
        blocker.checkIfUserGettingFreaky(makeEvent("com.temp.app"))
        assertEquals(0, homePressCount())

        // Expired temporary ignored app -> SHOULD block
        configureBlocker(
            isTurnedOn = true,
            blocked = setOf("shorts"),
            tempIgnoredApps = mapOf("com.temp.app" to System.currentTimeMillis() - 1_000L)
        )
        service.fakeRootNodeProvider = { makeNode("com.temp.app", text = "shorts") }
        blocker.checkIfUserGettingFreaky(makeEvent("com.temp.app"))
        assertEquals(1, homePressCount())
    }

    @Test
    fun c7_gracePeriodDelaysHomePressAndRechecks() {
        configureBlocker(isTurnedOn = true, blocked = setOf("shorts"), graceSeconds = 2)
        service.fakeRootNodeProvider = { makeNode("com.video.app", text = "Watch shorts now") }

        blocker.checkIfUserGettingFreaky(makeEvent("com.video.app"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Should not press home before grace period elapses", 0, homePressCount())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals("Should press home after grace period if keyword still visible", 1, homePressCount())
    }

    @Test
    fun c7_gracePeriodCancelledWhenUserLeavesContent() {
        configureBlocker(isTurnedOn = true, blocked = setOf("shorts"), graceSeconds = 3)
        service.fakeRootNodeProvider = { makeNode("com.video.app", text = "Watch shorts now") }

        blocker.checkIfUserGettingFreaky(makeEvent("com.video.app"))
        // User scrolls away from keyword within grace period
        service.fakeRootNodeProvider = { makeNode("com.video.app", text = "Clean documentation page") }
        blocker.checkIfUserGettingFreaky(makeEvent("com.video.app"))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertEquals("Grace period should be cancelled when keyword disappears", 0, homePressCount())
    }

    @Test
    fun c9_selfProtectionBlocksAppInfoUninstallAndAccessibilityDisable() {
        configureBlocker(isTurnedOn = false, antiUninstall = false)

        // App info screen with Force Stop
        service.fakeRootNodeProvider = {
            makeNode(
                "com.android.settings",
                children = listOf(
                    makeNode("com.android.settings", text = "Warden"),
                    makeNode("com.android.settings", text = "Force stop")
                )
            )
        }
        blocker.checkIfUserGettingFreaky(makeEvent("com.android.settings"))
        assertEquals(1, homePressCount())

        // Accessibility disable dialog
        service.fakeRootNodeProvider = {
            makeNode(
                "com.android.settings",
                children = listOf(
                    makeNode("com.android.settings", text = "Stop Warden?")
                )
            )
        }
        blocker.checkIfUserGettingFreaky(makeEvent("com.android.settings"))
        assertEquals(2, homePressCount())
    }

    @Test
    fun c9_selfProtectionBlocksDeviceAdminDeactivateOnlyOutsideGraceWindow() {
        // Inside 180s activation grace window -> allowed
        configureBlocker(
            isTurnedOn = false,
            antiUninstall = true,
            adminRequestedAt = System.currentTimeMillis() - 10_000L
        )
        service.fakeRootNodeProvider = {
            makeNode(
                "com.android.settings",
                children = listOf(
                    makeNode("com.android.settings", text = "Warden"),
                    makeNode("com.android.settings", text = "Device admin app"),
                    makeNode("com.android.settings", text = "Deactivate")
                )
            )
        }
        blocker.checkIfUserGettingFreaky(
            makeEvent("com.android.settings", className = "com.android.settings.DeviceAdminAdd")
        )
        assertEquals("Should allow Device Admin screen during 180s activation window", 0, homePressCount())

        // Outside 180s activation grace window -> blocked
        configureBlocker(
            isTurnedOn = false,
            antiUninstall = true,
            adminRequestedAt = System.currentTimeMillis() - 200_000L
        )
        service.fakeRootNodeProvider = {
            makeNode(
                "com.android.settings",
                children = listOf(
                    makeNode("com.android.settings", text = "Warden"),
                    makeNode("com.android.settings", text = "Device admin app"),
                    makeNode("com.android.settings", text = "Deactivate")
                )
            )
        }
        blocker.checkIfUserGettingFreaky(
            makeEvent("com.android.settings", className = "com.android.settings.DeviceAdminAdd")
        )
        assertEquals("Should block Device Admin deactivation after 180s window", 1, homePressCount())
    }

    @Test
    fun c9_safePackagesExemptFromSelfProtection() {
        configureBlocker(isTurnedOn = false, antiUninstall = true)
        service.fakeRootNodeProvider = {
            makeNode(
                "com.android.chrome",
                children = listOf(
                    makeNode("com.android.chrome", text = "How to uninstall Warden app")
                )
            )
        }
        blocker.checkIfUserGettingFreaky(makeEvent("com.android.chrome"))
        assertEquals("Browsers in safe list must not trigger self-protection", 0, homePressCount())
    }
}
