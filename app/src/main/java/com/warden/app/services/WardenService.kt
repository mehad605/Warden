package com.warden.app.services

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import com.warden.app.CrashLogger
import com.warden.app.blockers.KeywordBlocker

class WardenService : BaseBlockingService() {

    private var keywordBlocker = KeywordBlocker()

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val eventChannel = Channel<AccessibilityEvent>(Channel.CONFLATED) { droppedEvent ->
        droppedEvent.recycle()
    }

    private lateinit var crashLogger: CrashLogger

    override fun onCreate() {
        super.onCreate()
        crashLogger = CrashLogger(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        super.onAccessibilityEvent(event)

        val eventCopy = AccessibilityEvent.obtain(event)
        val result = eventChannel.trySend(eventCopy)

        if (result.isFailure) {
            eventCopy.recycle()
        }
    }

    override fun onInterrupt() {
    }

    private fun startBackgroundWorker() {
        serviceScope.launch {
            for (event in eventChannel) {
                try {
                    keywordBlocker.checkIfUserGettingFreaky(event)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    crashLogger.logNonFatalError(Exception(t))
                    Log.e("BlockWords", "Background worker error", t)
                } finally {
                    event.recycle()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        keywordBlocker.setupBlocker(this)
        keywordBlocker.setupReceivers()
        startBackgroundWorker()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            keywordBlocker.removeReceivers()
            eventChannel.close()
            serviceScope.cancel()
        } catch (_: Exception) {}
    }
}