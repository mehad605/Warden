package com.warden.app.blockers

import com.warden.app.R

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.warden.app.services.BaseBlockingService
import java.util.Locale

class KeywordBlocker : BaseBlocker() {
    companion object {
        const val INTENT_ACTION_REFRESH_CONFIG =
            "com.warden.app.refresh.keywordblocker.config"
        private const val TARGET_EVENTS_MASK = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    }

    private lateinit var service : BaseBlockingService

    lateinit var blockedKeyword: HashSet<String>
    lateinit var whitelistedKeywords: HashSet<String>
    lateinit var ignoredApps: HashSet<String>
    var ignoreGracePeriodSeconds: Int = 2
    var antiUninstallEnabled: Boolean = false
    var deviceAdminActivationRequestedAt: Long = 0L

    private var isTurnedOn = false
    private var blockerDisabledUntil = 0L
    private var temporaryIgnoredApps: Map<String, Long> = emptyMap()
    private var lastEventTimeStamp = 0L
    private var refreshCooldown : Int = 2000

    private val handler = Handler(Looper.getMainLooper())
    private var pendingGraceMinimizeRunnable: Runnable? = null
    private var delayedDetectedKeyword: String? = null
    private var delayedPackageName: String? = null

    private var blockedPattern: Regex? = null

    private fun containsBlockedKeyword(text: String): Pair<String, String>? {
        val pattern = blockedPattern ?: return null

        var lowerText = text.lowercase(Locale.ROOT)

        if (this::whitelistedKeywords.isInitialized) {
            for (white in whitelistedKeywords) {
                val lowerWhite = white.lowercase(Locale.ROOT)
                if (lowerWhite.isNotBlank()) {
                    val spaces = " ".repeat(lowerWhite.length)
                    lowerText = lowerText.replace(lowerWhite, spaces)
                }
            }
        }

        val match = pattern.find(lowerText)
        if (match != null) {
            val keyword = match.value
            val context = extractContext(text, match.range.first, match.value.length)
            return Pair(keyword, context)
        }
        return null
    }

