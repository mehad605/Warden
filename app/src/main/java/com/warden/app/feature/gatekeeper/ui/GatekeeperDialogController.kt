package com.warden.app.feature.gatekeeper.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.warden.app.R
import com.warden.app.core.platform.InstalledAppsProvider
import com.warden.app.core.platform.LaunchableApp
import com.warden.app.databinding.DialogAiChatBinding
import com.warden.app.feature.gatekeeper.domain.EncodedImage
import com.warden.app.feature.gatekeeper.domain.GatekeeperRequest
import com.warden.app.feature.gatekeeper.domain.GatekeeperVerdict
import com.warden.app.feature.gatekeeper.domain.JustificationValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Supported AI Gatekeeper dialog flows. */
enum class GatekeeperDialogMode {
    TEMPORARY_IGNORE,
    PAUSE_BLOCKER,
    EXTEND_IGNORE,
}

/**
 * Coordinates the AI Gatekeeper dialog lifecycle: loading apps off the main thread,
 * decoding screenshot attachments, validating justifications locally, and invoking evaluation.
 */
class GatekeeperDialogController(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val scope: CoroutineScope,
    private val appsProvider: InstalledAppsProvider,
    private val requestPickImage: (onPicked: (Uri) -> Unit) -> Unit,
    private val evaluateRequest: suspend (GatekeeperRequest, EncodedImage?) -> GatekeeperVerdict,
    private val onApproved: (GatekeeperDialogMode, LaunchableApp?, Boolean, GatekeeperVerdict) -> Unit,
    private val onRejected: () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Displays the gatekeeper dialog for [mode], optionally locked to [targetPackage]. */
    fun show(
        mode: GatekeeperDialogMode,
        permanentIgnoredApps: List<String>,
        rejectionCount: Int,
        targetPackage: String? = null,
    ) {
        val binding = DialogAiChatBinding.inflate(layoutInflater)
        val viewBinder = GatekeeperDialogViewBinder(context, binding)
        val dialog = MaterialAlertDialogBuilder(context).setView(binding.root).create()
        var attachedBitmap: Bitmap? = null

        val targetLabel = targetPackage?.let { appsProvider.cachedLabel(it) ?: it }
        viewBinder.bindModeHeader(mode, permanentIgnoredApps, targetLabel)
        if (mode != GatekeeperDialogMode.PAUSE_BLOCKER) {
            loadAppsAsync(viewBinder, dialog, targetPackage)
        }
        bindAttachmentButtons(binding) { attachedBitmap = it }
        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnSubmit.setOnClickListener {
            handleSubmit(binding, viewBinder, dialog, mode, permanentIgnoredApps, rejectionCount, attachedBitmap)
        }
        dialog.show()
    }

    private fun loadAppsAsync(
        viewBinder: GatekeeperDialogViewBinder,
        dialog: AlertDialog,
        targetPackage: String?,
    ) {
        viewBinder.showAppsLoading()
        scope.launch {
            val apps = appsProvider.loadLaunchableApps()
            if (dialog.isShowing) {
                viewBinder.bindLoadedApps(apps, targetPackage)
            }
        }
    }

    private fun bindAttachmentButtons(
        binding: DialogAiChatBinding,
        onBitmapChanged: (Bitmap?) -> Unit,
    ) {
        binding.btnAttachImage.setOnClickListener {
            requestPickImage { uri ->
                scope.launch {
                    val bitmap = withContext(ioDispatcher) { decodeBitmapFromUri(uri) }
                    if (bitmap != null) {
                        onBitmapChanged(bitmap)
                        binding.cardImagePreview.visibility = View.VISIBLE
                        binding.ivPreview.setImageBitmap(bitmap)
                    }
                }
            }
        }
        binding.btnRemoveImage.setOnClickListener {
            onBitmapChanged(null)
            binding.cardImagePreview.visibility = View.GONE
        }
    }

    private fun handleSubmit(
        binding: DialogAiChatBinding,
        viewBinder: GatekeeperDialogViewBinder,
        dialog: AlertDialog,
        mode: GatekeeperDialogMode,
        permanentIgnoredApps: List<String>,
        rejectionCount: Int,
        attachedBitmap: Bitmap?,
    ) {
        val justification = binding.etJustification.text?.toString().orEmpty()
        val selectedApp = binding.spinnerApps.selectedItem as? LaunchableApp
        if (mode != GatekeeperDialogMode.PAUSE_BLOCKER && selectedApp == null) {
            Toast.makeText(context, R.string.select_app_prompt, Toast.LENGTH_SHORT).show()
            return
        }
        val isRemoval =
            mode == GatekeeperDialogMode.TEMPORARY_IGNORE &&
                selectedApp != null &&
                permanentIgnoredApps.contains(selectedApp.packageName)

        val validationError = JustificationValidator.validate(justification, isRemoval = isRemoval)
        if (validationError != null) {
            Toast.makeText(context, validationError, Toast.LENGTH_LONG).show()
            return
        }

        val request = buildRequest(mode, selectedApp, isRemoval, justification, rejectionCount)
        evaluateAsync(viewBinder, dialog, mode, selectedApp, isRemoval, request, attachedBitmap)
    }

    private fun buildRequest(
        mode: GatekeeperDialogMode,
        selectedApp: LaunchableApp?,
        isRemoval: Boolean,
        justification: String,
        rejectionCount: Int,
    ): GatekeeperRequest =
        when (mode) {
            GatekeeperDialogMode.PAUSE_BLOCKER -> GatekeeperRequest.PauseBlocker(justification, rejectionCount)
            GatekeeperDialogMode.EXTEND_IGNORE ->
                GatekeeperRequest.ExtendExemption(
                    appName = selectedApp?.label.orEmpty(),
                    packageName = selectedApp?.packageName.orEmpty(),
                    userPrompt = justification,
                    rejectionCount = rejectionCount,
                )
            GatekeeperDialogMode.TEMPORARY_IGNORE ->
                if (isRemoval) {
                    GatekeeperRequest.RemoveFromIgnore(
                        appName = selectedApp?.label.orEmpty(),
                        packageName = selectedApp?.packageName.orEmpty(),
                        userPrompt = justification,
                        rejectionCount = rejectionCount,
                    )
                } else {
                    GatekeeperRequest.AppExemption(
                        appName = selectedApp?.label.orEmpty(),
                        packageName = selectedApp?.packageName.orEmpty(),
                        userPrompt = justification,
                        rejectionCount = rejectionCount,
                    )
                }
        }

    private fun evaluateAsync(
        viewBinder: GatekeeperDialogViewBinder,
        dialog: AlertDialog,
        mode: GatekeeperDialogMode,
        selectedApp: LaunchableApp?,
        isRemoval: Boolean,
        request: GatekeeperRequest,
        attachedBitmap: Bitmap?,
    ) {
        viewBinder.setLoading(true)
        scope.launch {
            try {
                val encodedImage =
                    attachedBitmap?.let { bmp ->
                        withContext(Dispatchers.Default) { BitmapImageEncoder.encode(bmp) }
                    }
                val verdict = evaluateRequest(request, encodedImage)
                viewBinder.showVerdict(
                    dialog = dialog,
                    verdict = verdict,
                    onApprovedRendered = { onApproved(mode, selectedApp, isRemoval, verdict) },
                    onRejectedRendered = onRejected,
                )
            } catch (e: IOException) {
                viewBinder.setLoading(false)
                showErrorToast(e.message)
            } catch (e: IllegalStateException) {
                viewBinder.setLoading(false)
                showErrorToast(e.message)
            }
        }
    }

    private fun showErrorToast(message: String?) {
        Toast.makeText(
            context,
            context.getString(R.string.ai_request_error, message.orEmpty()),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? =
        try {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(resolver, uri)
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
}
