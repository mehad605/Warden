package com.warden.app.feature.blocker.ui

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.warden.app.R
import com.warden.app.data.models.WebsiteBlockProfile
import com.warden.app.databinding.ItemWebsiteProfileRowBinding
import java.text.NumberFormat
import java.util.Locale

/** Callbacks passed into [WebsiteProfilesOverviewBinder.renderOverview] for row interactions. */
internal data class ProfileRowCallbacks(
    val isSwitchMuted: () -> Boolean,
    val syncSwitch: (MaterialSwitch, Boolean) -> Unit,
    val onToggleProfile: (profileId: String, enabled: Boolean, revert: () -> Unit) -> Unit,
    val onOpenProfile: (profileId: String) -> Unit,
)

/**
 * Inflates and formats profile cards inside the Website Lists overview container, wires the
 * domain add input, and builds localized site count summaries.
 */
internal object WebsiteProfilesOverviewBinder {
    /** Populates [container] with interactive profile cards and ON/OFF switches. */
    internal fun renderOverview(
        context: Context,
        container: LinearLayout,
        profiles: List<WebsiteBlockProfile>,
        builtinCount: Int,
        callbacks: ProfileRowCallbacks,
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)
        profiles.forEach { profile ->
            val row = ItemWebsiteProfileRowBinding.inflate(inflater, container, false)
            row.tvProfileName.text = profile.name
            row.tvProfileBuiltinBadge.visibility = if (profile.isBuiltin) View.VISIBLE else View.GONE
            row.tvProfileSummary.text = formatProfileSummary(context, profile, builtinCount)
            row.switchProfileEnabled.contentDescription = profile.name
            row.switchProfileEnabled.isChecked = profile.isEnabled
            row.switchProfileEnabled.setOnCheckedChangeListener { _, isChecked ->
                if (callbacks.isSwitchMuted()) return@setOnCheckedChangeListener
                callbacks.onToggleProfile(profile.id, isChecked) {
                    callbacks.syncSwitch(row.switchProfileEnabled, true)
                }
            }
            row.root.setOnClickListener { callbacks.onOpenProfile(profile.id) }
            container.addView(row.root)
        }
    }

    /** Wires end-icon, IME Done, and hardware Enter submission on the add-domain text field. */
    internal fun bindDomainInput(
        inputLayout: TextInputLayout,
        editText: TextInputEditText,
        onSubmit: () -> Unit,
    ) {
        inputLayout.setEndIconOnClickListener { onSubmit() }
        editText.doAfterTextChanged { inputLayout.error = null }
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onSubmit()
                true
            } else {
                false
            }
        }
        editText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && (event == null || event.action == KeyEvent.ACTION_UP)) {
                onSubmit()
                true
            } else {
                false
            }
        }
    }

    /** Formats the concise site-count and active-status subtitle shown on a profile card. */
    internal fun formatProfileSummary(
        context: Context,
        profile: WebsiteBlockProfile,
        builtinCount: Int,
    ): String {
        val activeBuiltins = if (profile.isBuiltin) (builtinCount - profile.disabledBuiltinDomains.size).coerceAtLeast(0) else 0
        val total = activeBuiltins + profile.domains.size
        val formatted = NumberFormat.getIntegerInstance(Locale.getDefault()).format(total)
        val stateLabel = context.getString(if (profile.isEnabled) R.string.profile_state_on else R.string.profile_state_off)
        return context.resources.getQuantityString(R.plurals.website_profile_summary_fmt, total, formatted, stateLabel)
    }

    /** Formats the stats header shown inside a selected profile's detail view. */
    internal fun formatDetailStats(
        context: Context,
        profile: WebsiteBlockProfile,
        builtinCount: Int,
    ): String {
        val nf = NumberFormat.getIntegerInstance(Locale.getDefault())
        return if (profile.isBuiltin) {
            val activeBuiltins = (builtinCount - profile.disabledBuiltinDomains.size).coerceAtLeast(0)
            val total = activeBuiltins + profile.domains.size
            context.getString(
                R.string.website_builtin_stats_fmt,
                nf.format(total),
                nf.format(activeBuiltins),
                nf.format(profile.domains.size),
            )
        } else {
            context.resources.getQuantityString(
                R.plurals.website_custom_stats_fmt,
                profile.domains.size,
                nf.format(profile.domains.size),
            )
        }
    }

    /** Displays a 5-second Undo [Snackbar] anchored cleanly above the bottom navigation bar. */
    internal fun showUndoSnackbar(
        root: View,
        anchor: View,
        message: String,
        onUndo: () -> Unit,
    ) {
        Snackbar.make(root, message, UNDO_DURATION_MS)
            .setAnchorView(anchor)
            .setAction(R.string.undo_action) { onUndo() }
            .show()
    }

    private const val UNDO_DURATION_MS = 5_000
}
