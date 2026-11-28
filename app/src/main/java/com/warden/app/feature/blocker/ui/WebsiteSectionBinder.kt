package com.warden.app.feature.blocker.ui

import android.content.Context
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.materialswitch.MaterialSwitch
import com.warden.app.R
import com.warden.app.data.models.WebsiteBlockProfile
import com.warden.app.data.models.WebsiteBlockerConfig
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import com.warden.app.feature.blocker.domain.RuleItemSpec
import com.warden.app.feature.security.ui.PasswordDialogController
import com.warden.app.feature.websites.data.PornDomainRepository
import com.warden.app.feature.websites.domain.DomainNormalizer
import com.warden.app.feature.websites.domain.DomainValidationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * Manages the Website Lists (profiles) UI tab: list overview with per-profile ON/OFF switches,
 * profile creation/renaming/deletion, and profile detail view with URL addition, search, letter
 * jump bar, and asymmetric password gates (turning ON & adding URLs are free; turning OFF,
 * removing URLs, disabling built-ins, and deleting profiles require the Settings Password).
 */
class WebsiteSectionBinder(
    private val context: Context,
    private val binding: FragmentKeywordBlockerBinding,
    private val scope: CoroutineScope,
    private val repository: PornDomainRepository,
    private val hasPassword: () -> Boolean,
    private val passwordDialogs: PasswordDialogController,
    private val isUpdatingUi: () -> Boolean,
    private val isViewAlive: () -> Boolean,
    private val dispatch: (BlockerAction) -> Unit,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var builtinDomains: List<String> = emptyList()
    private var latestConfig: WebsiteBlockerConfig = WebsiteBlockerConfig()
    private var lastRenderedConfig: WebsiteBlockerConfig? = null
    private var lastRenderedProfileId: String? = null
    private var lastBuiltinsCount: Int = -1
    private var selectedProfileId: String? = null
    private var pendingOpenProfileId: String? = null
    private var query: String = ""
    private var renderJob: Job? = null
    private var isSyncingSwitch: Boolean = false

    private val adapter = RuleListAdapter(onActionClick = ::handleRowAction)

    /** Wires profile list buttons, detail navigation, domain input, search, and lazy asset loading. */
    fun bindListeners() {
        val rules = binding.tabRules
        rules.rvDomains.layoutManager = LinearLayoutManager(context)
        rules.rvDomains.adapter = adapter

        rules.btnNewWebsiteProfile.setOnClickListener {
            WebsiteProfileDialogs.showCreateDialog(context) { name ->
                val newId = "custom_${UUID.randomUUID().toString().take(8)}"
                pendingOpenProfileId = newId
                dispatch(CreateWebsiteProfile(profileId = newId, name = name))
            }
        }
        rules.btnBackToProfiles.setOnClickListener { openProfile(null) }
        rules.btnResetBuiltins.setOnClickListener { dispatch(ResetDisabledBuiltinDomains) }
        rules.etSearchDomains.doAfterTextChanged { text ->
            query = text?.toString().orEmpty()
            scheduleDetailListBuild()
        }
        bindDetailProfileActions()
        WebsiteProfilesOverviewBinder.bindDomainInput(rules.tilAddDomain, rules.etDomainInput, ::submitDomainInput)

        scope.launch {
            builtinDomains = repository.loadBuiltinDomains()
            if (isViewAlive()) forceRender()
        }
    }

    /** Renders either the Website Lists overview or the currently selected profile's detail view. */
    fun render(config: WebsiteBlockerConfig) {
        latestConfig = config
        val unchanged =
            config == lastRenderedConfig &&
                selectedProfileId == lastRenderedProfileId &&
                builtinDomains.size == lastBuiltinsCount
        if (unchanged) return
        forceRender()
    }

    private fun forceRender() {
        val profiles = latestConfig.effectiveProfiles()
        val activeProfile = resolveActiveProfile(profiles)
        lastRenderedConfig = latestConfig
        lastRenderedProfileId = selectedProfileId
        lastBuiltinsCount = builtinDomains.size

        val rules = binding.tabRules
        val showingDetail = activeProfile != null
        rules.layoutWebsiteProfilesList.visibility = if (showingDetail) View.GONE else View.VISIBLE
        rules.layoutWebsiteProfileDetail.visibility = if (showingDetail) View.VISIBLE else View.GONE

        if (activeProfile != null) {
            renderProfileDetail(activeProfile)
        } else {
            WebsiteProfilesOverviewBinder.renderOverview(
                context = context,
                container = rules.layoutProfilesContainer,
                profiles = profiles,
                builtinCount = builtinDomains.size,
                callbacks =
                    ProfileRowCallbacks(
                        isSwitchMuted = { isUpdatingUi() || isSyncingSwitch },
                        syncSwitch = ::syncSwitch,
                        onToggleProfile = ::handleProfileToggle,
                        onOpenProfile = ::openProfile,
                    ),
            )
        }
    }

    private fun resolveActiveProfile(profiles: List<WebsiteBlockProfile>): WebsiteBlockProfile? {
        val pendingId = pendingOpenProfileId
        if (pendingId != null && profiles.any { it.id == pendingId }) {
            pendingOpenProfileId = null
            resetDetailInputs(pendingId)
        }
        val active = selectedProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
        if (selectedProfileId != null && active == null) selectedProfileId = null
        return active
    }

    private fun renderProfileDetail(profile: WebsiteBlockProfile) {
        val rules = binding.tabRules
        rules.tvSelectedProfileName.text = profile.name
        rules.btnRenameProfile.visibility = if (profile.isBuiltin) View.GONE else View.VISIBLE
        rules.btnDeleteProfile.visibility = if (profile.isBuiltin) View.GONE else View.VISIBLE
        rules.btnResetBuiltins.visibility =
            if (profile.isBuiltin && profile.disabledBuiltinDomains.isNotEmpty()) View.VISIBLE else View.GONE
        syncSwitch(rules.switchSelectedProfile, profile.isEnabled)
        rules.tvWebsiteStats.text = WebsiteProfilesOverviewBinder.formatDetailStats(context, profile, builtinDomains.size)
        scheduleDetailListBuild()
    }

    private fun bindDetailProfileActions() {
        val rules = binding.tabRules
        rules.switchSelectedProfile.setOnCheckedChangeListener { _, isChecked ->
            val id = selectedProfileId ?: return@setOnCheckedChangeListener
            if (isUpdatingUi() || isSyncingSwitch) return@setOnCheckedChangeListener
            handleProfileToggle(id, isChecked) { syncSwitch(rules.switchSelectedProfile, true) }
        }
        rules.btnRenameProfile.setOnClickListener {
            val profile = currentSelectedProfile() ?: return@setOnClickListener
            if (profile.isBuiltin) return@setOnClickListener
            WebsiteProfileDialogs.showRenameDialog(context, profile.name) { newName ->
                dispatch(RenameWebsiteProfile(profile.id, newName))
            }
        }
        rules.btnDeleteProfile.setOnClickListener {
            val profile = currentSelectedProfile() ?: return@setOnClickListener
            if (profile.isBuiltin) return@setOnClickListener
            runPasswordGated {
                dispatch(DeleteWebsiteProfile(profile.id))
                openProfile(null)
            }
        }
    }

    private fun openProfile(profileId: String?) {
        resetDetailInputs(profileId)
        forceRender()
    }

    private fun resetDetailInputs(profileId: String?) {
        selectedProfileId = profileId
        query = ""
        binding.tabRules.etSearchDomains.setText("")
        binding.tabRules.tilAddDomain.error = null
    }

    private fun handleProfileToggle(
        profileId: String,
        enable: Boolean,
        revertSwitchOnCancel: () -> Unit,
    ) {
        if (!enable && hasPassword()) {
            revertSwitchOnCancel()
            runPasswordGated { dispatch(SetWebsiteProfileEnabled(profileId, enabled = false)) }
        } else {
            dispatch(SetWebsiteProfileEnabled(profileId, enabled = enable))
        }
    }

    private fun submitDomainInput() {
        val profileId = selectedProfileId ?: return
        val rules = binding.tabRules
        val raw = rules.etDomainInput.text?.toString().orEmpty()
        when (val result = DomainNormalizer.validateUserInput(raw)) {
            is DomainValidationResult.Invalid -> rules.tilAddDomain.error = result.reason
            is DomainValidationResult.Valid -> {
                rules.tilAddDomain.error = null
                dispatch(AddDomainToProfile(profileId = profileId, rawDomain = result.domain))
                rules.etDomainInput.setText("")
            }
        }
    }

    private fun scheduleDetailListBuild() {
        val profile = currentSelectedProfile() ?: return
        val snapshotBuiltins = if (profile.isBuiltin) builtinDomains else emptyList()
        val snapshotQuery = query.trim().lowercase(Locale.ROOT)
        renderJob?.cancel()
        renderJob =
            scope.launch {
                val grouped =
                    withContext(computeDispatcher) {
                        WebsiteListBuilder.buildForProfile(context, profile, snapshotBuiltins, snapshotQuery)
                    }
                if (!isViewAlive() || selectedProfileId != profile.id) return@launch
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

    private fun handleRowAction(spec: RuleItemSpec) {
        val profileId = selectedProfileId ?: return
        when {
            spec.id.startsWith(WebsiteListBuilder.PREFIX_USER) -> {
                val domain = spec.id.removePrefix(WebsiteListBuilder.PREFIX_USER)
                runPasswordGated {
                    dispatch(RemoveDomainFromProfile(profileId, domain))
                    showUndo(context.getString(R.string.removed_entry_fmt, domain)) {
                        dispatch(AddDomainToProfile(profileId, domain))
                    }
                }
            }
            spec.id.startsWith(WebsiteListBuilder.PREFIX_BUILTIN) -> {
                val domain = spec.id.removePrefix(WebsiteListBuilder.PREFIX_BUILTIN)
                runPasswordGated {
                    dispatch(SetBuiltinDomainDisabled(domain, disabled = true))
                    showUndo(context.getString(R.string.disabled_builtin_fmt, domain)) {
                        dispatch(SetBuiltinDomainDisabled(domain, disabled = false))
                    }
                }
            }
            spec.id.startsWith(WebsiteListBuilder.PREFIX_DISABLED) -> {
                val domain = spec.id.removePrefix(WebsiteListBuilder.PREFIX_DISABLED)
                dispatch(SetBuiltinDomainDisabled(domain, disabled = false))
            }
        }
    }

    private fun currentSelectedProfile(): WebsiteBlockProfile? =
        selectedProfileId?.let { id -> latestConfig.effectiveProfiles().firstOrNull { it.id == id } }

    private fun syncSwitch(
        toggle: MaterialSwitch,
        checked: Boolean,
    ) {
        if (toggle.isChecked == checked) return
        isSyncingSwitch = true
        try {
            toggle.isChecked = checked
        } finally {
            isSyncingSwitch = false
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

    private fun showUndo(
        message: String,
        onUndo: () -> Unit,
    ) = WebsiteProfilesOverviewBinder.showUndoSnackbar(binding.root, binding.bottomNavContainer, message, onUndo)
}
