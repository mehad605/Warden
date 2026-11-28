package com.warden.app.feature.blocker.ui

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.warden.app.R
import com.warden.app.data.models.BlockedEntry
import com.warden.app.data.models.MatchMode
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import com.warden.app.feature.blocker.domain.KeywordNormalizer
import com.warden.app.feature.blocker.domain.NormalizedKeyword
import com.warden.app.feature.blocker.domain.RuleBadgeKind
import com.warden.app.feature.blocker.domain.RuleItemSpec
import com.warden.app.feature.blocker.domain.RuleListFormatter
import com.warden.app.feature.blocker.domain.TermValidationResult
import com.warden.app.feature.security.ui.PasswordDialogController

/**
 * Manages the Blocked Words and Safe Phrases lists: per-entry match mode picker, length/empty
 * validation, live search, dictionary-sorted letter-grouped virtualized lists, inline mode toggle,
 * and password-gated delete with 5-second Undo.
 */
class KeywordsSectionBinder(
    private val context: Context,
    private val binding: FragmentKeywordBlockerBinding,
    private val hasPassword: () -> Boolean,
    private val passwordDialogs: PasswordDialogController,
    private val dispatch: (BlockerAction) -> Unit,
) {
    private var latestBlocked: List<BlockedEntry> = emptyList()
    private var latestWhitelist: List<String> = emptyList()
    private var blockedQuery: String = ""
    private var whitelistQuery: String = ""

    private val blockedAdapter =
        RuleListAdapter(
            onBadgeClick = ::toggleEntryModeInline,
            onActionClick = { spec -> requestRemoveBlocked(spec.id) },
        )

    private val whitelistAdapter =
        RuleListAdapter(
            onActionClick = { spec -> requestRemoveWhitelist(spec.id) },
        )

    /** Wires inputs, match mode toggle, live search filters, and virtualized RecyclerViews. */
    fun bindListeners() {
        val rules = binding.tabRules
        rules.rvBlockedWords.layoutManager = LinearLayoutManager(context)
        rules.rvBlockedWords.adapter = blockedAdapter
        rules.rvWhitelistWords.layoutManager = LinearLayoutManager(context)
        rules.rvWhitelistWords.adapter = whitelistAdapter

        rules.tilAddWord.setEndIconOnClickListener { submitBlockedKeyword() }
        bindSubmitInput(rules.etKeyword, rules.tilAddWord, ::submitBlockedKeyword)
        rules.tilAddWhitelistWord.setEndIconOnClickListener { submitWhitelistKeyword() }
        bindSubmitInput(rules.etWhitelistKeyword, rules.tilAddWhitelistWord, ::submitWhitelistKeyword)

        rules.etSearchBlocked.doAfterTextChanged { text ->
            blockedQuery = text?.toString().orEmpty()
            renderBlockedList()
        }
        rules.etSearchWhitelist.doAfterTextChanged { text ->
            whitelistQuery = text?.toString().orEmpty()
            renderWhitelistList()
        }
    }

    private fun bindSubmitInput(
        editText: android.widget.EditText,
        layout: com.google.android.material.textfield.TextInputLayout,
        onSubmit: () -> Unit,
    ) {
        editText.doAfterTextChanged { layout.error = null }
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onSubmit()
                true
            } else {
                false
            }
        }
        editText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && (event == null || event.action == KeyEvent.ACTION_UP)) {
                onSubmit()
                true
            } else {
                false
            }
        }
    }

    /** Renders both blocked entries (with modes) and whitelisted safe phrases. */
    fun render(
        blockedEntries: List<BlockedEntry>,
        whitelistedKeywords: List<String>,
    ) {
        latestBlocked = blockedEntries
        latestWhitelist = whitelistedKeywords
        renderBlockedList()
        renderWhitelistList()
    }

    private fun renderBlockedList() {
        val rules = binding.tabRules
        val exactCount = latestBlocked.count { it.mode == MatchMode.EXACT }
        val containsCount = latestBlocked.size - exactCount
        rules.tvKeywordCount.text =
            context.resources.getQuantityString(
                R.plurals.blocked_counts_summary_fmt,
                latestBlocked.size,
                latestBlocked.size,
                exactCount,
                containsCount,
            )

        val specs =
            latestBlocked.map { entry ->
                val isContains = entry.mode == MatchMode.CONTAINS
                val badgeLabel =
                    context.getString(if (isContains) R.string.badge_anywhere else R.string.badge_exact)
                RuleItemSpec(
                    id = entry.term,
                    label = entry.term,
                    badgeText = badgeLabel,
                    badgeKind = if (isContains) RuleBadgeKind.CONTAINS else RuleBadgeKind.EXACT,
                    badgeClickable = true,
                )
            }
        val grouped = RuleListFormatter.buildGroupedList(specs, blockedQuery)
        blockedAdapter.submitList(grouped.rows)
        LetterJumpBarBinder.bind(context, rules.layoutJumpBlocked, rules.scrollJumpBlocked, grouped.letterPositions, rules.rvBlockedWords)
        updateEmptyState(rules.tvEmptyBlocked, rules.rvBlockedWords, grouped.rows.isEmpty(), blockedQuery, R.string.empty_blocked_words)
    }

    private fun renderWhitelistList() {
        val rules = binding.tabRules
        rules.tvWhitelistKeywordCount.text =
            context.resources.getQuantityString(R.plurals.word_count, latestWhitelist.size, latestWhitelist.size)

        val specs = latestWhitelist.map { RuleItemSpec(id = it, label = it) }
        val grouped = RuleListFormatter.buildGroupedList(specs, whitelistQuery)
        whitelistAdapter.submitList(grouped.rows)
        LetterJumpBarBinder.bind(
            context,
            rules.layoutJumpWhitelist,
            rules.scrollJumpWhitelist,
            grouped.letterPositions,
            rules.rvWhitelistWords,
        )
        updateEmptyState(
            rules.tvEmptyWhitelist,
            rules.rvWhitelistWords,
            grouped.rows.isEmpty(),
            whitelistQuery,
            R.string.empty_safe_phrases,
        )
    }

    private fun updateEmptyState(
        emptyView: android.widget.TextView,
        listView: View,
        isEmpty: Boolean,
        query: String,
        defaultEmptyRes: Int,
    ) {
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        listView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (isEmpty) {
            emptyView.setText(if (query.isBlank()) defaultEmptyRes else R.string.empty_search_results)
        }
    }

    private fun selectedAddMode(): MatchMode =
        if (binding.tabRules.toggleAddMode.checkedButtonId == R.id.btn_add_mode_contains) {
            MatchMode.CONTAINS
        } else {
            MatchMode.EXACT
        }

    private fun submitBlockedKeyword() {
        val rules = binding.tabRules
        val raw = rules.etKeyword.text?.toString().orEmpty()
        when (val result = KeywordNormalizer.validateAndNormalize(raw)) {
            TermValidationResult.Empty -> rules.tilAddWord.error = context.getString(R.string.error_term_empty)
            is TermValidationResult.TooLong ->
                rules.tilAddWord.error =
                    context.resources.getQuantityString(R.plurals.error_term_too_long, result.maxLength, result.maxLength)
            is TermValidationResult.Valid -> {
                rules.tilAddWord.error = null
                handleValidBlocked(result.value, selectedAddMode())
            }
        }
    }

    private fun handleValidBlocked(
        normalized: NormalizedKeyword,
        mode: MatchMode,
    ) {
        if (normalized.isRisky) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.risky_keyword_title)
                .setMessage(R.string.risky_keyword_message)
                .setPositiveButton(R.string.add_anyway) { _, _ -> commitBlocked(normalized, mode) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            commitBlocked(normalized, mode)
        }
    }

    private fun commitBlocked(
        normalized: NormalizedKeyword,
        mode: MatchMode,
    ) {
        if (normalized.wasUrl) {
            Toast.makeText(context, R.string.warning_link_blocker_may_not_work, Toast.LENGTH_LONG).show()
        }
        dispatch(BlockerAction.AddBlockedKeyword(normalized.normalized, mode))
        binding.tabRules.etKeyword.setText("")
    }

    private fun submitWhitelistKeyword() {
        val rules = binding.tabRules
        val raw = rules.etWhitelistKeyword.text?.toString().orEmpty()
        when (val result = KeywordNormalizer.validateAndNormalize(raw)) {
            TermValidationResult.Empty -> rules.tilAddWhitelistWord.error = context.getString(R.string.error_term_empty)
            is TermValidationResult.TooLong ->
                rules.tilAddWhitelistWord.error =
                    context.resources.getQuantityString(R.plurals.error_term_too_long, result.maxLength, result.maxLength)
            is TermValidationResult.Valid -> {
                rules.tilAddWhitelistWord.error = null
                dispatch(BlockerAction.AddWhitelistedKeyword(result.value.normalized))
                rules.etWhitelistKeyword.setText("")
            }
        }
    }

    private fun toggleEntryModeInline(spec: RuleItemSpec) {
        val current = latestBlocked.firstOrNull { it.term == spec.id } ?: return
        val nextMode = if (current.mode == MatchMode.EXACT) MatchMode.CONTAINS else MatchMode.EXACT
        if (current.mode == MatchMode.CONTAINS) {
            runPasswordGated { dispatch(BlockerAction.SetBlockedKeywordMode(current.term, nextMode)) }
        } else {
            dispatch(BlockerAction.SetBlockedKeywordMode(current.term, nextMode))
        }
    }

    private fun requestRemoveBlocked(term: String) {
        val removed = latestBlocked.firstOrNull { it.term == term } ?: return
        runPasswordGated {
            dispatch(BlockerAction.RemoveBlockedKeyword(term))
            showUndoBar(context.getString(R.string.removed_entry_fmt, term)) {
                dispatch(BlockerAction.RestoreBlockedEntry(removed))
            }
        }
    }

    private fun requestRemoveWhitelist(phrase: String) {
        runPasswordGated {
            dispatch(BlockerAction.RemoveWhitelistedKeyword(phrase))
            showUndoBar(context.getString(R.string.removed_entry_fmt, phrase)) {
                dispatch(BlockerAction.AddWhitelistedKeyword(phrase))
            }
        }
    }

    private fun runPasswordGated(onAuthorized: () -> Unit) {
        if (hasPassword()) {
            passwordDialogs.showVerifyDialog(
                title = context.getString(R.string.password_prompt_title),
                message = context.getString(R.string.password_required_to_delete),
                onVerified = onAuthorized,
            )
        } else {
            onAuthorized()
        }
    }

    private fun showUndoBar(
        message: String,
        onUndo: () -> Unit,
    ) {
        Snackbar.make(binding.root, message, UNDO_DURATION_MS)
            .setAnchorView(binding.bottomNavContainer)
            .setAction(R.string.undo_action) { onUndo() }
            .show()
    }

    companion object {
        private const val UNDO_DURATION_MS = 5_000
    }
}
