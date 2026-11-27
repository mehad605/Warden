package com.warden.app.feature.blocker.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.warden.app.R
import com.warden.app.data.models.Settings
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Binds the Gemini API key field (real value masked by native password toggle),
 * Save & Validate API Key controls, Material 3 model picker dialog, AI status banner,
 * and Shield's Request Temporary Pass button.
 */
class GeminiSectionBinder(
    private val context: Context,
    private val binding: FragmentKeywordBlockerBinding,
    private val scope: CoroutineScope,
    private val currentSettings: () -> Settings,
    private val dispatch: (BlockerAction) -> Unit,
    private val onValidateApiKey: suspend (String) -> GeminiValidationResult,
    private val onRefreshModels: suspend () -> List<String>,
    private val onRequestTempIgnore: () -> Unit,
    private val isViewAlive: () -> Boolean,
) {
    private var lastSyncedApiKey: String? = null
    private var hasSyncedKeyOnce = false
    private var isValidating = false
    private var lastValidationFailed = false

    /** Wires API key save/validate, model picker dialog, refresh, and temporary-pass button. */
    fun bindListeners() {
        val vault = binding.tabVault
        binding.tabShield.btnAskAiIgnore.setOnClickListener { onRequestTempIgnore() }
        vault.btnSaveApiKey.setOnClickListener { saveApiKeyInput() }
        vault.btnValidateApiKey.setOnClickListener { validateApiKeyInput() }
        vault.layoutModelPicker.setOnClickListener { openModelPicker() }
        vault.btnRefreshModels.setOnClickListener { refreshModels() }
        vault.etApiKey.doAfterTextChanged {
            lastValidationFailed = false
            renderValidateButton(currentSettings())
        }
    }

    /** Renders the Gemini API status banner, real masked key input, validation state, and model picker. */
    fun render(settings: Settings) {
        val vault = binding.tabVault
        val storedKey = settings.geminiApiKey.orEmpty()
        if (!hasSyncedKeyOnce || lastSyncedApiKey != storedKey) {
            hasSyncedKeyOnce = true
            lastSyncedApiKey = storedKey
            val currentText = vault.etApiKey.text?.toString().orEmpty()
            if (currentText != storedKey) {
                vault.etApiKey.setText(storedKey)
                vault.etApiKey.setSelection(storedKey.length)
            }
        }
        renderStatusBanner(settings)
        renderModelPicker(settings)
        renderValidateButton(settings)
        binding.tabShield.btnAskAiIgnore.isEnabled = true
    }

    private fun renderStatusBanner(settings: Settings) {
        val vault = binding.tabVault
        val hasKey = !settings.geminiApiKey.isNullOrBlank()
        val modelCount = settings.availableGeminiModels.size
        val isValidated = hasKey && modelCount > 0

        vault.btnRefreshModels.isEnabled = hasKey && !isValidating
        when {
            isValidating -> {
                setStatusBanner(R.string.api_status_validating, showCheck = false, isError = false, isValid = false)
            }
            lastValidationFailed -> {
                setStatusBanner(R.string.api_status_invalid, showCheck = false, isError = true, isValid = false)
            }
            isValidated -> {
                vault.ivApiStatusIcon.visibility = View.VISIBLE
                vault.tvApiStatus.text =
                    context.resources.getQuantityString(R.plurals.api_status_validated, modelCount, modelCount)
                applyStatusColors(isValid = true, isError = false)
            }
            hasKey -> {
                setStatusBanner(R.string.api_status_sync_models, showCheck = false, isError = false, isValid = false)
            }
            else -> {
                setStatusBanner(R.string.api_status_none, showCheck = false, isError = false, isValid = false)
            }
        }
    }

    private fun setStatusBanner(
        textRes: Int,
        showCheck: Boolean,
        isError: Boolean,
        isValid: Boolean,
    ) {
        val vault = binding.tabVault
        vault.ivApiStatusIcon.visibility = if (showCheck) View.VISIBLE else View.GONE
        vault.tvApiStatus.setText(textRes)
        applyStatusColors(isValid = isValid, isError = isError)
    }

    private fun applyStatusColors(
        isValid: Boolean,
        isError: Boolean,
    ) {
        val vault = binding.tabVault
        val bgColor =
            when {
                isError -> MaterialColors.getColor(vault.root, com.google.android.material.R.attr.colorErrorContainer)
                isValid -> ContextCompat.getColor(context, R.color.status_active_bg)
                else -> MaterialColors.getColor(vault.root, com.google.android.material.R.attr.colorSurfaceContainerHigh)
            }
        val textColor =
            when {
                isError -> MaterialColors.getColor(vault.root, com.google.android.material.R.attr.colorOnErrorContainer)
                else -> MaterialColors.getColor(vault.root, com.google.android.material.R.attr.colorOnSurface)
            }
        vault.layoutApiStatus.backgroundTintList = ColorStateList.valueOf(bgColor)
        vault.tvApiStatus.setTextColor(textColor)
    }

    private fun renderModelPicker(settings: Settings) {
        val vault = binding.tabVault
        val models = settings.availableGeminiModels
        val selected = settings.selectedGeminiModel
        val displayModel =
            when {
                models.isEmpty() -> context.getString(R.string.no_models_loaded)
                selected.isNotBlank() -> selected.removePrefix(MODELS_PREFIX)
                else -> models.first().removePrefix(MODELS_PREFIX)
            }
        vault.tvSelectedModel.text = displayModel
        val dotColor =
            if (models.isNotEmpty()) {
                ContextCompat.getColor(context, R.color.status_active)
            } else {
                MaterialColors.getColor(vault.root, com.google.android.material.R.attr.colorOutline)
            }
        vault.viewModelStatusDot.backgroundTintList = ColorStateList.valueOf(dotColor)
    }

    private fun renderValidateButton(settings: Settings) {
        val btn = binding.tabVault.btnValidateApiKey
        val typedKey = binding.tabVault.etApiKey.text?.toString()?.trim().orEmpty()
        val storedKey = settings.geminiApiKey.orEmpty()
        val isCurrentlyValidated =
            typedKey.isNotEmpty() && typedKey == storedKey && settings.availableGeminiModels.isNotEmpty()

        btn.isEnabled = !isValidating
        when {
            isValidating -> {
                btn.setText(R.string.validating_api_key)
                btn.icon = null
            }
            isCurrentlyValidated -> {
                btn.setText(R.string.api_key_validated_label)
                btn.icon = ContextCompat.getDrawable(context, R.drawable.baseline_done_24)
            }
            else -> {
                btn.setText(R.string.validate_api_key)
                btn.icon = null
            }
        }
    }

    private fun saveApiKeyInput() {
        val input = binding.tabVault.etApiKey.text?.toString()?.trim().orEmpty()
        lastValidationFailed = false
        val current = currentSettings().geminiApiKey.orEmpty()
        val toastRes =
            when {
                input == current -> R.string.api_key_unchanged
                input.isEmpty() -> {
                    dispatch(BlockerAction.SetGeminiApiKey(null))
                    R.string.api_key_removed
                }
                else -> {
                    dispatch(BlockerAction.SetGeminiApiKey(input))
                    R.string.api_key_saved
                }
            }
        Toast.makeText(context, toastRes, Toast.LENGTH_SHORT).show()
    }

    private fun validateApiKeyInput() {
        val input = binding.tabVault.etApiKey.text?.toString()?.trim().orEmpty()
        if (input.isEmpty()) {
            Toast.makeText(context, R.string.configure_api_key_first, Toast.LENGTH_SHORT).show()
            return
        }
        isValidating = true
        lastValidationFailed = false
        renderStatusBanner(currentSettings())
        renderValidateButton(currentSettings())
        scope.launch {
            val result = onValidateApiKey(input)
            if (!isViewAlive()) return@launch
            isValidating = false
            handleValidationResult(result)
            render(currentSettings())
        }
    }

    private fun handleValidationResult(result: GeminiValidationResult) {
        when (result) {
            is GeminiValidationResult.Valid -> {
                lastValidationFailed = false
                Toast.makeText(
                    context,
                    context.resources.getQuantityString(
                        R.plurals.api_key_validated_toast,
                        result.models.size,
                        result.models.size,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            GeminiValidationResult.InvalidOrUnreachable -> {
                lastValidationFailed = true
                Toast.makeText(context, R.string.api_key_invalid_toast, Toast.LENGTH_LONG).show()
            }
            GeminiValidationResult.Cleared -> {
                lastValidationFailed = false
                Toast.makeText(context, R.string.api_key_removed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openModelPicker() {
        val settings = currentSettings()
        val models = settings.availableGeminiModels
        if (models.isEmpty()) {
            val typedKey = binding.tabVault.etApiKey.text?.toString()?.trim().orEmpty()
            if (typedKey.isNotEmpty()) {
                validateApiKeyInput()
            } else {
                Toast.makeText(context, R.string.configure_api_key_first, Toast.LENGTH_SHORT).show()
            }
            return
        }
        val displayNames = models.map { it.removePrefix(MODELS_PREFIX) }.toTypedArray()
        val checkedIndex = models.indexOf(settings.selectedGeminiModel).coerceAtLeast(0)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.choose_gemini_model_title)
            .setSingleChoiceItems(displayNames, checkedIndex) { dialog, which ->
                if (which in models.indices) {
                    dispatch(BlockerAction.SelectGeminiModel(models[which]))
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshModels() {
        val input = binding.tabVault.etApiKey.text?.toString()?.trim().orEmpty()
        if (input.isEmpty() && currentSettings().geminiApiKey.isNullOrEmpty()) {
            Toast.makeText(context, R.string.configure_api_key_first, Toast.LENGTH_SHORT).show()
            return
        }
        isValidating = true
        lastValidationFailed = false
        renderStatusBanner(currentSettings())
        renderValidateButton(currentSettings())
        Toast.makeText(context, R.string.refreshing_models, Toast.LENGTH_SHORT).show()
        scope.launch {
            val models = onRefreshModels()
            if (!isViewAlive()) return@launch
            isValidating = false
            lastValidationFailed = models.isEmpty()
            render(currentSettings())
            val toastRes = if (models.isNotEmpty()) R.string.models_refreshed else R.string.api_key_invalid_toast
            Toast.makeText(context, toastRes, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val MODELS_PREFIX = "models/"
    }
}