    private fun extractContext(text: String, keywordStartIndex: Int, keywordLength: Int): String {
        val beforeText = text.substring(0, keywordStartIndex)
        val afterText = text.substring(keywordStartIndex + keywordLength)
        
        val beforeWords = beforeText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val afterWords = afterText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        
        val wordsBefore = beforeWords.takeLast(7).joinToString(" ")
        val wordsAfter = afterWords.take(7).joinToString(" ")
        val actualKeyword = text.substring(keywordStartIndex, keywordStartIndex + keywordLength)
        
        return listOf(wordsBefore, actualKeyword, wordsAfter).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun checkIfUserGettingFreaky(event: AccessibilityEvent?) {
        fun showMessage(word: String, contextString: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    service,
                    "Blocked word $word was found : \" $contextString \"",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        fun pressHome(word: String, contextString: String) {
            showMessage(word, contextString)
            service.pressHome()
        }

        // Self-Protection check: Prevent clearing data, force stop, or uninstalling
        val antiUninstallSafePackages = setOf(
            // Browsers
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.opera.browser",
            "com.microsoft.emmx",
            "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser",
            // Social & Messaging
            "com.facebook.katana",
            "com.facebook.orca",
            "com.whatsapp",
            "org.telegram.messenger",
            "com.twitter.android",
            "com.instagram.android",
            "com.snapchat.android",
            "com.discord",
            "com.reddit.frontpage",
            "com.zhiliaoapp.musically"
        )

        if (event != null && (event.eventType and TARGET_EVENTS_MASK) != 0) {
            val rootNode = service.rootInActiveWindow
            if (rootNode != null) {
                val appPackage = rootNode.packageName?.toString() ?: ""
                if (appPackage != "com.warden.app" && !antiUninstallSafePackages.contains(appPackage)) {
                    val screenTexts = mutableListOf<String>()
                    collectScreenTexts(rootNode, screenTexts)
                    val hasAppName = screenTexts.any {
                        val lower = it.lowercase(Locale.ROOT)
                        lower.contains("warden")
                    }
                    
                    val eventClassName = event.className?.toString() ?: ""
                    val isDeviceAdminScreen = eventClassName.contains("DeviceAdmin", ignoreCase = true) ||
                            eventClassName.contains("DevicePolicy", ignoreCase = true) ||
                            screenTexts.any {
                                val lower = it.lowercase(Locale.ROOT)
                                lower.contains("device admin") || lower.contains("device administrator") || lower.contains("active admin")
                            }

                    val hasDeactivateAction = screenTexts.any {
                        val lower = it.lowercase(Locale.ROOT)
                        lower.contains("deactivate") || lower.contains("de-activate")
                    }

                    val hasUninstallAction = screenTexts.any {
                        val lower = it.lowercase(Locale.ROOT)
                        lower.contains("force stop") || lower.contains("kill") || lower.contains("clear data") || lower.contains("uninstall")
                    }

                    val hasAccessibilityAction = screenTexts.any {
                        val lower = it.lowercase(Locale.ROOT)
                        lower.contains("warden needs access to accessibility data") ||
                        lower.contains("stop warden") ||
                        lower.contains("turn off warden")
                    }

                    val isActivating = System.currentTimeMillis() - deviceAdminActivationRequestedAt < 180000

                    var shouldBlock = false
                    if (hasAppName) {
                        if (isDeviceAdminScreen) {
                            if (antiUninstallEnabled && hasDeactivateAction && !isActivating) {
                                shouldBlock = true
                            }
                        } else if (hasAccessibilityAction) {
                            shouldBlock = true
                        } else {
                            if (hasUninstallAction) {
                                shouldBlock = true
                            }
                        }
                    }

                    if (shouldBlock) {
                        service.pressHome()
                        cancelGracePeriod()
                        rootNode.recycle()
                        val msg = if (antiUninstallEnabled) {
                            "Self-protection: Cannot disable accessibility, force stop, clear data, deactivate admin, or uninstall Warden!"
                        } else {
                            "Self-protection: Cannot disable accessibility, force stop, clear data, or uninstall Warden!"
                        }
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                service,
                                msg,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return
                    }
                }
                rootNode.recycle()
            }
        }

        if (!isTurnedOn) return
        if (System.currentTimeMillis() < blockerDisabledUntil) {
            cancelGracePeriod()
            return
        }
        if (event == null || (event.eventType and TARGET_EVENTS_MASK) == 0) return

        val appPackage = event.packageName?.toString() ?: ""
        if (!service.isDelayOver(lastEventTimeStamp, refreshCooldown) || appPackage == "com.warden.app") {
            return
        }

        val tempExpiry = temporaryIgnoredApps[appPackage]
        val isIgnored = ignoredApps.contains(appPackage) || (tempExpiry != null && System.currentTimeMillis() < tempExpiry)

        if (isIgnored) {
            cancelGracePeriod()
            return
        }

        val rootNode = service.rootInActiveWindow ?: return
        if (rootNode.packageName?.toString() == "com.warden.app") {
            cancelGracePeriod()
            return
        }

        val detectedResult = scanNodeForBlockedKeyword(rootNode)

        if (detectedResult != null) {
            val detectedKeyword = detectedResult.first
            val contextString = detectedResult.second
            if (ignoreGracePeriodSeconds > 0) {
                if (delayedDetectedKeyword == detectedKeyword && delayedPackageName == appPackage) {
                    // Grace period already active, wait for task
                } else {
                    cancelGracePeriod()
                    delayedDetectedKeyword = detectedKeyword
                    delayedPackageName = appPackage
                    
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            service,
                            "Blocked word $detectedKeyword was found : \" $contextString \"",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    val runnable = Runnable {
                        val currentRoot = service.rootInActiveWindow
                        if (currentRoot != null && currentRoot.packageName?.toString() == delayedPackageName) {
                            val checkResult = scanNodeForBlockedKeyword(currentRoot)
                            if (checkResult?.first == delayedDetectedKeyword) {
                                pressHome(checkResult!!.first, checkResult!!.second)
                            }
                        }
                        delayedDetectedKeyword = null
                        delayedPackageName = null
                    }
                    pendingGraceMinimizeRunnable = runnable
                    handler.postDelayed(runnable, ignoreGracePeriodSeconds * 1000L)
                }
            } else {
                // ignoreGracePeriodSeconds is 0: block immediately!
                cancelGracePeriod()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        service,
                        "Blocked word $detectedKeyword was found : \" $contextString \"",
                        Toast.LENGTH_LONG
                    ).show()
                }
                pressHome(detectedKeyword, contextString)
            }
        } else {
            if (appPackage == delayedPackageName) {
                cancelGracePeriod()
            }
        }
    }

