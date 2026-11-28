package com.warden.app.services

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.warden.app.core.di.AppContainerProvider
import com.warden.app.core.platform.CrashLogger
import com.warden.app.feature.blocker.service.AccessibilityNodeScanner
import com.warden.app.feature.blocker.service.BlockAlertPresenter
import com.warden.app.feature.blocker.service.BlockerCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * AccessibilityService entry point that feeds throttled, conflated window events to
 * [BlockerCoordinator] while minimizing background CPU, Binder IPC, and RAM footprint.
 *
 * Package and class name (`com.warden.app.services.WardenService`) are preserved verbatim so
 * upgrading retains the user's Accessibility Service permission grant.
 */
class WardenService : AccessibilityService() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val eventChannel =
        Channel<AccessibilityEvent>(Channel.CONFLATED) { dropped ->
            AccessibilityNodeScanner.recycleEventCompat(dropped)
        }

    private var alertPresenter: BlockAlertPresenter? = null
    private var coordinator: BlockerCoordinator? = null
    private var lastContentEventPackage: String? = null
    private var lastContentEventUptimeMs: Long = 0L

    @Volatile
    private var builtinDomainsLoaded: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (application as AppContainerProvider).container
        val presenter = BlockAlertPresenter(this)
        alertPresenter = presenter
        val activeCoordinator =
            BlockerCoordinator(
                rootNodeProvider = { rootInActiveWindow },
                pressHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
                showToast = presenter::show,
                dismissToast = presenter::dismiss,
            )
        coordinator = activeCoordinator

        // Observe settings directly from the process-shared repository; load the 46k built-in
        // domain set lazily ONLY if the built-in Adult Sites profile is actually enabled.
        serviceScope.launch {
            container.settingsRepository.settings.collectLatest { settings ->
                activeCoordinator.onSettingsUpdated(settings)
                if (!builtinDomainsLoaded && settings.websiteBlockerConfig.isBuiltinProfileEnabled()) {
                    builtinDomainsLoaded = true
                    val builtinSet = container.pornDomainRepository.loadBuiltinDomainSet()
                    activeCoordinator.onBuiltinDomainsLoaded(builtinSet)
                }
            }
        }
        startEventWorker(activeCoordinator, container.crashLogger)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val eventType = event.eventType
        if ((eventType and TARGET_EVENTS_MASK) == 0) return

        val eventPkg = event.packageName?.toString()
        if (eventPkg == packageName) return

        // Coalesce high-frequency content-changed bursts before allocating/copying AccessibilityEvent
        // or waking the background worker; window-state switches always pass immediately.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val nowUptime = SystemClock.uptimeMillis()
            val withinThrottle =
                eventPkg == lastContentEventPackage &&
                    (nowUptime - lastContentEventUptimeMs) < CONTENT_BURST_THROTTLE_MS
            if (withinThrottle) return
            lastContentEventPackage = eventPkg
            lastContentEventUptimeMs = nowUptime
        }

        val copy = AccessibilityNodeScanner.copyEventCompat(event)
        if (eventChannel.trySend(copy).isFailure) {
            AccessibilityNodeScanner.recycleEventCompat(copy)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        coordinator?.shutdown()
        coordinator = null
        alertPresenter?.shutdown()
        alertPresenter = null
        eventChannel.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startEventWorker(
        activeCoordinator: BlockerCoordinator,
        crashLogger: CrashLogger,
    ) {
        serviceScope.launch {
            for (event in eventChannel) {
                try {
                    activeCoordinator.onAccessibilityEvent(event)
                } catch (c: CancellationException) {
                    throw c
                } catch (e: IllegalStateException) {
                    crashLogger.logNonFatalError(e)
                } catch (e: SecurityException) {
                    crashLogger.logNonFatalError(e)
                } finally {
                    AccessibilityNodeScanner.recycleEventCompat(event)
                }
            }
        }
    }

    companion object {
        private const val CONTENT_BURST_THROTTLE_MS = 200L
        private const val TARGET_EVENTS_MASK =
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    }
}
