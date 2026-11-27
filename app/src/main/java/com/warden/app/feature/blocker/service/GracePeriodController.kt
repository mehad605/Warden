package com.warden.app.feature.blocker.service

import android.os.Handler
import android.os.Looper

/**
 * Coordinates delayed re-verification and cancellation for the keyword-blocker grace period.
 *
 * Ensures at most one pending minimization timer exists at a time and prevents duplicate timers
 * when the same `(packageName, keyword)` remains visible across rapid events.
 */
class GracePeriodController(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private var pendingRunnable: Runnable? = null
    private var pendingKeyword: String? = null
    private var pendingPackage: String? = null

    /**
     * Schedules [onElapsed] after [graceSeconds] unless an identical `(packageName, keyword)`
     * grace timer is already pending.
     *
     * @return `true` if a new timer was scheduled (caller should show the initial toast),
     *   `false` if an identical timer was already running.
     */
    fun scheduleIfNew(
        packageName: String,
        keyword: String,
        graceSeconds: Int,
        onElapsed: () -> Unit,
    ): Boolean {
        if (pendingKeyword == keyword && pendingPackage == packageName) {
            return false
        }
        cancel()
        pendingKeyword = keyword
        pendingPackage = packageName
        val runnable =
            Runnable {
                pendingRunnable = null
                pendingKeyword = null
                pendingPackage = null
                onElapsed()
            }
        pendingRunnable = runnable
        handler.postDelayed(runnable, graceSeconds * 1_000L)
        return true
    }

    /**
     * Cancels any pending grace timer if it belongs to [packageName].
     *
     * @return `true` if a pending timer for [packageName] was cancelled.
     */
    fun cancelForPackage(packageName: String): Boolean {
        if (pendingPackage == packageName) {
            cancel()
            return true
        }
        return false
    }

    /** Immediately cancels any pending grace timer. */
    fun cancel() {
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
        pendingKeyword = null
        pendingPackage = null
    }
}
