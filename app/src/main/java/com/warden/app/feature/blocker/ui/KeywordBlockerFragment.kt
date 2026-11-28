package com.warden.app.feature.blocker.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.warden.app.R
import com.warden.app.core.di.AppContainer
import com.warden.app.core.di.AppContainerProvider
import com.warden.app.core.platform.LaunchableApp
import com.warden.app.data.models.Settings
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import com.warden.app.feature.gatekeeper.domain.GatekeeperVerdict
import com.warden.app.feature.gatekeeper.ui.GatekeeperDialogMode
import com.warden.app.feature.onboarding.ui.OnboardingSpotlightController
import com.warden.app.services.WardenService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main Sentinel dashboard fragment wiring [FragmentKeywordBlockerBinding] (Shield / Rules / Vault),
 * [BlockerViewModel], [DashboardControllers], and [OnboardingSpotlightController].
 */
class KeywordBlockerFragment : Fragment() {
    private var _binding: FragmentKeywordBlockerBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val container: AppContainer by lazy { (requireContext().applicationContext as AppContainerProvider).container }
    private val viewModel: BlockerViewModel by activityViewModels {
        BlockerViewModel.Factory(container.settingsRepository, container.gatekeeperClient, container.clock)
    }

    private var isUpdatingUi = false
    private var pendingExportJson: String? = null
    private var onImagePickedCallback: ((Uri) -> Unit)? = null
    private var controllers: DashboardControllers? = null
    private var navigationBinder: SentinelNavigationBinder? = null
    private var spotlightController: OnboardingSpotlightController? = null

