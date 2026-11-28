package com.warden.app.feature.blocker.ui

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.warden.app.R

/**
 * Material 3 dialogs for creating and renaming custom website-blocking lists/profiles.
 */
internal object WebsiteProfileDialogs {
    private const val DIALOG_PADDING_DP = 20

    /** Displays a dialog prompting the user for a new website list name. */
    internal fun showCreateDialog(
        context: Context,
        onConfirm: (name: String) -> Unit,
    ) {
        showNameDialog(
            context = context,
            titleRes = R.string.create_website_list_title,
            positiveRes = R.string.create_action,
            initialName = "",
            onConfirm = onConfirm,
        )
    }

    /** Displays a dialog allowing the user to rename a custom website list. */
    internal fun showRenameDialog(
        context: Context,
        currentName: String,
        onConfirm: (newName: String) -> Unit,
    ) {
        showNameDialog(
            context = context,
            titleRes = R.string.rename_website_list_title,
            positiveRes = R.string.save,
            initialName = currentName,
            onConfirm = onConfirm,
        )
    }

    private fun showNameDialog(
        context: Context,
        titleRes: Int,
        positiveRes: Int,
        initialName: String,
        onConfirm: (String) -> Unit,
    ) {
        val input =
            EditText(context).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                hint = context.getString(R.string.website_list_name_hint)
                setText(initialName)
                setSelection(text.length)
                requestFocus()
            }
        val padPx = (DIALOG_PADDING_DP * context.resources.displayMetrics.density).toInt()
        val container =
            FrameLayout(context).apply {
                setPadding(padPx, padPx / 2, padPx, 0)
                addView(input)
            }
        MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
            .setView(container)
            .setPositiveButton(positiveRes) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) onConfirm(name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
