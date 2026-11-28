package com.warden.app.feature.blocker.domain

import java.util.Locale

/**
 * Pure domain evaluator determining whether a visible system screen attempts to tamper with
 * Warden (Force Stop, Clear Data, Uninstall, Accessibility revocation, or Device Admin removal).
 */
object SelfProtectionEvaluator {
    private const val WARDEN_PACKAGE = "com.warden.app"
    private const val DEVICE_ADMIN_ACTIVATION_GRACE_MS = 180_000L

    // Browsers and social/messaging apps are excluded so reading articles or chats mentioning
    // "Warden" and "uninstall" does not trigger false-positive self-protection.
    private val SAFE_PACKAGES =
        setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.opera.browser",
            "com.microsoft.emmx",
            "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser",
            "com.facebook.katana",
            "com.facebook.orca",
            "com.whatsapp",
            "org.telegram.messenger",
            "com.twitter.android",
            "com.instagram.android",
            "com.snapchat.android",
            "com.discord",
            "com.reddit.frontpage",
            "com.zhiliaoapp.musically",
        )

    /** Returns `true` if [packageName] should be inspected by the self-protection guard. */
    fun shouldInspectPackage(packageName: String): Boolean = packageName != WARDEN_PACKAGE && !SAFE_PACKAGES.contains(packageName)

    /**
     * Evaluates visible [screenTexts] and [eventClassName] and returns a user-facing warning
     * message if the screen must be blocked via `GLOBAL_ACTION_HOME`, or `null` if allowed.
     */
    fun evaluate(
        packageName: String,
        eventClassName: String,
        screenTexts: List<String>,
        antiUninstallEnabled: Boolean,
        deviceAdminActivationRequestedAt: Long,
        nowMs: Long,
    ): String? {
        if (!shouldInspectPackage(packageName)) return null
        if (screenTexts.none { it.contains("warden", ignoreCase = true) }) return null
        val lowerTexts = screenTexts.map { it.lowercase(Locale.ROOT) }

        val isDeviceAdminScreen = isDeviceAdminUi(eventClassName, lowerTexts)
        val isActivating = nowMs - deviceAdminActivationRequestedAt < DEVICE_ADMIN_ACTIVATION_GRACE_MS

        val shouldBlock =
            when {
                isDeviceAdminScreen -> antiUninstallEnabled && hasDeactivateAction(lowerTexts) && !isActivating
                hasAccessibilityDisableAction(lowerTexts) -> true
                else -> hasUninstallOrStopAction(lowerTexts)
            }
        return if (shouldBlock) warningMessage(antiUninstallEnabled) else null
    }

    private fun isDeviceAdminUi(
        eventClassName: String,
        lowerTexts: List<String>,
    ): Boolean =
        eventClassName.contains("DeviceAdmin", ignoreCase = true) ||
            eventClassName.contains("DevicePolicy", ignoreCase = true) ||
            lowerTexts.any {
                it.contains("device admin") || it.contains("device administrator") || it.contains("active admin")
            }

    private fun hasDeactivateAction(lowerTexts: List<String>): Boolean =
        lowerTexts.any { it.contains("deactivate") || it.contains("de-activate") }

    private fun hasUninstallOrStopAction(lowerTexts: List<String>): Boolean =
        lowerTexts.any {
            it.contains("force stop") || it.contains("kill") || it.contains("clear data") || it.contains("uninstall")
        }

    private fun hasAccessibilityDisableAction(lowerTexts: List<String>): Boolean =
        lowerTexts.any {
            it.contains("warden needs access to accessibility data") ||
                it.contains("stop warden") ||
                it.contains("turn off warden")
        }

    private fun warningMessage(antiUninstallEnabled: Boolean): String =
        if (antiUninstallEnabled) {
            "Self-protection: Cannot disable accessibility, force stop, clear data, deactivate admin, or uninstall Warden!"
        } else {
            "Self-protection: Cannot disable accessibility, force stop, clear data, or uninstall Warden!"
        }
}
