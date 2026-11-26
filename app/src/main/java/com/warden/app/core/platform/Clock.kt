package com.warden.app.core.platform

import android.os.SystemClock

/**
 * Abstraction over wall-clock and monotonic uptime clocks for deterministic testing.
 */
interface Clock {
    /** Current wall-clock time in epoch milliseconds. */
    fun currentTimeMillis(): Long

    /** Monotonic uptime in milliseconds since boot. */
    fun uptimeMillis(): Long
}

/**
 * Production [Clock] backed by [System.currentTimeMillis] and [SystemClock.uptimeMillis].
 */
class SystemClockProvider : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun uptimeMillis(): Long = SystemClock.uptimeMillis()
}
