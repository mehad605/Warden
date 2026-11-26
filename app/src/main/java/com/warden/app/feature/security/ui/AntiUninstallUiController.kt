package com.warden.app.feature.security.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.warden.app.R
import com.warden.app.core.platform.DeviceAdminController

/**
 * Coordinates Anti-Uninstall switch interactions with [DeviceAdminController] and
 * [PasswordDialogController].
 */
class AntiUninstallUiController(
    private val context: Context,
    private val deviceAdminController: DeviceAdminController,
    private val passwordDialogs: PasswordDialogController,
    private val onRequestAdminActivation: (Intent) -> Unit,
    private val onSetEnabled: (Boolean) -> Unit,
    private val onSyncSwitchVisual: (Boolean) -> Unit,
) {
    /** Synchronizes persisted anti-uninstall state with OS Device Admin active status. */
    fun syncWithSystemState(persistedEnabled: Boolean) {
        val isActive = deviceAdminController.isAdminActive()
        if (isActive != persistedEnabled) {
            onSetEnabled(isActive)
        }
    }

    /** Handles user toggle events on the Anti-Uninstall switch. */
    fun onToggleRequested(
        desiredChecked: Boolean,
        hasPassword: Boolean,
        onRecordActivationRequest: () -> Unit,
    ) {
        if (desiredChecked) {
            handleEnableRequest(onRecordActivationRequest)
        } else {
            handleDisableRequest(hasPassword)
        }
    }

    private fun handleEnableRequest(onRecordActivationRequest: () -> Unit) {
        if (deviceAdminController.isAdminActive()) {
            onSetEnabled(true)
            return
        }
        onSyncSwitchVisual(false)
        onRecordActivationRequest()
        val intent =
            deviceAdminController.buildActivationIntent(
                context.getString(R.string.anti_uninstall_admin_explanation),
            )
        onRequestAdminActivation(intent)
    }

    private fun handleDisableRequest(hasPassword: Boolean) {
        onSyncSwitchVisual(true)
        if (hasPassword) {
            passwordDialogs.showVerifyDialog(
                title = context.getString(R.string.disable_anti_uninstall_title),
                message = context.getString(R.string.disable_anti_uninstall_password_msg),
                onVerified = ::deactivateAdmin,
            )
        } else {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.disable_anti_uninstall_title)
                .setMessage(R.string.disable_anti_uninstall_confirm_msg)
                .setPositiveButton(R.string.disable) { dialog, _ ->
                    deactivateAdmin()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun deactivateAdmin() {
        try {
            deviceAdminController.removeActiveAdmin()
            onSetEnabled(false)
            onSyncSwitchVisual(false)
            Toast.makeText(context, R.string.anti_uninstall_disabled, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(
                context,
                context.getString(R.string.anti_uninstall_disable_error, e.message.orEmpty()),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
