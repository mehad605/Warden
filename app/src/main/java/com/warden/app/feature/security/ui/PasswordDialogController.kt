package com.warden.app.feature.security.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.warden.app.R
import com.warden.app.feature.security.domain.PasswordPolicy

/**
 * Presents password verification, creation/change, and removal dialogs backed by [PasswordPolicy].
 */
class PasswordDialogController(
    private val context: Context,
    private val verifyPassword: (String) -> Boolean,
    private val onSetPassword: (String) -> Unit,
    private val onRemovePassword: () -> Unit
) {

    /** Prompts for the current password and invokes [onVerified] only when it matches. */
    fun showVerifyDialog(title: String, message: String, onVerified: () -> Unit) {
        val (inputLayout, editText) = createPasswordInput(
            hint = context.getString(R.string.enter_password),
            helperText = null
        )
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(inputLayout)
            .setPositiveButton(R.string.proceed) { dialog, _ ->
                if (verifyPassword(editText.text?.toString().orEmpty())) {
                    onVerified()
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, R.string.incorrect_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    /** Starts the set-or-change password flow, verifying the existing password first if present. */
    fun showSetOrChangeDialog(hasExistingPassword: Boolean) {
        if (hasExistingPassword) {
            showVerifyDialog(
                title = context.getString(R.string.verify_old_password_title),
                message = context.getString(R.string.verify_old_password_message),
                onVerified = ::showNewPasswordDialog
            )
        } else {
            showNewPasswordDialog()
        }
    }

    /** Prompts for the current password before removing password protection. */
    fun showRemoveDialog() {
        showVerifyDialog(
            title = context.getString(R.string.remove_password_title),
            message = context.getString(R.string.remove_password_message)
        ) {
            onRemovePassword()
            Toast.makeText(context, R.string.password_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNewPasswordDialog() {
        val (inputLayout, editText) = createPasswordInput(
            hint = context.getString(R.string.password_hint),
            helperText = context.getString(R.string.strong_password_rules)
        )
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.set_password)
            .setView(inputLayout)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val candidate = editText.text?.toString().orEmpty()
                if (PasswordPolicy.isStrong(candidate)) {
                    onSetPassword(candidate)
                    Toast.makeText(context, R.string.password_updated, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    inputLayout.error = context.getString(R.string.strong_password_rules)
                }
            }
        }
        dialog.show()
    }

    private fun createPasswordInput(
        hint: String,
        helperText: String?
    ): Pair<TextInputLayout, TextInputEditText> {
        val editText = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val inputLayout = TextInputLayout(context).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(INPUT_HORIZONTAL_PADDING, INPUT_VERTICAL_PADDING, INPUT_HORIZONTAL_PADDING, INPUT_VERTICAL_PADDING)
            this.hint = hint
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            this.helperText = helperText
            addView(editText)
        }
        return inputLayout to editText
    }

    companion object {
        private const val INPUT_HORIZONTAL_PADDING = 24
        private const val INPUT_VERTICAL_PADDING = 8
    }
}
