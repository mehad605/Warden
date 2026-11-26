package com.warden.app.feature.blocker.ui

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.warden.app.R
import com.warden.app.data.models.Settings
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import com.warden.app.feature.security.ui.AntiUninstallUiController
import com.warden.app.feature.security.ui.PasswordDialogController

/**
 * Binds the Security & Data dashboard cards: password status/buttons, anti-uninstall switch,
 * and backup export/import actions.
 */
internal class SecurityAndBackupSectionBinder(
    private val context: Context,
    private val binding: FragmentKeywordBlockerBinding,
    private val hasPassword: () -> Boolean,
    private val passwordDialogs: PasswordDialogController,
    private val antiUninstallController: AntiUninstallUiController,
    private val isUpdatingUi: () -> Boolean,
    private val onRecordAdminActivation: () -> Unit,
    private val onExportClicked: () -> Unit,
    private val onImportClicked: () -> Unit,
) {
    /** Wires anti-uninstall switch, password buttons, and backup export/import buttons. */
    fun bindListeners() {
        val vault = binding.tabVault
        vault.switchAntiUninstall.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUi()) return@setOnCheckedChangeListener
            antiUninstallController.onToggleRequested(
                desiredChecked = isChecked,
                hasPassword = hasPassword(),
                onRecordActivationRequest = onRecordAdminActivation,
            )
        }
        vault.btnSetPassword.setOnClickListener {
            passwordDialogs.showSetOrChangeDialog(hasPassword())
        }
        vault.btnRemovePassword.setOnClickListener { passwordDialogs.showRemoveDialog() }
        vault.btnExportSettings.setOnClickListener { onExportClicked() }
        vault.btnImportSettings.setOnClickListener { onImportClicked() }
    }

    /** Renders password protection status badge and buttons from [settings]. */
    fun render(settings: Settings) {
        val vault = binding.tabVault
        if (settings.passwordHash != null) {
            vault.tvPasswordStatus.setText(R.string.password_required_status)
            vault.tvPasswordBadge.setText(R.string.status_on)
            vault.tvPasswordBadge.setTextColor(ContextCompat.getColor(context, R.color.status_active))
            vault.tvPasswordBadge.visibility = View.VISIBLE
            vault.btnSetPassword.setText(R.string.change_password)
            vault.btnRemovePassword.visibility = View.VISIBLE
        } else {
            vault.tvPasswordStatus.setText(R.string.no_password_set)
            vault.tvPasswordBadge.visibility = View.GONE
            vault.btnSetPassword.setText(R.string.set_password)
            vault.btnRemovePassword.visibility = View.GONE
        }
    }
}
