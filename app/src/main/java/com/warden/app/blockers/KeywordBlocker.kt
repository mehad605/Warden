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

    private fun containsBlockedKeyword(text: String): String? {
        val lowerText = text.lowercase(Locale.ROOT)
        for (keyword in blockedKeyword) {
            val lowerKeyword = keyword.lowercase(Locale.ROOT)
            if (lowerKeyword.isNotBlank() && lowerText.contains(lowerKeyword)) {
                return keyword
            }
        }
        return null
    }

    fun checkIfUserGettingFreaky(event: AccessibilityEvent?) {
        fun showMessage(word: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    service,
                    service.getString(R.string.blocked_keyword_word_was_found).replace("-word", word),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        fun pressHome(word: String) {
            showMessage(word)
            service.pressHome()
        }

        // Self-Protection check: Prevent clearing data, force stop, or uninstalling
        if (event != null && (event.eventType and TARGET_EVENTS_MASK) != 0) {
            val rootNode = service.rootInActiveWindow
            if (rootNode != null) {
                val appPackage = rootNode.packageName?.toString() ?: ""
                if (appPackage != "com.warden.app") {
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

                    val isActivating = System.currentTimeMillis() - deviceAdminActivationRequestedAt < 180000

                    var shouldBlock = false
                    if (hasAppName) {
                        if (isDeviceAdminScreen) {
                            if (antiUninstallEnabled && hasDeactivateAction && !isActivating) {
                                shouldBlock = true
                            }
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
                            "Self-protection: Cannot force stop, clear data, deactivate admin, or uninstall Warden!"
                        } else {
                            "Self-protection: Cannot force stop, clear data, or uninstall Warden!"
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

        val detectedKeyword = scanNodeForBlockedKeyword(rootNode)

        if (detectedKeyword != null) {
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
                            "Blocked word: \"$detectedKeyword\" was detected",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    val runnable = Runnable {
                        val currentRoot = service.rootInActiveWindow
                        if (currentRoot != null && currentRoot.packageName?.toString() == delayedPackageName) {
                            val checkKeyword = scanNodeForBlockedKeyword(currentRoot)
                            if (checkKeyword == delayedDetectedKeyword) {
                                pressHome(checkKeyword!!)
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
                        "Blocked word: \"$detectedKeyword\" was detected",
                        Toast.LENGTH_LONG
                    ).show()
                }
                pressHome(detectedKeyword)
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

    private fun scanNodeForBlockedKeyword(node: AccessibilityNodeInfo?): String? {
        node ?: return null

        val nodeText = node.text?.toString()
        if (!nodeText.isNullOrEmpty()) {
            val foundWord = containsBlockedKeyword(nodeText)
            if (foundWord != null) return foundWord
        }

        val contentDesc = node.contentDescription?.toString()
        if (!contentDesc.isNullOrEmpty()) {
            val foundWord = containsBlockedKeyword(contentDesc)
            if (foundWord != null) return foundWord
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val foundWord = scanNodeForBlockedKeyword(child)
            if (foundWord != null) {
                return foundWord
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
                ignoredApps = settings.keywordBlockerConfig.ignoredApps.toHashSet()
                ignoreGracePeriodSeconds = settings.ignoreGracePeriodSeconds
                isTurnedOn = settings.keywordBlockerConfig.isActive
                blockerDisabledUntil = settings.blockerDisabledUntil
                temporaryIgnoredApps = settings.temporaryIgnoredApps
                antiUninstallEnabled = settings.antiUninstallEnabled
                deviceAdminActivationRequestedAt = settings.deviceAdminActivationRequestedAt
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