    private fun cancelGracePeriod() {
        pendingGraceMinimizeRunnable?.let {
            handler.removeCallbacks(it)
        }
        pendingGraceMinimizeRunnable = null
        delayedDetectedKeyword = null
        delayedPackageName = null
    }

    private fun scanNodeForBlockedKeyword(node: AccessibilityNodeInfo?): Pair<String, String>? {
        node ?: return null

        val nodeText = node.text?.toString()
        if (!nodeText.isNullOrEmpty()) {
            val foundResult = containsBlockedKeyword(nodeText)
            if (foundResult != null) return foundResult
        }

        val contentDesc = node.contentDescription?.toString()
        if (!contentDesc.isNullOrEmpty()) {
            val foundResult = containsBlockedKeyword(contentDesc)
            if (foundResult != null) return foundResult
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val foundResult = scanNodeForBlockedKeyword(child)
            if (foundResult != null) {
                return foundResult
            }
        }

        return null
    }

    private fun collectScreenTexts(node: AccessibilityNodeInfo?, targetList: MutableList<String>) {
        node ?: return
        val text = node.text?.toString()
        if (!text.isNullOrEmpty()) {
            targetList.add(text)
        }
        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrEmpty()) {
            targetList.add(desc)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectScreenTexts(child, targetList)
                child.recycle()
            }
        }
    }

    fun setupBlocker(service: BaseBlockingService) {
        this.service = service
        Log.d("Keyword Blocker", "Setting up kw blocker")
        CoroutineScope(Dispatchers.IO).launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                blockedKeyword = settings.keywordBlockerConfig.blockedKeywords.toHashSet()
                whitelistedKeywords = settings.keywordBlockerConfig.whitelistedKeywords.toHashSet()
                ignoredApps = settings.keywordBlockerConfig.ignoredApps.toHashSet()
                ignoreGracePeriodSeconds = settings.ignoreGracePeriodSeconds
                isTurnedOn = settings.keywordBlockerConfig.isActive
                blockerDisabledUntil = settings.blockerDisabledUntil
                temporaryIgnoredApps = settings.temporaryIgnoredApps
                antiUninstallEnabled = settings.antiUninstallEnabled
                deviceAdminActivationRequestedAt = settings.deviceAdminActivationRequestedAt
                
                val validKeywords = blockedKeyword.filter { it.isNotBlank() }.map { Regex.escape(it.lowercase(Locale.ROOT)) }
                if (validKeywords.isNotEmpty()) {
                    val sortedKeywords = validKeywords.sortedByDescending { it.length }
                    val keywordsPattern = sortedKeywords.joinToString("|")
                    val patternString = """(?<!\w)(?:$keywordsPattern)+(?!\w)"""
                    blockedPattern = Regex(patternString)
                } else {
                    blockedPattern = null
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun setupReceivers() {
        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_CONFIG)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
        } else {
            service.registerReceiver(refreshReceiver, filter)
        }
    }

    fun removeReceivers() {
        try {
            service.unregisterReceiver(refreshReceiver)
            cancelGracePeriod()
        } catch (_: Exception) {}
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                INTENT_ACTION_REFRESH_CONFIG -> setupBlocker(service)
            }
        }
    }
}