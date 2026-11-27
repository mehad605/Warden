package com.warden.app.feature.gatekeeper.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.warden.app.R
import com.warden.app.core.platform.LaunchableApp
import com.warden.app.databinding.DialogAiChatBinding
import com.warden.app.databinding.ItemChatBubbleAiBinding
import com.warden.app.databinding.ItemChatBubbleUserBinding
import com.warden.app.feature.gatekeeper.domain.GatekeeperVerdict
import com.warden.app.feature.gatekeeper.domain.JustificationValidator

/**
 * View-state helper for the full-screen chat [DialogAiChatBinding]: configures mode headers,
 * target app bar, live sentence counter, conversation bubbles, typing indicator, and verdict cards.
 */
internal class GatekeeperDialogViewBinder(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val binding: DialogAiChatBinding,
) {
    /** Configures titles, metadata pill, and target-app bar visibility for [mode]. */
    fun bindModeHeader(
        mode: GatekeeperDialogMode,
        rejectionCount: Int,
        targetAppLabel: String?,
    ) {
        val rejections =
            context.resources.getQuantityString(R.plurals.rejection_count, rejectionCount, rejectionCount)
        binding.tvChatMeta.text = context.getString(R.string.ai_chat_meta_fmt, rejections)
        when (mode) {
            GatekeeperDialogMode.TEMPORARY_IGNORE -> {
                binding.tvDialogTitle.setText(R.string.ai_ignore_title)
                binding.tvDialogSubtitle.setText(R.string.ai_ignore_subtitle)
                binding.layoutTargetApp.visibility = View.VISIBLE
            }
            GatekeeperDialogMode.PAUSE_BLOCKER -> {
                binding.tvDialogTitle.setText(R.string.ai_pause_title)
                binding.tvDialogSubtitle.setText(R.string.ai_pause_subtitle)
                binding.layoutTargetApp.visibility = View.GONE
                binding.btnAttachImage.visibility = View.VISIBLE
            }
            GatekeeperDialogMode.EXTEND_IGNORE -> {
                val label = targetAppLabel ?: context.getString(R.string.ai_default_app_label)
                binding.tvDialogTitle.setText(R.string.ai_extend_title)
                binding.tvDialogSubtitle.text = context.getString(R.string.ai_extend_subtitle, label)
                binding.layoutTargetApp.visibility = View.VISIBLE
                binding.btnAttachImage.visibility = View.VISIBLE
            }
        }
        updateSentenceCounter("", isRemoval = false)
    }

    /** Shows loading copy in the target app bar while launchable apps load asynchronously. */
    fun showAppsLoading() {
        binding.tvSelectedApp.setText(R.string.loading_installed_apps)
        binding.layoutTargetApp.isEnabled = false
        binding.ivAppChevron.visibility = View.GONE
        binding.btnSubmit.isEnabled = false
    }

    /** Updates the target app bar with [selectedApp] and locks picker interaction when [locked]. */
    fun renderSelectedApp(
        selectedApp: LaunchableApp?,
        locked: Boolean,
        mode: GatekeeperDialogMode,
        isRemoval: Boolean,
    ) {
        binding.tvSelectedApp.text =
            selectedApp?.label ?: context.getString(R.string.select_app_prompt)
        binding.layoutTargetApp.isEnabled = !locked
        binding.ivAppChevron.visibility = if (locked) View.GONE else View.VISIBLE
        binding.btnSubmit.isEnabled = true

        if (mode == GatekeeperDialogMode.TEMPORARY_IGNORE && selectedApp != null) {
            applyTempIgnoreCopy(selectedApp.label, isRemoval)
        }
        updateSentenceCounter(binding.etJustification.text?.toString().orEmpty(), isRemoval)
    }

    /** Updates the live sentence counter pill above the composer. */
    fun updateSentenceCounter(
        text: String,
        isRemoval: Boolean,
    ) {
        if (isRemoval) {
            binding.tvSentenceCounter.setText(R.string.ai_removal_counter_hint)
            binding.tvSentenceCounter.setTextColor(ContextCompat.getColor(context, R.color.status_active))
            return
        }
        val count = JustificationValidator.countSentences(text)
        val isReady = JustificationValidator.validate(text, isRemoval = false) == null
        val pluralRes = if (isReady) R.plurals.sentence_counter_ready_fmt else R.plurals.sentence_counter_fmt
        binding.tvSentenceCounter.text =
            context.resources.getQuantityString(pluralRes, count, count, JustificationValidator.REQUIRED_SENTENCES)
        val color =
            if (isReady) {
                ContextCompat.getColor(context, R.color.status_active)
            } else {
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
        binding.tvSentenceCounter.setTextColor(color)
    }

    /** Appends the user's submitted justification (and optional thumbnail) as a right-aligned bubble. */
    fun appendUserBubble(
        message: String,
        attachment: Bitmap?,
    ) {
        val item = ItemChatBubbleUserBinding.inflate(layoutInflater, binding.layoutConversationEntries, false)
        item.tvUserMessage.text = message
        if (attachment != null) {
            item.ivUserAttachment.setImageBitmap(attachment)
            item.ivUserAttachment.visibility = View.VISIBLE
        }
        binding.layoutConversationEntries.addView(item.root)
        scrollToBottom()
    }

    /** Toggles the AI thinking bubble and composer interactivity while evaluating. */
    fun setLoading(isLoading: Boolean) {
        binding.layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled = !isLoading
        binding.etJustification.isEnabled = !isLoading
        binding.btnAttachImage.isEnabled = !isLoading
        if (isLoading) scrollToBottom()
    }

    /** Appends the styled APPROVED/REJECTED verdict bubble and auto-dismisses on approval. */
    fun showVerdict(
        dialog: Dialog,
        verdict: GatekeeperVerdict,
        onApprovedRendered: () -> Unit,
        onRejectedRendered: () -> Unit,
    ) {
        binding.layoutLoading.visibility = View.GONE
        val item = ItemChatBubbleAiBinding.inflate(layoutInflater, binding.layoutConversationEntries, false)
        item.tvVerdictMessage.text = verdict.botResponse

        if (verdict.approved) {
            val activeColor = ContextCompat.getColor(context, R.color.status_active)
            item.tvVerdictTitle.text =
                if (verdict.durationMinutes > 0) {
                    context.getString(R.string.verdict_approved_minutes_fmt, verdict.durationMinutes)
                } else {
                    context.getString(R.string.verdict_approved)
                }
            item.tvVerdictTitle.setTextColor(activeColor)
            item.cardAiVerdict.strokeColor = activeColor
            binding.layoutConversationEntries.addView(item.root)
            scrollToBottom()
            onApprovedRendered()
            binding.btnSubmit.postDelayed({ if (dialog.isShowing) dialog.dismiss() }, AUTO_DISMISS_DELAY_MS)
        } else {
            val errorColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorError)
            item.tvVerdictTitle.setText(R.string.verdict_rejected)
            item.tvVerdictTitle.setTextColor(errorColor)
            item.cardAiVerdict.strokeColor = errorColor
            binding.layoutConversationEntries.addView(item.root)
            scrollToBottom()
            onRejectedRendered()
            setLoading(false)
        }
    }

    private fun applyTempIgnoreCopy(
        appLabel: String,
        isRemoval: Boolean,
    ) {
        if (isRemoval) {
            binding.tvDialogTitle.setText(R.string.ai_remove_ignore_title)
            binding.tvDialogSubtitle.text = context.getString(R.string.ai_remove_ignore_subtitle, appLabel)
            binding.btnAttachImage.visibility = View.GONE
            binding.etJustification.setHint(R.string.ai_removal_justification_hint)
        } else {
            binding.tvDialogTitle.setText(R.string.ai_ignore_title)
            binding.tvDialogSubtitle.text = context.getString(R.string.ai_ignore_app_subtitle, appLabel)
            binding.btnAttachImage.visibility = View.VISIBLE
            binding.etJustification.setHint(R.string.ai_composer_hint)
        }
    }

    private fun scrollToBottom() {
        binding.scrollChatMessages.post {
            binding.scrollChatMessages.fullScroll(View.FOCUS_DOWN)
        }
    }

    companion object {
        private const val AUTO_DISMISS_DELAY_MS = 3_000L
    }
}
