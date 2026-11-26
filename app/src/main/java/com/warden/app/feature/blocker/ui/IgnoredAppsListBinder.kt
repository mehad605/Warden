package com.warden.app.feature.blocker.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.TextViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.warden.app.R
import com.warden.app.core.platform.Clock
import com.warden.app.core.platform.InstalledAppsProvider
import com.warden.app.databinding.ItemIgnoredAppBinding
import com.warden.app.feature.blocker.domain.IgnoredAppRow
import com.warden.app.feature.blocker.domain.IgnoredAppsFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Binds active permanent and temporary ignored apps, runs an event-driven 1-second countdown
 * ONLY while temporary exemptions exist (zero idle polling), and shows the decrease-time dialog.
 */
class IgnoredAppsListBinder(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val scope: CoroutineScope,
    private val cardContainer: View,
    private val listContainer: LinearLayout,
    private val appsProvider: InstalledAppsProvider,
    private val clock: Clock,
    private val onRequestExtend: (packageName: String) -> Unit,
    private val onDecreaseDuration: (packageName: String, newMinutes: Int) -> Unit,
    private val onPruneExpired: () -> Unit
) {

    private var countdownJob: Job? = null
    private var latestTempApps: Map<String, Long> = emptyMap()

    /** Renders [permanentApps] and unexpired [temporaryApps] and toggles the countdown ticker. */
    fun render(permanentApps: List<String>, temporaryApps: Map<String, Long>) {
        latestTempApps = temporaryApps
        val nowMs = clock.currentTimeMillis()
        val rows = IgnoredAppsFormatter.buildActiveRows(permanentApps, temporaryApps, nowMs)

        if (rows.isEmpty()) {
            cardContainer.visibility = View.GONE
            listContainer.removeAllViews()
            stopCountdown()
            return
        }

        cardContainer.visibility = View.VISIBLE
        listContainer.removeAllViews()
        rows.forEach { row -> listContainer.addView(buildRowView(row, nowMs)) }

        if (rows.any { it.isTemporary }) {
            startCountdownIfNeeded()
        } else {
            stopCountdown()
        }
    }

    /** Starts the 1-second ticker on resume if at least one temporary exemption is active. */
    fun onResume() {
        val nowMs = clock.currentTimeMillis()
        if (latestTempApps.values.any { it > nowMs }) {
            startCountdownIfNeeded()
        }
    }

    /** Stops the countdown ticker on pause or view teardown so background CPU stays zero. */
    fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun startCountdownIfNeeded() {
        if (countdownJob?.isActive == true) return
        countdownJob = scope.launch {
            while (isActive) {
                val nowMs = clock.currentTimeMillis()
                if (IgnoredAppsFormatter.pruneExpired(latestTempApps, nowMs) != null) {
                    onPruneExpired()
                }
                refreshCountdownTexts(nowMs)
                if (latestTempApps.values.none { it > nowMs }) {
                    break
                }
                delay(TICK_INTERVAL_MS)
            }
            countdownJob = null
        }
    }

    private fun buildRowView(row: IgnoredAppRow, nowMs: Long): View {
        val itemBinding = ItemIgnoredAppBinding.inflate(layoutInflater, listContainer, false)
        itemBinding.root.tag = row.packageName
        itemBinding.tvAppName.text = appsProvider.cachedLabel(row.packageName) ?: row.packageName
        itemBinding.tvPackageName.text = row.packageName

        scope.launch {
            val resolved = appsProvider.resolveAppInfo(row.packageName)
            if (itemBinding.root.tag == row.packageName) {
                itemBinding.tvAppName.text = resolved.label
                itemBinding.ivAppIcon.setImageDrawable(resolved.icon)
            }
        }

        bindDurationControls(itemBinding, row, nowMs)
        return itemBinding.root
    }

    private fun bindDurationControls(
        itemBinding: ItemIgnoredAppBinding,
        row: IgnoredAppRow,
        nowMs: Long
    ) {
        val expiryMs = row.expiryMs
        if (expiryMs != null) {
            itemBinding.tvRemainingTime.text = IgnoredAppsFormatter.formatRemaining(expiryMs, nowMs)
            itemBinding.tvRemainingTime.setOnClickListener {
                val label = appsProvider.cachedLabel(row.packageName) ?: row.packageName
                showDecreaseTimeDialog(row.packageName, label, expiryMs)
            }
            itemBinding.btnExtendTime.visibility = View.VISIBLE
            itemBinding.btnExtendTime.setOnClickListener { onRequestExtend(row.packageName) }
        } else {
            itemBinding.tvRemainingTime.setText(R.string.permanent_label)
            itemBinding.tvRemainingTime.isClickable = false
            itemBinding.tvRemainingTime.isFocusable = false
            itemBinding.btnExtendTime.visibility = View.GONE
        }
    }

    private fun refreshCountdownTexts(nowMs: Long) {
        for (i in 0 until listContainer.childCount) {
            val child = listContainer.getChildAt(i)
            val pkg = child.tag as? String ?: continue
            val tvRemaining = child.findViewById<TextView>(R.id.tv_remaining_time) ?: continue
            val expiry = latestTempApps[pkg]
            if (expiry != null) {
                tvRemaining.text = IgnoredAppsFormatter.formatRemaining(expiry, nowMs)
            } else {
                tvRemaining.setText(R.string.permanent_label)
            }
        }
    }

    private fun showDecreaseTimeDialog(packageName: String, appLabel: String, expiryMs: Long) {
        val bounds = IgnoredAppsFormatter.remainingMinutesForSlider(expiryMs, clock.currentTimeMillis()) ?: return
        val (remainingMinutes, maxMinutes) = bounds

        val tvValue = TextView(context).apply {
            text = context.getString(R.string.new_duration_minutes, remainingMinutes)
            TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 0, 0, VALUE_BOTTOM_PADDING)
        }
        val slider = Slider(context).apply {
            valueFrom = 0.0f
            valueTo = maxMinutes.toFloat()
            value = remainingMinutes.toFloat()
            stepSize = 1.0f
            addOnChangeListener { _, value, _ ->
                tvValue.text = context.getString(R.string.new_duration_minutes, value.toInt())
            }
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DIALOG_PADDING_H, DIALOG_PADDING_V, DIALOG_PADDING_H, DIALOG_PADDING_V)
            addView(tvValue)
            addView(slider)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.decrease_ignore_title)
            .setMessage(context.getString(R.string.decrease_ignore_message, appLabel))
            .setView(container)
            .setPositiveButton(R.string.apply_action) { _, _ ->
                onDecreaseDuration(packageName, slider.value.toInt())
                Toast.makeText(context, R.string.duration_updated, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val TICK_INTERVAL_MS = 1_000L
        private const val DIALOG_PADDING_H = 24
        private const val DIALOG_PADDING_V = 16
        private const val VALUE_BOTTOM_PADDING = 8
    }
}
