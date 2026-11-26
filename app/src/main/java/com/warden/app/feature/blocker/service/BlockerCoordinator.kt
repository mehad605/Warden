package com.warden.app.feature.blocker.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.warden.app.core.platform.Clock
import com.warden.app.core.platform.SystemClockProvider
import com.warden.app.data.models.Settings
import com.warden.app.feature.blocker.domain.BlockingDecisionEngine
import com.warden.app.feature.blocker.domain.KeywordMatch

/**
 * Coordinates accessibility event processing, self-protection, keyword scanning, and grace-period
 * minimization with explicit dependency injection and guaranteed `AccessibilityNodeInfo` recycling.
 */
class BlockerCoordinator(
    private val rootNodeProvider: () -> AccessibilityNodeInfo?,
    private val pressHome: () -> Unit,
    private val showToast: (String) -> Unit,
    private val engine: BlockingDecisionEngine = BlockingDecisionEngine(),
    private val graceController: GracePeriodController = GracePeriodController(),
    private val clock: Clock = SystemClockProvider(),
) {
    /** Updates compiled rules when [Settings] changes. */
    fun onSettingsUpdated(settings: Settings) {
        engine.updateSettings(settings)
    }

    /** Processes one accessibility event; safe to invoke from a background worker coroutine. */
    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || (event.eventType and TARGET_EVENTS_MASK) == 0) return

        val nowMs = clock.currentTimeMillis()
        if (handleSelfProtection(event, nowMs)) return

        val eventPackage = event.packageName?.toString().orEmpty()
        if (!engine.shouldScanPackage(eventPackage, nowMs)) {
            graceController.cancel()
            return
        }

        scanActiveWindow(eventPackage, nowMs)
    }

    /** Cancels pending timers when the hosting service is destroyed. */
    fun shutdown() {
        graceController.cancel()
    }

    private fun handleSelfProtection(
        event: AccessibilityEvent,
        nowMs: Long,
    ): Boolean {
        val rootNode = rootNodeProvider() ?: return false
        try {
            val activePackage = rootNode.packageName?.toString().orEmpty()
            if (!engine.shouldInspectSelfProtection(activePackage)) return false

            val screenTexts = mutableListOf<String>()
            AccessibilityNodeScanner.collectScreenTexts(rootNode, screenTexts)
            val className = event.className?.toString().orEmpty()
            val warning = engine.evaluateSelfProtection(activePackage, className, screenTexts, nowMs)
            if (warning != null) {
                graceController.cancel()
                pressHome()
                showToast(warning)
                return true
            }
            return false
        } finally {
            AccessibilityNodeScanner.recycleNodeCompat(rootNode)
        }
    }

    private fun scanActiveWindow(
        eventPackage: String,
        nowMs: Long,
    ) {
        val rootNode = rootNodeProvider() ?: return
        try {
            val rootPackage = rootNode.packageName?.toString().orEmpty()
            if (!engine.shouldScanPackage(rootPackage, nowMs)) {
                graceController.cancel()
                return
            }
            val match = AccessibilityNodeScanner.findFirstMatch(rootNode, engine.matcher)
            if (match != null) {
                handleKeywordMatch(eventPackage, match)
            } else {
                graceController.cancelForPackage(eventPackage)
            }
        } finally {
            AccessibilityNodeScanner.recycleNodeCompat(rootNode)
        }
    }

    private fun handleKeywordMatch(
        packageName: String,
        match: KeywordMatch,
    ) {
        val graceSeconds = engine.gracePeriodSeconds
        if (graceSeconds <= 0) {
            graceController.cancel()
            showToast(engine.formatBlockedMessage(match))
            pressHome()
            return
        }

        val scheduled =
            graceController.scheduleIfNew(packageName, match.keyword, graceSeconds) {
                recheckAndMinimize(packageName, match.keyword)
            }
        if (scheduled) {
            showToast(engine.formatBlockedMessage(match))
        }
    }

    private fun recheckAndMinimize(
        expectedPackage: String,
        expectedKeyword: String,
    ) {
        val rootNode = rootNodeProvider() ?: return
        try {
            if (rootNode.packageName?.toString() != expectedPackage) return
            val match = AccessibilityNodeScanner.findFirstMatch(rootNode, engine.matcher)
            if (match != null && match.keyword == expectedKeyword) {
                showToast(engine.formatBlockedMessage(match))
                pressHome()
            }
        } finally {
            AccessibilityNodeScanner.recycleNodeCompat(rootNode)
        }
    }

    companion object {
        private const val TARGET_EVENTS_MASK =
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    }
}
