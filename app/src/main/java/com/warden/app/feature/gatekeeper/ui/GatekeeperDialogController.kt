package com.warden.app.feature.gatekeeper.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
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
 * Coordinates the full-screen, keyboard-aware AI Gatekeeper chat dialog lifecycle:
 * serving cached launchable apps instantaneously, decoding screenshot attachments,
 * validating justifications live, and streaming verdict bubbles into the chat thread.
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
    private class ChatSessionState(
        var apps: List<LaunchableApp> = emptyList(),
        var selectedApp: LaunchableApp? = null,
        var attachedBitmap: Bitmap? = null,
    )

    /** Displays the full-screen gatekeeper chat for [mode], optionally locked to [targetPackage]. */
    fun show(
        mode: GatekeeperDialogMode,
        permanentIgnoredApps: List<String>,
        rejectionCount: Int,
        targetPackage: String? = null,
    ): Dialog {
        val binding = DialogAiChatBinding.inflate(layoutInflater)
        val viewBinder = GatekeeperDialogViewBinder(context, layoutInflater, binding)
        val dialog = createChatDialog(binding)
        val state = ChatSessionState()

        val targetLabel = targetPackage?.let { appsProvider.cachedLabel(it) ?: it }
        viewBinder.bindModeHeader(mode, rejectionCount, targetLabel)
        if (mode != GatekeeperDialogMode.PAUSE_BLOCKER) {
            bindAppSelector(binding, viewBinder, dialog, state, mode, permanentIgnoredApps, targetPackage)
        }
        bindComposer(binding, viewBinder, dialog, state, mode, permanentIgnoredApps, rejectionCount)
        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
        return dialog
    }

    private fun createChatDialog(binding: DialogAiChatBinding): Dialog {
        val dialog = Dialog(context, R.style.Theme_BlockWords_ChatDialog)
        dialog.setContentView(binding.root)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            @Suppress("DEPRECATION")
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.chatRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        return dialog
    }

    private fun bindAppSelector(
        binding: DialogAiChatBinding,
        viewBinder: GatekeeperDialogViewBinder,
        dialog: Dialog,
        state: ChatSessionState,
        mode: GatekeeperDialogMode,
        permanentIgnoredApps: List<String>,
        targetPackage: String?,
    ) {
        val warm = appsProvider.cachedLaunchableAppsOrNull()
        if (warm != null) {
            applyLoadedApps(warm, viewBinder, state, mode, permanentIgnoredApps, targetPackage)
        } else {
            viewBinder.showAppsLoading()
            scope.launch {
                val loaded = appsProvider.loadLaunchableApps()
                if (dialog.isShowing) {
                    applyLoadedApps(loaded, viewBinder, state, mode, permanentIgnoredApps, targetPackage)
                }
            }
        }
        binding.layoutTargetApp.setOnClickListener {
            if (targetPackage != null || state.apps.isEmpty()) return@setOnClickListener
            AppPickerDialog.show(context, layoutInflater, state.apps) { picked ->
                state.selectedApp = picked
                viewBinder.renderSelectedApp(
                    selectedApp = picked,
                    locked = false,
                    mode = mode,
                    isRemoval = isRemovalRequest(mode, picked, permanentIgnoredApps),
                )
            }
        }
    }

    private fun applyLoadedApps(
        loaded: List<LaunchableApp>,
        viewBinder: GatekeeperDialogViewBinder,
        state: ChatSessionState,
        mode: GatekeeperDialogMode,
        permanentIgnoredApps: List<String>,
        targetPackage: String?,
    ) {
        state.apps = loaded
        state.selectedApp =
            if (targetPackage != null) {
                loaded.firstOrNull { it.packageName == targetPackage }
                    ?: LaunchableApp(appsProvider.cachedLabel(targetPackage) ?: targetPackage, targetPackage)
            } else {
                loaded.firstOrNull()
            }
        viewBinder.renderSelectedApp(
            selectedApp = state.selectedApp,
            locked = targetPackage != null,
            mode = mode,
            isRemoval = isRemovalRequest(mode, state.selectedApp, permanentIgnoredApps),
        )
    }

    private fun bindComposer(
        binding: DialogAiChatBinding,
        viewBinder: GatekeeperDialogViewBinder,
        dialog: Dialog,
        state: ChatSessionState,
        mode: GatekeeperDialogMode,
        permanentIgnoredApps: List<String>,
        rejectionCount: Int,
    ) {
        binding.etJustification.doAfterTextChanged { text ->
            val isRemoval = isRemovalRequest(mode, state.selectedApp, permanentIgnoredApps)
            viewBinder.updateSentenceCounter(text?.toString().orEmpty(), isRemoval)
        }
        binding.btnAttachImage.setOnClickListener {
            requestPickImage { uri ->
                scope.launch {
                    val bitmap = withContext(ioDispatcher) { BitmapImageEncoder.decodeFromUri(context, uri) }
                    if (bitmap != null && dialog.isShowing) {
                        state.attachedBitmap = bitmap
                        binding.cardImagePreview.visibility = View.VISIBLE
                        binding.ivPreview.setImageBitmap(bitmap)
                    }
                }
            }
        }
        binding.btnRemoveImage.setOnClickListener {
            state.attachedBitmap = null
            binding.cardImagePreview.visibility = View.GONE
        }
        binding.btnSubmit.setOnClickListener {
            handleSubmit(binding, viewBinder, dialog, state, mode, permanentIgnoredApps, rejectionCount)
        }
    }

    private fun handleSubmit(
        binding: DialogAiChatBinding,
        viewBinder: GatekeeperDialogViewBinder,
        dialog: Dialog,
        state: ChatSessionState,
        mode: GatekeeperDialogMode,
        permanentIgnoredApps: List<String>,
        rejectionCount: Int,
    ) {
        val justification = binding.etJustification.text?.toString()?.trim().orEmpty()
        val selectedApp = state.selectedApp
        if (mode != GatekeeperDialogMode.PAUSE_BLOCKER && selectedApp == null) {
            Toast.makeText(context, R.string.select_app_prompt, Toast.LENGTH_SHORT).show()
            return
        }
        val isRemoval = isRemovalRequest(mode, selectedApp, permanentIgnoredApps)
        val validationError = JustificationValidator.validate(justification, isRemoval = isRemoval)
        if (validationError != null) {
            Toast.makeText(context, validationError, Toast.LENGTH_LONG).show()
            return
        }

        val attachmentToSend = state.attachedBitmap
        viewBinder.appendUserBubble(justification, attachmentToSend)
        binding.etJustification.setText("")
        state.attachedBitmap = null
        binding.cardImagePreview.visibility = View.GONE

        val request = buildRequest(mode, selectedApp, isRemoval, justification, rejectionCount)
        evaluateAsync(viewBinder, dialog, mode, selectedApp, isRemoval, request, attachmentToSend)
    }

    private fun isRemovalRequest(
        mode: GatekeeperDialogMode,
        selectedApp: LaunchableApp?,
        permanentIgnoredApps: List<String>,
    ): Boolean =
        mode == GatekeeperDialogMode.TEMPORARY_IGNORE &&
            selectedApp != null &&
            permanentIgnoredApps.contains(selectedApp.packageName)

    private fun buildRequest(
        mode: GatekeeperDialogMode,
        selectedApp: LaunchableApp?,
        isRemoval: Boolean,
        justification: String,
        rejectionCount: Int,
    ): GatekeeperRequest {
        val label = selectedApp?.label.orEmpty()
        val pkg = selectedApp?.packageName.orEmpty()
        return when (mode) {
            GatekeeperDialogMode.PAUSE_BLOCKER -> GatekeeperRequest.PauseBlocker(justification, rejectionCount)
            GatekeeperDialogMode.EXTEND_IGNORE -> GatekeeperRequest.ExtendExemption(label, pkg, justification, rejectionCount)
            GatekeeperDialogMode.TEMPORARY_IGNORE ->
                if (isRemoval) {
                    GatekeeperRequest.RemoveFromIgnore(label, pkg, justification, rejectionCount)
                } else {
                    GatekeeperRequest.AppExemption(label, pkg, justification, rejectionCount)
                }
        }
    }

    private fun evaluateAsync(
        viewBinder: GatekeeperDialogViewBinder,
        dialog: Dialog,
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
                if (dialog.isShowing) {
                    viewBinder.showVerdict(
                        dialog = dialog,
                        verdict = verdict,
                        onApprovedRendered = { onApproved(mode, selectedApp, isRemoval, verdict) },
                        onRejectedRendered = onRejected,
                    )
                }
            } catch (e: IOException) {
                if (dialog.isShowing) viewBinder.setLoading(false)
                showErrorToast(e.message)
            } catch (e: IllegalStateException) {
                if (dialog.isShowing) viewBinder.setLoading(false)
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
}
