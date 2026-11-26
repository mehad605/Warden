package com.warden.app.services

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.warden.app.core.di.AppContainerProvider
import com.warden.app.core.platform.CrashLogger
import com.warden.app.feature.blocker.service.AccessibilityNodeScanner
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
 * AccessibilityService entry point that feeds conflated window events to [BlockerCoordinator].
 *
 * Package and class name (`com.warden.app.services.WardenService`) are preserved verbatim so
 * upgrading from `baseline-v1` retains the user's Accessibility Service permission grant.
 */
class WardenService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val eventChannel =
        Channel<AccessibilityEvent>(Channel.CONFLATED) { dropped ->
            AccessibilityNodeScanner.recycleEventCompat(dropped)
        }

    private var coordinator: BlockerCoordinator? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (application as AppContainerProvider).container
        val activeCoordinator =
            BlockerCoordinator(
                rootNodeProvider = { rootInActiveWindow },
                pressHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
                showToast = ::showMainThreadToast,
            )
        coordinator = activeCoordinator

        // Observe settings directly from the process-shared repository (no broadcast receiver needed).
        serviceScope.launch {
            container.settingsRepository.settings.collectLatest { settings ->
                activeCoordinator.onSettingsUpdated(settings)
            }
        }
        startEventWorker(activeCoordinator, container.crashLogger)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val copy = AccessibilityNodeScanner.copyEventCompat(event)
        if (eventChannel.trySend(copy).isFailure) {
            AccessibilityNodeScanner.recycleEventCompat(copy)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        coordinator?.shutdown()
        coordinator = null
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

    private fun showMainThreadToast(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}
