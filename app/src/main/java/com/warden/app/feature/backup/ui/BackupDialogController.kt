package com.warden.app.feature.backup.ui

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.TextViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.warden.app.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Coordinates settings backup export/import dialogs and executes [android.content.ContentResolver]
 * file I/O off the main thread.
 */
class BackupDialogController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onError: (Exception) -> Unit = {}
) {

    /**
     * Prompts the user to include/exclude secrets when present, then invokes [onConfirmExport]
     * with `(includePassword, includeApiKey)`.
     */
    fun requestExport(
        hasPassword: Boolean,
        hasApiKey: Boolean,
        onConfirmExport: (includePassword: Boolean, includeApiKey: Boolean) -> Unit
    ) {
        if (!hasPassword && !hasApiKey) {
            onConfirmExport(false, false)
            return
        }
        showExportOptionsDialog(hasPassword, hasApiKey, onConfirmExport)
    }

    /** Writes [json] to [uri] on [ioDispatcher] and shows a completion toast on the main thread. */
    fun writeExportedJson(uri: Uri, json: String) {
        scope.launch {
            val succeeded = withContext(ioDispatcher) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: return@withContext false
                    true
                } catch (e: IOException) {
                    onError(e)
                    false
                } catch (e: SecurityException) {
                    onError(e)
                    false
                }
            }
            val msgRes = if (succeeded) R.string.state_exported else R.string.failed_to_export_data
            Toast.makeText(context, msgRes, Toast.LENGTH_SHORT).show()
        }
    }

    /** Reads UTF-8 JSON from [uri] on [ioDispatcher] and passes it to [onImport]. */
    fun readImportJson(uri: Uri, onImport: suspend (String) -> Boolean) {
        scope.launch {
            val rawJson = withContext(ioDispatcher) { readTextSafely(uri) }
            val succeeded = rawJson != null && onImport(rawJson)
            val msgRes = if (succeeded) R.string.state_imported else R.string.failed_to_import
            Toast.makeText(context, msgRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun readTextSafely(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    } catch (e: IOException) {
        onError(e)
        null
    } catch (e: SecurityException) {
        onError(e)
        null
    }

    private fun showExportOptionsDialog(
        hasPassword: Boolean,
        hasApiKey: Boolean,
        onConfirmExport: (Boolean, Boolean) -> Unit
    ) {
        val switchPassword = MaterialSwitch(context).apply {
            text = context.getString(R.string.export_password_hash)
            isChecked = true
            visibility = if (hasPassword) View.VISIBLE else View.GONE
        }
        val switchApiKey = MaterialSwitch(context).apply {
            text = context.getString(R.string.export_api_key)
            isChecked = true
            visibility = if (hasApiKey) View.VISIBLE else View.GONE
        }
        val container = buildOptionsContainer(switchPassword, switchApiKey)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.export_options_title)
            .setView(container)
            .setPositiveButton(R.string.export_action) { dialog, _ ->
                onConfirmExport(
                    hasPassword && switchPassword.isChecked,
                    hasApiKey && switchApiKey.isChecked
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun buildOptionsContainer(
        switchPassword: MaterialSwitch,
        switchApiKey: MaterialSwitch
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(DIALOG_PADDING_H, DIALOG_PADDING_V, DIALOG_PADDING_H, DIALOG_PADDING_V)
        addView(
            TextView(context).apply {
                text = context.getString(R.string.export_options_info)
                setPadding(0, 0, 0, INFO_BOTTOM_PADDING)
                TextViewCompat.setTextAppearance(
                    this,
                    com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
                )
            }
        )
        addView(switchPassword)
        addView(switchApiKey)
    }

    companion object {
        private const val DIALOG_PADDING_H = 48
        private const val DIALOG_PADDING_V = 24
        private const val INFO_BOTTOM_PADDING = 32
    }
}
