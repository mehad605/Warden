package com.warden.app.feature.blocker.ui

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.warden.app.R
import com.warden.app.data.models.WebsiteBlockerConfig
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import com.warden.app.feature.blocker.domain.RuleItemSpec
import com.warden.app.feature.websites.data.PornDomainRepository
import com.warden.app.feature.websites.domain.DomainNormalizer
import com.warden.app.feature.websites.domain.DomainValidationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/**
 * Manages the Website Blocker UI tab: master switch, URL/domain addition & validation,
 * off-main-thread filtering over 46,000+ built-in adult domains, individual built-in
 * disable/re-enable, "Reset built-ins", whitelisted domain overrides, and 5-second Undo.
 */
class WebsiteSectionBinder(
    private val context: Context,
    private val binding: FragmentKeywordBlockerBinding,
    private val scope: CoroutineScope,
    private val repository: PornDomainRepository,
    private val isUpdatingUi: () -> Boolean,
    private val isViewAlive: () -> Boolean,
    private val dispatch: (BlockerAction) -> Unit,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var builtinDomains: List<String> = emptyList()
    private var latestConfig: WebsiteBlockerConfig = WebsiteBlockerConfig()
    private var query: String = ""
    private var renderJob: Job? = null

    private val adapter = RuleListAdapter(onActionClick = ::handleRowAction)

    /** Wires master switch, add URL/domain input, Reset Built-ins button, search, and list. */
    fun bindListeners() {
        val rules = binding.tabRules
        rules.rvDomains.layoutManager = LinearLayoutManager(context)
        rules.rvDomains.adapter = adapter

        rules.switchWebsiteBlocker.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi()) dispatch(SetWebsiteBlockerEnabled(isChecked))
        }
        rules.tilAddDomain.setEndIconOnClickListener { submitDomainInput() }
        rules.etDomainInput.doAfterTextChanged { rules.tilAddDomain.error = null }
        rules.etDomainInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitDomainInput()
                true
            } else {
                false
            }
        }
        rules.etDomainInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && (event == null || event.action == KeyEvent.ACTION_UP)) {
                submitDomainInput()
                true
            } else {
                false
            }
        }
        rules.btnResetBuiltins.setOnClickListener { dispatch(ResetDisabledBuiltinDomains) }
        rules.etSearchDomains.doAfterTextChanged { text ->
            query = text?.toString().orEmpty()
            scheduleListBuild()
        }

        scope.launch {
            builtinDomains = repository.loadBuiltinDomains()
            if (isViewAlive()) render(latestConfig)
        }
    }

    /** Renders the website blocker toggle, counts, Reset button, and sorted/filtered domain list. */
    fun render(config: WebsiteBlockerConfig) {
        latestConfig = config
        val rules = binding.tabRules
        if (rules.switchWebsiteBlocker.isChecked != config.isEnabled) {
            rules.switchWebsiteBlocker.isChecked = config.isEnabled
        }
        rules.btnResetBuiltins.visibility =
            if (config.disabledBuiltinDomains.isNotEmpty()) View.VISIBLE else View.GONE

        val activeBuiltins = (builtinDomains.size - config.disabledBuiltinDomains.size).coerceAtLeast(0)
        val totalActive = activeBuiltins + config.userBlockedDomains.size
        val nf = NumberFormat.getIntegerInstance(Locale.getDefault())
        rules.tvWebsiteStats.text =
            context.getString(
                R.string.website_stats_fmt,
                nf.format(totalActive),
                nf.format(activeBuiltins),
                nf.format(config.userBlockedDomains.size),
                nf.format(config.whitelistedDomains.size),
            )
        scheduleListBuild()
    }

    private fun scheduleListBuild() {
        val snapshotConfig = latestConfig
        val snapshotBuiltins = builtinDomains
        val snapshotQuery = query.trim().lowercase(Locale.ROOT)
        renderJob?.cancel()
        renderJob =
            scope.launch {
                val grouped =
                    withContext(computeDispatcher) {
                        WebsiteListBuilder.build(context, snapshotConfig, snapshotBuiltins, snapshotQuery)
                    }
                if (!isViewAlive()) return@launch
                val rules = binding.tabRules
                adapter.submitList(grouped.rows)
                LetterJumpBarBinder.bind(
                    context,
                    rules.layoutJumpDomains,
                    rules.scrollJumpDomains,
                    grouped.letterPositions,
                    rules.rvDomains,
                )
                val empty = grouped.rows.isEmpty()
                rules.tvEmptyDomains.visibility = if (empty) View.VISIBLE else View.GONE
                rules.rvDomains.visibility = if (empty) View.GONE else View.VISIBLE
            }
    }

    private fun submitDomainInput() {
        val rules = binding.tabRules
        val raw = rules.etDomainInput.text?.toString().orEmpty()
        when (val result = DomainNormalizer.validateUserInput(raw)) {
            is DomainValidationResult.Invalid -> rules.tilAddDomain.error = result.reason
            is DomainValidationResult.Valid -> {
                rules.tilAddDomain.error = null
                val isAllow = rules.toggleDomainTarget.checkedButtonId == R.id.btn_domain_allow
                dispatch(
                    if (isAllow) {
                        AddWhitelistedDomain(result.domain)
                    } else {
                        AddUserBlockedDomain(result.domain)
                    },
                )
                rules.etDomainInput.setText("")
            }
        }
    }

    private fun handleRowAction(spec: RuleItemSpec) {
        when {
            spec.id.startsWith(WebsiteListBuilder.PREFIX_USER) -> {
                val domain = spec.id.removePrefix(WebsiteListBuilder.PREFIX_USER)
                dispatch(RemoveUserBlockedDomain(domain))
                showUndo(context.getString(R.string.removed_entry_fmt, domain)) {
                    dispatch(AddUserBlockedDomain(domain))
                }
            }
            spec.id.startsWith(WebsiteListBuilder.PREFIX_ALLOW) -> {
                val domain = spec.id.removePrefix(WebsiteListBuilder.PREFIX_ALLOW)
                dispatch(RemoveWhitelistedDomain(domain))
                showUndo(context.getString(R.string.removed_entry_fmt, domain)) {
                    dispatch(AddWhitelistedDomain(domain))
                }
            }
            spec.id.startsWith(WebsiteListBuilder.PREFIX_BUILTIN) -> {
                val domain = spec.id.removePrefix(WebsiteListBuilder.PREFIX_BUILTIN)
                dispatch(SetBuiltinDomainDisabled(domain, disabled = true))
                showUndo(context.getString(R.string.disabled_builtin_fmt, domain)) {
                    dispatch(SetBuiltinDomainDisabled(domain, disabled = false))
                }
            }
            spec.id.startsWith(WebsiteListBuilder.PREFIX_DISABLED) -> {
                val domain = spec.id.removePrefix(WebsiteListBuilder.PREFIX_DISABLED)
                dispatch(SetBuiltinDomainDisabled(domain, disabled = false))
            }
        }
    }

    private fun showUndo(
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
