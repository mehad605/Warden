package com.warden.app.feature.gatekeeper.ui

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.warden.app.R
import com.warden.app.core.platform.LaunchableApp
import com.warden.app.databinding.DialogAiChatBinding
import com.warden.app.feature.gatekeeper.domain.GatekeeperVerdict

/**
 * Pure view-state helper for [DialogAiChatBinding]: configures headers, spinner state,
 * loading indicator, and verdict styling for each [GatekeeperDialogMode].
 */
internal class GatekeeperDialogViewBinder(
    private val context: Context,
    private val binding: DialogAiChatBinding,
) {
    /** Configures titles, hints, and field visibility for [mode]. */
    fun bindModeHeader(
        mode: GatekeeperDialogMode,
        permanentIgnoredApps: List<String>,
        targetAppLabel: String?,
    ) {
        when (mode) {
            GatekeeperDialogMode.TEMPORARY_IGNORE -> bindTempIgnoreSpinnerListener(permanentIgnoredApps)
            GatekeeperDialogMode.PAUSE_BLOCKER -> {
                binding.tvDialogTitle.setText(R.string.ai_pause_title)
                binding.tvDialogSubtitle.setText(R.string.ai_pause_subtitle)
                binding.tvSelectAppLabel.visibility = View.GONE
                binding.spinnerApps.visibility = View.GONE
                binding.layoutAttachment.visibility = View.VISIBLE
                binding.tilJustification.hint = context.getString(R.string.ai_justification_hint)
            }
            GatekeeperDialogMode.EXTEND_IGNORE -> {
                val label = targetAppLabel ?: context.getString(R.string.ai_default_app_label)
                binding.tvDialogTitle.setText(R.string.ai_extend_title)
                binding.tvDialogSubtitle.text = context.getString(R.string.ai_extend_subtitle, label)
                binding.tvSelectAppLabel.visibility = View.VISIBLE
                binding.spinnerApps.visibility = View.VISIBLE
                binding.layoutAttachment.visibility = View.VISIBLE
                binding.tilJustification.hint = context.getString(R.string.ai_justification_hint)
            }
        }
    }

    /** Shows a placeholder spinner state while installed apps load off the main thread. */
    fun showAppsLoading() {
        binding.spinnerApps.adapter =
            ArrayAdapter(
                context,
                android.R.layout.simple_spinner_item,
                listOf(context.getString(R.string.loading_installed_apps)),
            )
        binding.spinnerApps.isEnabled = false
        binding.btnSubmit.isEnabled = false
    }

    /** Populates the app spinner and locks selection when [targetPackage] is specified. */
    fun bindLoadedApps(
        apps: List<LaunchableApp>,
        targetPackage: String?,
    ) {
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, apps)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerApps.adapter = adapter
        if (targetPackage != null) {
            val index = apps.indexOfFirst { it.packageName == targetPackage }
            if (index >= 0) binding.spinnerApps.setSelection(index)
            binding.spinnerApps.isEnabled = false
        } else {
            binding.spinnerApps.isEnabled = true
        }
        binding.btnSubmit.isEnabled = true
    }

    /** Toggles loading indicator and disables action buttons while an evaluation is in flight. */
    fun setLoading(isLoading: Boolean) {
        binding.layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled = !isLoading
        binding.btnCancel.isEnabled = !isLoading
    }

    /** Renders the approved/rejected verdict card and auto-dismisses on approval. */
    fun showVerdict(
        dialog: AlertDialog,
        verdict: GatekeeperVerdict,
        onApprovedRendered: () -> Unit,
        onRejectedRendered: () -> Unit,
    ) {
        binding.layoutLoading.visibility = View.GONE
        binding.cardAiVerdict.visibility = View.VISIBLE
        binding.tvVerdictMessage.text = verdict.botResponse

        if (verdict.approved) {
            binding.tvVerdictTitle.setText(R.string.verdict_approved)
            binding.tvVerdictTitle.setTextColor(context.resources.getColor(android.R.color.holo_green_dark, null))
            onApprovedRendered()
            binding.btnSubmit.postDelayed({ if (dialog.isShowing) dialog.dismiss() }, AUTO_DISMISS_DELAY_MS)
        } else {
            binding.tvVerdictTitle.setText(R.string.verdict_rejected)
            binding.tvVerdictTitle.setTextColor(context.resources.getColor(android.R.color.holo_red_dark, null))
            onRejectedRendered()
            binding.btnSubmit.isEnabled = true
            binding.btnCancel.isEnabled = true
        }
    }

    private fun bindTempIgnoreSpinnerListener(permanentIgnoredApps: List<String>) {
        binding.tvDialogTitle.setText(R.string.ai_ignore_title)
        binding.tvDialogSubtitle.setText(R.string.ai_ignore_subtitle)
        binding.spinnerApps.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    val selected = binding.spinnerApps.selectedItem as? LaunchableApp
                    if (selected == null) {
                        binding.layoutAttachment.visibility = View.GONE
                        return
                    }
                    applySelectionCopy(selected.label, permanentIgnoredApps.contains(selected.packageName))
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    private fun applySelectionCopy(
        appLabel: String,
        isAlreadyIgnored: Boolean,
    ) {
        if (isAlreadyIgnored) {
            binding.tvDialogTitle.setText(R.string.ai_remove_ignore_title)
            binding.tvDialogSubtitle.text = context.getString(R.string.ai_remove_ignore_subtitle, appLabel)
            binding.layoutAttachment.visibility = View.GONE
            binding.tilJustification.hint = context.getString(R.string.ai_removal_justification_hint)
        } else {
            binding.tvDialogTitle.setText(R.string.ai_ignore_title)
            binding.tvDialogSubtitle.text = context.getString(R.string.ai_ignore_app_subtitle, appLabel)
            binding.layoutAttachment.visibility = View.VISIBLE
            binding.tilJustification.hint = context.getString(R.string.ai_justification_hint)
        }
    }

    companion object {
        private const val AUTO_DISMISS_DELAY_MS = 3_000L
    }
}
