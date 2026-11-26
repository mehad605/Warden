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
import com.warden.app.Warden
import com.warden.app.core.di.AppContainer
import com.warden.app.core.platform.LaunchableApp
import com.warden.app.data.models.Settings
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import com.warden.app.feature.backup.ui.BackupDialogController
import com.warden.app.feature.gatekeeper.domain.GatekeeperVerdict
import com.warden.app.feature.gatekeeper.ui.GatekeeperDialogController
import com.warden.app.feature.gatekeeper.ui.GatekeeperDialogMode
import com.warden.app.feature.security.ui.AntiUninstallUiController
import com.warden.app.feature.security.ui.PasswordDialogController
import com.warden.app.services.WardenService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main settings dashboard fragment wiring [FragmentKeywordBlockerBinding] to [BlockerViewModel]
 * and focused feature binders/controllers.
 */
class KeywordBlockerFragment : Fragment() {

    private var _binding: FragmentKeywordBlockerBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val container: AppContainer by lazy { (requireContext().applicationContext as Warden).container }
    private val viewModel: BlockerViewModel by activityViewModels { container.blockerViewModelFactory() }

    private var isUpdatingUi = false
    private var pendingExportJson: String? = null
    private var onImagePickedCallback: ((Uri) -> Unit)? = null

    private var keywordsBinder: KeywordsSectionBinder? = null
    private var geminiBinder: GeminiSectionBinder? = null
    private var securityBinder: SecurityAndBackupSectionBinder? = null
    private var antiUninstallController: AntiUninstallUiController? = null
    private var backupController: BackupDialogController? = null
    private var gatekeeperDialogs: GatekeeperDialogController? = null
    private var ignoredAppsBinder: IgnoredAppsListBinder? = null

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onImagePickedCallback?.invoke(it) }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && json != null) backupController?.writeExportedJson(uri, json)
        else if (uri != null) Toast.makeText(requireContext(), R.string.failed_to_export_data, Toast.LENGTH_SHORT).show()
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { backupController?.readImportJson(it, viewModel::importSettingsJson) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKeywordBlockerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pwdDialogs = initSecurityAndBackup()
        initBlockerBinders(pwdDialogs)
        initGatekeeperAndIgnoredApps()
        bindCoreListeners()
        observeSettings()
    }

    override fun onResume() {
        super.onResume()
        val accessibilityEnabled = container.accessibilityChecker.isServiceEnabled(WardenService::class.java)
        binding.cardAccessibilityWarning.visibility = if (accessibilityEnabled) View.GONE else View.VISIBLE
        antiUninstallController?.syncWithSystemState(viewModel.settings.value.antiUninstallEnabled)
        ignoredAppsBinder?.onResume()
    }

    override fun onPause() {
        super.onPause()
        ignoredAppsBinder?.stopCountdown()
    }

    override fun onDestroyView() {
        ignoredAppsBinder?.stopCountdown()
        keywordsBinder = null
        geminiBinder = null
        securityBinder = null
        antiUninstallController = null
        backupController = null
        gatekeeperDialogs = null
        ignoredAppsBinder = null
        _binding = null
        super.onDestroyView()
    }

    private fun initSecurityAndBackup(): PasswordDialogController {
        val ctx = requireContext()
        val pwdDialogs = PasswordDialogController(
            ctx, viewModel::verifyPassword,
            { viewModel.dispatch(BlockerAction.SetPassword(it)) },
            { viewModel.dispatch(BlockerAction.SetPassword(null)) }
        )
        val antiUninstall = AntiUninstallUiController(
            ctx, container.deviceAdminController, pwdDialogs, { startActivity(it) },
            { viewModel.dispatch(BlockerAction.SetAntiUninstallEnabled(it)) },
            { checked -> runWithoutListenerFeedback { binding.switchAntiUninstall.isChecked = checked } }
        )
        antiUninstallController = antiUninstall
        backupController = BackupDialogController(ctx, viewLifecycleOwner.lifecycleScope) {
            container.crashLogger.logNonFatalError(it)
        }
        securityBinder = SecurityAndBackupSectionBinder(
            ctx, binding, { viewModel.settings.value.passwordHash != null }, pwdDialogs, antiUninstall,
            { isUpdatingUi }, { viewModel.dispatch(BlockerAction.RecordAdminActivationRequest) },
            ::triggerExport, { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
        )
        return pwdDialogs
    }

    private fun initBlockerBinders(pwdDialogs: PasswordDialogController) {
        val ctx = requireContext()
        keywordsBinder = KeywordsSectionBinder(
            context = ctx,
            binding = binding,
            hasPassword = { viewModel.settings.value.passwordHash != null },
            passwordDialogs = pwdDialogs,
            dispatch = viewModel::dispatch
        )
        geminiBinder = GeminiSectionBinder(
            context = ctx,
            binding = binding,
            scope = viewLifecycleOwner.lifecycleScope,
            currentSettings = { viewModel.settings.value },
            dispatch = viewModel::dispatch,
            onRefreshModels = viewModel::refreshGeminiModels,
            onRequestTempIgnore = { openGatekeeperDialog(GatekeeperDialogMode.TEMPORARY_IGNORE) },
            isViewAlive = { _binding != null }
        )
    }

    private fun initGatekeeperAndIgnoredApps() {
        val ctx = requireContext()
        val viewScope = viewLifecycleOwner.lifecycleScope
        gatekeeperDialogs = GatekeeperDialogController(
            context = ctx,
            layoutInflater = layoutInflater,
            scope = viewScope,
            appsProvider = container.installedAppsProvider,
            requestPickImage = { callback ->
                onImagePickedCallback = callback
                selectImageLauncher.launch("image/*")
            },
            evaluateRequest = viewModel::evaluateGatekeeper,
            onApproved = ::applyGatekeeperApproval,
            onRejected = { viewModel.dispatch(BlockerAction.IncrementRejectionCount) }
        )
        ignoredAppsBinder = IgnoredAppsListBinder(
            context = ctx,
            layoutInflater = layoutInflater,
            scope = viewScope,
            cardContainer = binding.cardIgnoredAppsList,
            listContainer = binding.layoutIgnoredApps,
            appsProvider = container.installedAppsProvider,
            clock = container.clock,
            onRequestExtend = { pkg -> openGatekeeperDialog(GatekeeperDialogMode.EXTEND_IGNORE, pkg) },
            onDecreaseDuration = { pkg, mins -> viewModel.dispatch(BlockerAction.DecreaseTempExemption(pkg, mins)) },
            onPruneExpired = { viewModel.dispatch(BlockerAction.PruneExpiredTempApps) }
        )
    }

    private fun bindCoreListeners() {
        keywordsBinder?.bindListeners()
        geminiBinder?.bindListeners()
        securityBinder?.bindListeners()
        binding.btnEnableAccessibility.setOnClickListener {
            container.accessibilityChecker.openAccessibilitySettings(requireContext(), WardenService::class.java)
        }
        binding.switchEnableBlocker.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUi) return@setOnCheckedChangeListener
            if (!isChecked) {
                runWithoutListenerFeedback { binding.switchEnableBlocker.isChecked = true }
                openGatekeeperDialog(GatekeeperDialogMode.PAUSE_BLOCKER)
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
                viewModel.settings.collectLatest { renderSettings(it) }
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
            binding.tvGraceValue.text = if (settings.ignoreGracePeriodSeconds == 0) {
                getString(R.string.grace_period_off)
            } else {
                getString(R.string.grace_period_seconds_fmt, settings.ignoreGracePeriodSeconds)
            }
            binding.switchAntiUninstall.isChecked = settings.antiUninstallEnabled
        }
        keywordsBinder?.render(config.blockedKeywords, config.whitelistedKeywords)
        securityBinder?.render(settings)
        geminiBinder?.render(settings)
        ignoredAppsBinder?.render(config.ignoredApps, settings.temporaryIgnoredApps)
    }

    private fun openGatekeeperDialog(mode: GatekeeperDialogMode, targetPackage: String? = null) {
        val snapshot = viewModel.settings.value
        if (snapshot.geminiApiKey.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.configure_gemini_key_first, Toast.LENGTH_SHORT).show()
            return
        }
        gatekeeperDialogs?.show(
            mode = mode,
            permanentIgnoredApps = snapshot.keywordBlockerConfig.ignoredApps,
            rejectionCount = snapshot.noCount,
            targetPackage = targetPackage
        )
    }

    private fun applyGatekeeperApproval(
        mode: GatekeeperDialogMode,
        app: LaunchableApp?,
        isRemoval: Boolean,
        verdict: GatekeeperVerdict
    ) {
        val action: BlockerAction? = when (mode) {
            GatekeeperDialogMode.PAUSE_BLOCKER -> BlockerAction.SetBlockerActive(false)
            GatekeeperDialogMode.EXTEND_IGNORE -> app?.let { BlockerAction.ExtendTempExemption(it.packageName, verdict.durationMinutes) }
            GatekeeperDialogMode.TEMPORARY_IGNORE -> app?.let {
                if (isRemoval) BlockerAction.RemoveAppFromIgnoreLists(it.packageName)
                else BlockerAction.GrantTempExemption(it.packageName, verdict.durationMinutes)
            }
        }
        action?.let(viewModel::dispatch)
    }

    private fun triggerExport() {
        val snapshot = viewModel.settings.value
        backupController?.requestExport(snapshot.passwordHash != null, snapshot.geminiApiKey != null) { incPwd, incKey ->
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
    }
}
