package com.warden.app.feature.blocker.domain

import java.util.Locale

/**
 * Display model for one row in the Active Ignored Apps list.
 */
data class IgnoredAppRow(
    val packageName: String,
    val expiryMs: Long?
) {
    /** `true` when this app has a finite expiration timestamp rather than a permanent exemption. */
    val isTemporary: Boolean get() = expiryMs != null
}

/**
 * Pure domain helpers for computing active ignored apps, pruning expired entries,
 * and formatting countdown labels.
 */
object IgnoredAppsFormatter {

    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MS_PER_MINUTE = MS_PER_SECOND * SECONDS_PER_MINUTE

    /** Combines [permanentApps] and unexpired [temporaryApps] into distinct display rows. */
    fun buildActiveRows(
        permanentApps: List<String>,
        temporaryApps: Map<String, Long>,
        nowMs: Long
    ): List<IgnoredAppRow> {
        val activeTemp = temporaryApps.filterValues { it > nowMs }
        val allPackages = (permanentApps + activeTemp.keys).distinct()
        return allPackages.map { pkg -> IgnoredAppRow(packageName = pkg, expiryMs = activeTemp[pkg]) }
    }

    /** Returns a filtered map excluding expired entries, or `null` if nothing expired. */
    fun pruneExpired(temporaryApps: Map<String, Long>, nowMs: Long): Map<String, Long>? {
        val hasExpired = temporaryApps.values.any { nowMs >= it }
        if (!hasExpired) return null
        return temporaryApps.filterValues { nowMs < it }
    }

    /** Formats remaining time from [nowMs] until [expiryMs] as `"%dm %02ds"` or `"0m 00s"`. */
    fun formatRemaining(expiryMs: Long, nowMs: Long): String {
        val remainingMs = expiryMs - nowMs
        if (remainingMs <= 0L) return "0m 00s"
        val remainingSeconds = (remainingMs / MS_PER_SECOND) % SECONDS_PER_MINUTE
        val remainingMinutes = remainingMs / MS_PER_MINUTE
        return String.format(Locale.ROOT, "%dm %02ds", remainingMinutes, remainingSeconds)
    }

    /** Computes remaining whole minutes (`>= 1` while still active) for the decrease-time slider. */
    fun remainingMinutesForSlider(expiryMs: Long, nowMs: Long): Pair<Int, Int>? {
        val remainingMs = expiryMs - nowMs
        if (remainingMs <= 0L) return null
        val remainingMinutes = (remainingMs / MS_PER_MINUTE).toInt()
        val maxMinutes = remainingMinutes.coerceAtLeast(1)
        return remainingMinutes to maxMinutes
    }
}
