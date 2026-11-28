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
 * minimization with a single `rootInActiveWindow` fetch per event and guaranteed node recycling.
 */
class BlockerCoordinator(
    private val rootNodeProvider: () -> AccessibilityNodeInfo?,
    private val pressHome: () -> Unit,
    private val showToast: (String) -> Unit,
    private val dismissToast: () -> Unit = {},
    private val engine: BlockingDecisionEngine = BlockingDecisionEngine(),
    private val graceController: GracePeriodController = GracePeriodController(),
    private val clock: Clock = SystemClockProvider(),
) {
    private var lastInstantBlockedPackage: String? = null
    private var lastInstantBlockedKeyword: String? = null
    private var lastInstantBlockedAtMs: Long = 0L

    /** Updates compiled rules when [Settings] changes. */
    fun onSettingsUpdated(settings: Settings) {
        engine.updateSettings(settings)
    }

    /** Updates the shipped built-in adult domain set used by the website blocker. */
    fun onBuiltinDomainsLoaded(domains: Collection<String>) {
        engine.updateBuiltinDomains(domains)
    }

    /** Processes one accessibility event using at most one `rootInActiveWindow` IPC call. */
    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || (event.eventType and TARGET_EVENTS_MASK) == 0) return

        val nowMs = clock.currentTimeMillis()
        val eventPackage = event.packageName?.toString().orEmpty()
        val maybeSelfProtect = eventPackage.isEmpty() || engine.shouldInspectSelfProtection(eventPackage)
        val shouldScanEventPkg = engine.shouldScanPackage(eventPackage, nowMs)

        if (!maybeSelfProtect && !shouldScanEventPkg) {
            cancelPendingChecks()
            return
        }

        val rootNode = rootNodeProvider() ?: return
        try {
            processRootNode(rootNode, event, shouldScanEventPkg, nowMs)
        } finally {
            AccessibilityNodeScanner.recycleNodeCompat(rootNode)
        }
    }

    private fun processRootNode(
        rootNode: AccessibilityNodeInfo,
        event: AccessibilityEvent,
        shouldScanEventPkg: Boolean,
        nowMs: Long,
    ) {
        val rootPackage = rootNode.packageName?.toString().orEmpty()
        if (handleSelfProtectionOnNode(rootNode, rootPackage, event, nowMs)) return
        if (!shouldScanEventPkg || !engine.shouldScanPackage(rootPackage, nowMs)) {
            cancelPendingChecks()
            return
        }
        scanNode(rootNode, rootPackage, nowMs)
    }

    private fun cancelPendingChecks() {
        graceController.cancel()
        clearInstantBlockCooldown()
    }

    /** Cancels pending timers when the hosting service is destroyed. */
    fun shutdown() {
        graceController.cancel()
        clearInstantBlockCooldown()
        dismissToast()
    }

    private fun handleSelfProtectionOnNode(
        rootNode: AccessibilityNodeInfo,
        activePackage: String,
        event: AccessibilityEvent,
        nowMs: Long,
    ): Boolean {
        if (!engine.shouldInspectSelfProtection(activePackage)) return false
        val screenTexts = mutableListOf<String>()
        AccessibilityNodeScanner.collectScreenTexts(rootNode, screenTexts)
        val className = event.className?.toString().orEmpty()
        val warning = engine.evaluateSelfProtection(activePackage, className, screenTexts, nowMs)
        if (warning != null) {
            graceController.cancel()
            clearInstantBlockCooldown()
            pressHome()
            showToast(warning)
            return true
        }
        return false
    }

    private fun scanNode(
        rootNode: AccessibilityNodeInfo,
        rootPackage: String,
        nowMs: Long,
    ) {
        if (rootPackage != lastInstantBlockedPackage) {
            clearInstantBlockCooldown()
        }
        val match = AccessibilityNodeScanner.findFirstMatch(rootNode, engine::findMatch)
        if (match != null) {
            handleKeywordMatch(rootPackage, match, nowMs)
        } else {
            if (rootPackage == lastInstantBlockedPackage) {
                clearInstantBlockCooldown()
            }
            if (graceController.cancelForPackage(rootPackage)) {
                dismissToast()
            }
        }
    }

    private fun handleKeywordMatch(
        packageName: String,
        match: KeywordMatch,
        nowMs: Long,
    ) {
        val graceSeconds = engine.gracePeriodSeconds
        if (graceSeconds <= 0) {
            val withinTransitionCooldown =
                lastInstantBlockedPackage == packageName &&
                    lastInstantBlockedKeyword == match.keyword &&
                    (nowMs - lastInstantBlockedAtMs) < INSTANT_BLOCK_COOLDOWN_MS
            if (withinTransitionCooldown) return

            lastInstantBlockedPackage = packageName
            lastInstantBlockedKeyword = match.keyword
            lastInstantBlockedAtMs = nowMs
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
            val match = AccessibilityNodeScanner.findFirstMatch(rootNode, engine::findMatch)
            if (match != null && match.keyword == expectedKeyword) {
                showToast(engine.formatBlockedMessage(match))
                pressHome()
            }
        } finally {
            AccessibilityNodeScanner.recycleNodeCompat(rootNode)
        }
    }

    private fun clearInstantBlockCooldown() {
        lastInstantBlockedPackage = null
        lastInstantBlockedKeyword = null
        lastInstantBlockedAtMs = 0L
    }

    companion object {
        private const val INSTANT_BLOCK_COOLDOWN_MS = 1_200L
        private const val TARGET_EVENTS_MASK =
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    }
}
