package com.warden.app.feature.gatekeeper.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.warden.app.R
import com.warden.app.core.platform.LaunchableApp
import com.warden.app.databinding.DialogAppPickerBinding
import java.util.Locale

/**
 * Lightweight searchable Material 3 dialog for picking a target app from a pre-loaded
 * [LaunchableApp] list without Spinner rendering glitches.
 */
internal object AppPickerDialog {
    /** Shows a searchable picker over [apps] and delivers the chosen app to [onSelected]. */
    fun show(
        context: Context,
        layoutInflater: LayoutInflater,
        apps: List<LaunchableApp>,
        onSelected: (LaunchableApp) -> Unit,
    ) {
        val binding = DialogAppPickerBinding.inflate(layoutInflater)
        val filtered = apps.toMutableList()
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, filtered)
        binding.listApps.adapter = adapter

        val dialog =
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.select_target_app)
                .setView(binding.root)
                .setNegativeButton(R.string.cancel, null)
                .create()

        binding.etAppSearch.doAfterTextChanged { query ->
            val needle = query?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            filtered.clear()
            if (needle.isEmpty()) {
                filtered.addAll(apps)
            } else {
                filtered.addAll(
                    apps.filter {
                        it.label.lowercase(Locale.ROOT).contains(needle) ||
                            it.packageName.lowercase(Locale.ROOT).contains(needle)
                    },
                )
            }
            adapter.notifyDataSetChanged()
        }

        binding.listApps.setOnItemClickListener { _, _, position, _ ->
            if (position in filtered.indices) {
                onSelected(filtered[position])
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
