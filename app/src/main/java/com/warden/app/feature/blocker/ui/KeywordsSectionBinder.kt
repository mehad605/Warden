package com.warden.app.feature.blocker.ui

import android.content.Context
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.warden.app.R
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import com.warden.app.feature.blocker.domain.KeywordNormalizer
import com.warden.app.feature.blocker.domain.NormalizedKeyword
import com.warden.app.feature.security.ui.PasswordDialogController

/**
 * Handles blocked-keyword and whitelisted-keyword inputs, URL normalization warnings,
 * risky-word confirmation, and chip-cloud rendering/deletion.
 */
class KeywordsSectionBinder(
    private val context: Context,
    private val binding: FragmentKeywordBlockerBinding,
    private val hasPassword: () -> Boolean,
    private val passwordDialogs: PasswordDialogController,
    private val dispatch: (BlockerAction) -> Unit,
) {
    /** Wires add-icon click listeners and Enter key submission for both keyword inputs. */
    fun bindListeners() {
        val rules = binding.tabRules
        rules.tilAddWord.setEndIconOnClickListener { submitBlockedKeyword() }
        rules.etKeyword.setOnKeyListener { _, keyCode, event ->
            handleEnterKey(keyCode, event, ::submitBlockedKeyword)
        }
        rules.tilAddWhitelistWord.setEndIconOnClickListener { submitWhitelistKeyword() }
        rules.etWhitelistKeyword.setOnKeyListener { _, keyCode, event ->
            handleEnterKey(keyCode, event, ::submitWhitelistKeyword)
        }
    }

    /** Renders both blocked and whitelisted chip clouds and their pluralized counts. */
    fun render(
        blockedKeywords: List<String>,
        whitelistedKeywords: List<String>,
    ) {
        val rules = binding.tabRules
        renderChips(rules.cgKeywords, rules.tvKeywordCount, blockedKeywords, isWhitelist = false)
        renderChips(rules.cgWhitelistKeywords, rules.tvWhitelistKeywordCount, whitelistedKeywords, isWhitelist = true)
    }

    private fun renderChips(
        chipGroup: ChipGroup,
        countView: TextView,
        keywords: List<String>,
        isWhitelist: Boolean,
    ) {
        chipGroup.removeAllViews()
        countView.text = context.resources.getQuantityString(R.plurals.word_count, keywords.size, keywords.size)
        keywords.forEach { keyword ->
            val chip =
                Chip(context).apply {
                    text = keyword
                    isCloseIconVisible = true
                    setOnCloseIconClickListener { requestRemove(keyword, isWhitelist) }
                }
            chipGroup.addView(chip)
        }
    }

    private fun submitBlockedKeyword() {
        val normalized = KeywordNormalizer.normalize(binding.tabRules.etKeyword.text?.toString().orEmpty()) ?: return
        if (normalized.isRisky) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.risky_keyword_title)
                .setMessage(R.string.risky_keyword_message)
                .setPositiveButton(R.string.add_anyway) { _, _ -> commitKeyword(normalized, isWhitelist = false) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            commitKeyword(normalized, isWhitelist = false)
        }
    }

    private fun submitWhitelistKeyword() {
        val normalized = KeywordNormalizer.normalize(binding.tabRules.etWhitelistKeyword.text?.toString().orEmpty()) ?: return
        commitKeyword(normalized, isWhitelist = true)
    }

    private fun commitKeyword(
        normalized: NormalizedKeyword,
        isWhitelist: Boolean,
    ) {
        if (normalized.wasUrl) {
            Toast.makeText(context, R.string.warning_link_blocker_may_not_work, Toast.LENGTH_LONG).show()
        }
        if (isWhitelist) {
            dispatch(BlockerAction.AddWhitelistedKeyword(normalized.normalized))
            binding.tabRules.etWhitelistKeyword.setText("")
        } else {
            dispatch(BlockerAction.AddBlockedKeyword(normalized.normalized))
            binding.tabRules.etKeyword.setText("")
        }
    }

    private fun requestRemove(
        keyword: String,
        isWhitelist: Boolean,
    ) {
        val remove = {
            dispatch(
                if (isWhitelist) {
                    BlockerAction.RemoveWhitelistedKeyword(keyword)
                } else {
                    BlockerAction.RemoveBlockedKeyword(keyword)
                },
            )
        }
        if (hasPassword()) {
            passwordDialogs.showVerifyDialog(
                title = context.getString(R.string.password_prompt_title),
                message = context.getString(R.string.password_required_to_delete),
            ) {
                remove()
                val toastRes = if (isWhitelist) R.string.whitelist_keyword_removed else R.string.keyword_removed
                Toast.makeText(context, toastRes, Toast.LENGTH_SHORT).show()
            }
        } else {
            remove()
        }
    }

    private fun handleEnterKey(
        keyCode: Int,
        event: KeyEvent?,
        onEnter: () -> Unit,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && (event == null || event.action == KeyEvent.ACTION_UP)) {
            onEnter()
            return true
        }
        return false
    }
}