    private val selectImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { onImagePickedCallback?.invoke(it) }
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val json = pendingExportJson
            pendingExportJson = null
            if (uri != null && json != null) {
                controllers?.backupController?.writeExportedJson(uri, json)
            } else if (uri != null) {
                Toast.makeText(requireContext(), R.string.failed_to_export_data, Toast.LENGTH_SHORT).show()
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { controllers?.backupController?.readImportJson(it, viewModel::importSettingsJson) }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentKeywordBlockerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        controllers =
            DashboardControllers.create(
                context = requireContext(),
                layoutInflater = layoutInflater,
                scope = viewLifecycleOwner.lifecycleScope,
                binding = binding,
                container = container,
                viewModel = viewModel,
                host = createHostCallbacks(),
            )
        navigationBinder =
            SentinelNavigationBinder(
                context = requireContext(),
                binding = binding,
                onToggleShieldOrb = ::toggleShieldFromOrb,
                onOpenGuide = ::showOnboardingGuide,
            )
        bindCoreListeners()
        observeSettings()
        viewLifecycleOwner.lifecycleScope.launch { container.installedAppsProvider.loadLaunchableApps() }
        if (savedInstanceState == null && arguments?.getBoolean(ARG_SHOW_ONBOARDING, false) == true) {
            showOnboardingGuide()
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = container.accessibilityChecker.isServiceEnabled(WardenService::class.java)
        navigationBinder?.renderAccessibilityStatus(enabled)
        controllers?.antiUninstallController?.syncWithSystemState(viewModel.settings.value.antiUninstallEnabled)
        controllers?.ignoredAppsBinder?.onResume()
    }

    override fun onPause() {
        super.onPause()
        controllers?.stopTimers()
    }

    override fun onDestroyView() {
        spotlightController?.dismiss()
        spotlightController = null
        controllers?.stopTimers()
        controllers = null
        navigationBinder = null
        _binding = null
        super.onDestroyView()
    }

    private fun createHostCallbacks(): DashboardControllers.HostCallbacks =
        DashboardControllers.HostCallbacks(
            isUpdatingUi = { isUpdatingUi },
            isViewAlive = { _binding != null },
            syncAntiUninstallSwitch = { checked ->
                runWithoutListenerFeedback { binding.tabVault.switchAntiUninstall.isChecked = checked }
            },
            startActivity = { startActivity(it) },
            requestPickImage = { callback ->
                onImagePickedCallback = callback
                selectImageLauncher.launch("image/*")
            },
            openGatekeeper = ::openGatekeeperDialog,
            onGatekeeperApproved = ::applyGatekeeperApproval,
            onExportClicked = ::triggerExport,
            onImportClicked = { importLauncher.launch(IMPORT_MIME_TYPES) },
        )

    private fun bindCoreListeners() {
        controllers?.bindAllListeners()
        navigationBinder?.bindListeners()
        binding.tabShield.btnEnableAccessibility.setOnClickListener {
            container.accessibilityChecker.openAccessibilitySettings(requireContext(), WardenService::class.java)
        }
        binding.tabShield.switchEnableBlocker.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUi) return@setOnCheckedChangeListener
            handleBlockerToggle(isChecked)
        }
        binding.tabRules.sliderGracePeriod.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.dispatch(BlockerAction.SetGracePeriodSeconds(value.toInt()))
        }
    }

    private fun toggleShieldFromOrb() = handleBlockerToggle(!viewModel.settings.value.keywordBlockerConfig.isActive)

    private fun handleBlockerToggle(desiredActive: Boolean) {
        if (!desiredActive) {
            runWithoutListenerFeedback { binding.tabShield.switchEnableBlocker.isChecked = true }
            openGatekeeperDialog(GatekeeperDialogMode.PAUSE_BLOCKER, null)
        } else {
            viewModel.dispatch(BlockerAction.SetBlockerActive(true))
            binding.tabShield.tvBlockerBadge.visibility = View.VISIBLE
        }
    }

    private fun showOnboardingGuide() {
        val tabs = SentinelNavigationBinder.Tab.entries
        spotlightController?.dismiss()
        spotlightController =
            OnboardingSpotlightController(
                context = requireContext(),
                layoutInflater = layoutInflater,
                rootContainer = binding.spotlightOverlayHost,
                scrollContainer = binding.scrollContent,
                steps = SentinelSpotlightSteps.build(binding),
                onSelectTab = { index -> navigationBinder?.selectTab(tabs[index]) },
                isAccessibilityEnabled = { container.accessibilityChecker.isServiceEnabled(WardenService::class.java) },
                onOpenAccessibilitySettings = {
                    container.accessibilityChecker.openAccessibilitySettings(requireContext(), WardenService::class.java)
                },
            ).also { it.show() }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.settings.collectLatest(::renderSettings) }
        }
    }

    private fun renderSettings(settings: Settings) {
        val config = settings.keywordBlockerConfig
        runWithoutListenerFeedback {
            if (binding.tabShield.switchEnableBlocker.isChecked != config.isActive) {
                binding.tabShield.switchEnableBlocker.isChecked = config.isActive
            }
            binding.tabShield.tvBlockerBadge.visibility = if (config.isActive) View.VISIBLE else View.GONE
            binding.tabRules.sliderGracePeriod.value = settings.ignoreGracePeriodSeconds.toFloat()
            binding.tabRules.tvGraceValue.text =
                if (settings.ignoreGracePeriodSeconds == 0) {
                    getString(R.string.grace_period_off)
                } else {
                    getString(R.string.grace_period_seconds_fmt, settings.ignoreGracePeriodSeconds)
                }
            binding.tabVault.switchAntiUninstall.isChecked = settings.antiUninstallEnabled
        }
        navigationBinder?.renderSummary(settings)
        controllers?.keywordsBinder?.render(config.effectiveBlockedEntries(), config.whitelistedKeywords)
        controllers?.websitesBinder?.render(settings.websiteBlockerConfig)
        controllers?.securityBinder?.render(settings)
        controllers?.geminiBinder?.render(settings)
        controllers?.ignoredAppsBinder?.render(config.ignoredApps, settings.temporaryIgnoredApps)
    }

    private fun openGatekeeperDialog(
        mode: GatekeeperDialogMode,
        targetPackage: String?,
    ) {
        val snapshot = viewModel.settings.value
        if (snapshot.geminiApiKey.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.configure_gemini_key_first, Toast.LENGTH_SHORT).show()
            navigationBinder?.selectTab(SentinelNavigationBinder.Tab.VAULT)
            return
        }
        controllers?.gatekeeperDialogs?.show(mode, snapshot.keywordBlockerConfig.ignoredApps, snapshot.noCount, targetPackage)
    }

    private fun applyGatekeeperApproval(
        mode: GatekeeperDialogMode,
        app: LaunchableApp?,
        isRemoval: Boolean,
        verdict: GatekeeperVerdict,
    ) {
        val action: BlockerAction? =
            when (mode) {
                GatekeeperDialogMode.PAUSE_BLOCKER -> BlockerAction.SetBlockerActive(false)
                GatekeeperDialogMode.EXTEND_IGNORE ->
                    app?.let {
                        BlockerAction.ExtendTempExemption(
                            it.packageName,
                            verdict.durationMinutes,
                        )
                    }
                GatekeeperDialogMode.TEMPORARY_IGNORE ->
                    app?.let {
                        if (isRemoval) {
                            BlockerAction.RemoveAppFromIgnoreLists(it.packageName)
                        } else {
                            BlockerAction.GrantTempExemption(it.packageName, verdict.durationMinutes)
                        }
                    }
            }
        action?.let(viewModel::dispatch)
    }

    private fun triggerExport() {
        val snapshot = viewModel.settings.value
        controllers?.backupController?.requestExport(
            hasPassword = snapshot.passwordHash != null,
            hasApiKey = snapshot.geminiApiKey != null,
        ) { incPwd, incKey ->
            viewLifecycleOwner.lifecycleScope.launch {
                pendingExportJson = viewModel.exportSettingsJson(incPwd, incKey)
                exportLauncher.launch(DEFAULT_EXPORT_FILE)
            }
        }
    }

    private inline fun runWithoutListenerFeedback(block: () -> Unit) {
        isUpdatingUi = true
        try {
            block()
        } finally {
            isUpdatingUi = false
        }
    }

    companion object {
        const val FRAGMENT_ID = "keyword_blocker"
        private const val ARG_SHOW_ONBOARDING = "show_onboarding"
        private const val DEFAULT_EXPORT_FILE = "Warden.json"
        private val IMPORT_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "*/*")

        /** Creates a [KeywordBlockerFragment], optionally launching the onboarding guide once. */
        fun newInstance(showOnboarding: Boolean = false): KeywordBlockerFragment =
            KeywordBlockerFragment().apply {
                arguments = bundleOf(ARG_SHOW_ONBOARDING to showOnboarding)
            }
    }
}
