package com.warden.app.feature.blocker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import com.warden.app.core.di.AppContainer
import com.warden.app.core.platform.LaunchableApp
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import com.warden.app.feature.backup.ui.BackupDialogController
import com.warden.app.feature.gatekeeper.domain.GatekeeperVerdict
import com.warden.app.feature.gatekeeper.ui.GatekeeperDialogController
import com.warden.app.feature.gatekeeper.ui.GatekeeperDialogMode
import com.warden.app.feature.security.ui.AntiUninstallUiController
import com.warden.app.feature.security.ui.PasswordDialogController
import kotlinx.coroutines.CoroutineScope

/**
 * Bundles the view-scoped binders and dialog controllers used by [KeywordBlockerFragment]
 * so the fragment remains concise and focused on lifecycle wiring.
 */
internal class DashboardControllers(
    val keywordsBinder: KeywordsSectionBinder,
    val geminiBinder: GeminiSectionBinder,
    val securityBinder: SecurityAndBackupSectionBinder,
    val antiUninstallController: AntiUninstallUiController,
    val backupController: BackupDialogController,
    val gatekeeperDialogs: GatekeeperDialogController,
    val ignoredAppsBinder: IgnoredAppsListBinder,
) {
    /** Host callbacks supplied by [KeywordBlockerFragment]. */
    data class HostCallbacks(
        val isUpdatingUi: () -> Boolean,
        val isViewAlive: () -> Boolean,
        val syncAntiUninstallSwitch: (Boolean) -> Unit,
        val startActivity: (Intent) -> Unit,
        val requestPickImage: ((Uri) -> Unit) -> Unit,
        val openGatekeeper: (GatekeeperDialogMode, String?) -> Unit,
        val onGatekeeperApproved: (GatekeeperDialogMode, LaunchableApp?, Boolean, GatekeeperVerdict) -> Unit,
        val onExportClicked: () -> Unit,
        val onImportClicked: () -> Unit,
    )

    /** Binds all section listeners in one call. */
    fun bindAllListeners() {
        keywordsBinder.bindListeners()
        geminiBinder.bindListeners()
        securityBinder.bindListeners()
    }

    /** Stops active timers when the view pauses or is destroyed. */
    fun stopTimers() {
        ignoredAppsBinder.stopCountdown()
    }

    companion object {
        /** Instantiates all view-scoped dashboard binders and controllers. */
        fun create(
            context: Context,
            layoutInflater: LayoutInflater,
            scope: CoroutineScope,
            binding: FragmentKeywordBlockerBinding,
            container: AppContainer,
            viewModel: BlockerViewModel,
            host: HostCallbacks,
        ): DashboardControllers {
            val pwdDialogs = createPasswordDialogs(context, viewModel)
            val antiUninstall = createAntiUninstallController(context, container, pwdDialogs, viewModel, host)
            val backup = BackupDialogController(context, scope, onError = container.crashLogger::logNonFatalError)
            return DashboardControllers(
                keywordsBinder = createKeywordsBinder(context, binding, pwdDialogs, viewModel),
                geminiBinder = createGeminiBinder(context, binding, scope, viewModel, host),
                securityBinder = createSecurityBinder(context, binding, pwdDialogs, antiUninstall, viewModel, host),
                antiUninstallController = antiUninstall,
                backupController = backup,
                gatekeeperDialogs = createGatekeeperDialogs(context, layoutInflater, scope, container, viewModel, host),
                ignoredAppsBinder = createIgnoredAppsBinder(context, layoutInflater, scope, binding, container, viewModel, host),
            )
        }

        private fun createPasswordDialogs(
            context: Context,
            viewModel: BlockerViewModel,
        ): PasswordDialogController =
            PasswordDialogController(
                context = context,
                verifyPassword = viewModel::verifyPassword,
                onSetPassword = { viewModel.dispatch(BlockerAction.SetPassword(it)) },
                onRemovePassword = { viewModel.dispatch(BlockerAction.SetPassword(null)) },
            )

        private fun createAntiUninstallController(
            context: Context,
            container: AppContainer,
            pwdDialogs: PasswordDialogController,
            viewModel: BlockerViewModel,
            host: HostCallbacks,
        ): AntiUninstallUiController =
            AntiUninstallUiController(
                context = context,
                deviceAdminController = container.deviceAdminController,
                passwordDialogs = pwdDialogs,
                onRequestAdminActivation = host.startActivity,
                onSetEnabled = { viewModel.dispatch(BlockerAction.SetAntiUninstallEnabled(it)) },
                onSyncSwitchVisual = host.syncAntiUninstallSwitch,
            )

        private fun createKeywordsBinder(
            context: Context,
            binding: FragmentKeywordBlockerBinding,
            pwdDialogs: PasswordDialogController,
            viewModel: BlockerViewModel,
        ): KeywordsSectionBinder =
            KeywordsSectionBinder(
                context = context,
                binding = binding,
                hasPassword = { viewModel.settings.value.passwordHash != null },
                passwordDialogs = pwdDialogs,
                dispatch = viewModel::dispatch,
            )

        private fun createGeminiBinder(
            context: Context,
            binding: FragmentKeywordBlockerBinding,
            scope: CoroutineScope,
            viewModel: BlockerViewModel,
            host: HostCallbacks,
        ): GeminiSectionBinder =
            GeminiSectionBinder(
                context = context,
                binding = binding,
                scope = scope,
                currentSettings = { viewModel.settings.value },
                dispatch = viewModel::dispatch,
                onRefreshModels = viewModel::refreshGeminiModels,
                onRequestTempIgnore = { host.openGatekeeper(GatekeeperDialogMode.TEMPORARY_IGNORE, null) },
                isViewAlive = host.isViewAlive,
            )

        private fun createSecurityBinder(
            context: Context,
            binding: FragmentKeywordBlockerBinding,
            pwdDialogs: PasswordDialogController,
            antiUninstall: AntiUninstallUiController,
            viewModel: BlockerViewModel,
            host: HostCallbacks,
        ): SecurityAndBackupSectionBinder =
            SecurityAndBackupSectionBinder(
                context = context,
                binding = binding,
                hasPassword = { viewModel.settings.value.passwordHash != null },
                passwordDialogs = pwdDialogs,
                antiUninstallController = antiUninstall,
                isUpdatingUi = host.isUpdatingUi,
                onRecordAdminActivation = { viewModel.dispatch(BlockerAction.RecordAdminActivationRequest) },
                onExportClicked = host.onExportClicked,
                onImportClicked = host.onImportClicked,
            )

        private fun createGatekeeperDialogs(
            context: Context,
            layoutInflater: LayoutInflater,
            scope: CoroutineScope,
            container: AppContainer,
            viewModel: BlockerViewModel,
            host: HostCallbacks,
        ): GatekeeperDialogController =
            GatekeeperDialogController(
                context = context,
                layoutInflater = layoutInflater,
                scope = scope,
                appsProvider = container.installedAppsProvider,
                requestPickImage = host.requestPickImage,
                evaluateRequest = viewModel::evaluateGatekeeper,
                onApproved = host.onGatekeeperApproved,
                onRejected = { viewModel.dispatch(BlockerAction.IncrementRejectionCount) },
            )

        private fun createIgnoredAppsBinder(
            context: Context,
            layoutInflater: LayoutInflater,
            scope: CoroutineScope,
            binding: FragmentKeywordBlockerBinding,
            container: AppContainer,
            viewModel: BlockerViewModel,
            host: HostCallbacks,
        ): IgnoredAppsListBinder =
            IgnoredAppsListBinder(
                context = context,
                layoutInflater = layoutInflater,
                scope = scope,
                cardContainer = binding.tabShield.cardIgnoredAppsList,
                listContainer = binding.tabShield.layoutIgnoredApps,
                appsProvider = container.installedAppsProvider,
                clock = container.clock,
                callbacks =
                    IgnoredAppsListBinder.Callbacks(
                        onRequestExtend = { pkg -> host.openGatekeeper(GatekeeperDialogMode.EXTEND_IGNORE, pkg) },
                        onDecreaseDuration = { pkg, mins -> viewModel.dispatch(BlockerAction.DecreaseTempExemption(pkg, mins)) },
                        onPruneExpired = { viewModel.dispatch(BlockerAction.PruneExpiredTempApps) },
                    ),
            )
    }
}
