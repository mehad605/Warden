package com.warden.app.feature.blocker.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.warden.app.services.WardenService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main settings dashboard fragment wiring [FragmentKeywordBlockerBinding] to [BlockerViewModel]
 * and [DashboardControllers].
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
        bindCoreListeners()
        observeSettings()
    }

    override fun onResume() {
        super.onResume()
        val enabled = container.accessibilityChecker.isServiceEnabled(WardenService::class.java)
        binding.cardAccessibilityWarning.visibility = if (enabled) View.GONE else View.VISIBLE
        controllers?.antiUninstallController?.syncWithSystemState(viewModel.settings.value.antiUninstallEnabled)
        controllers?.ignoredAppsBinder?.onResume()
    }

    override fun onPause() {
        super.onPause()
        controllers?.stopTimers()
    }

    override fun onDestroyView() {
        controllers?.stopTimers()
        controllers = null
        _binding = null
        super.onDestroyView()
    }

    private fun createHostCallbacks(): DashboardControllers.HostCallbacks =
        DashboardControllers.HostCallbacks(
            isUpdatingUi = { isUpdatingUi },
            isViewAlive = { _binding != null },
            syncAntiUninstallSwitch = { checked ->
                runWithoutListenerFeedback { binding.switchAntiUninstall.isChecked = checked }
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
        binding.btnEnableAccessibility.setOnClickListener {
            container.accessibilityChecker.openAccessibilitySettings(requireContext(), WardenService::class.java)
        }
        binding.switchEnableBlocker.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUi) return@setOnCheckedChangeListener
            if (!isChecked) {
                runWithoutListenerFeedback { binding.switchEnableBlocker.isChecked = true }
                openGatekeeperDialog(GatekeeperDialogMode.PAUSE_BLOCKER, null)
            } else {
                viewModel.dispatch(BlockerAction.SetBlockerActive(true))
                binding.tvBlockerBadge.visibility = View.VISIBLE
            }
        }
        binding.sliderGracePeriod.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.dispatch(BlockerAction.SetGracePeriodSeconds(value.toInt()))
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collectLatest(::renderSettings)
            }
        }
    }

    private fun renderSettings(settings: Settings) {
        val config = settings.keywordBlockerConfig
        runWithoutListenerFeedback {
            if (binding.switchEnableBlocker.isChecked != config.isActive) {
                binding.switchEnableBlocker.isChecked = config.isActive
            }
            binding.tvBlockerBadge.visibility = if (config.isActive) View.VISIBLE else View.GONE
            binding.sliderGracePeriod.value = settings.ignoreGracePeriodSeconds.toFloat()
            binding.tvGraceValue.text =
                if (settings.ignoreGracePeriodSeconds == 0) {
                    getString(R.string.grace_period_off)
                } else {
                    getString(R.string.grace_period_seconds_fmt, settings.ignoreGracePeriodSeconds)
                }
            binding.switchAntiUninstall.isChecked = settings.antiUninstallEnabled
        }
        controllers?.keywordsBinder?.render(config.blockedKeywords, config.whitelistedKeywords)
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
            return
        }
        controllers?.gatekeeperDialogs?.show(
            mode = mode,
            permanentIgnoredApps = snapshot.keywordBlockerConfig.ignoredApps,
            rejectionCount = snapshot.noCount,
            targetPackage = targetPackage,
        )
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
                    app?.let { BlockerAction.ExtendTempExemption(it.packageName, verdict.durationMinutes) }
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
        private const val DEFAULT_EXPORT_FILE = "Warden.json"
        private val IMPORT_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "*/*")
    }
}
