package com.warden.app.feature.blocker.ui

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import com.warden.app.R
import com.warden.app.data.models.Settings
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Binds the Gemini API key field, model selector spinner, refresh button, and AI status banner.
 */
class GeminiSectionBinder(
    private val context: Context,
    private val binding: FragmentKeywordBlockerBinding,
    private val scope: CoroutineScope,
    private val currentSettings: () -> Settings,
    private val dispatch: (BlockerAction) -> Unit,
    private val onRefreshModels: suspend () -> List<String>,
    private val onRequestTempIgnore: () -> Unit,
    private val isViewAlive: () -> Boolean,
) {
    private var lastRenderedModels: List<String> = emptyList()
    private var isUpdatingSpinner = false

    /** Wires API key save, model refresh, spinner selection, and exemption entry listeners. */
    fun bindListeners() {
        val vault = binding.tabVault
        val shield = binding.tabShield
        vault.btnSaveApiKey.setOnClickListener { saveApiKeyInput() }
        shield.cardExemption.setOnClickListener { onRequestTempIgnore() }
        shield.btnAskAiIgnore.setOnClickListener { onRequestTempIgnore() }
        vault.btnRefreshModels.setOnClickListener { refreshModels() }
        vault.spinnerGeminiModels.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    if (isUpdatingSpinner) return
                    val models = currentSettings().availableGeminiModels
                    if (position in models.indices) {
                        dispatch(BlockerAction.SelectGeminiModel(models[position]))
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    /** Renders the Gemini API status banner, masked key input, and model dropdown from [settings]. */
    fun render(settings: Settings) {
        val vault = binding.tabVault
        val models = settings.availableGeminiModels
        val cleanNames = models.map { it.removePrefix("models/") }
        isUpdatingSpinner = true
        try {
            if (models != lastRenderedModels) {
                lastRenderedModels = models
                val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, cleanNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                vault.spinnerGeminiModels.adapter = adapter
            }
            val selectedIndex = cleanNames.indexOf(settings.selectedGeminiModel.removePrefix("models/"))
            if (selectedIndex >= 0 && vault.spinnerGeminiModels.selectedItemPosition != selectedIndex) {
                vault.spinnerGeminiModels.setSelection(selectedIndex)
            }
            renderStatusBanner(hasKey = !settings.geminiApiKey.isNullOrEmpty(), hasModels = models.isNotEmpty())
        } finally {
            isUpdatingSpinner = false
        }
    }

    private fun renderStatusBanner(
        hasKey: Boolean,
        hasModels: Boolean,
    ) {
        val vault = binding.tabVault
        if (hasKey) {
            vault.ivApiStatusIcon.visibility = View.VISIBLE
            vault.btnRefreshModels.isEnabled = true
            vault.etApiKey.setText(MASKED_API_KEY)
            vault.tvApiStatus.setText(
                if (hasModels) R.string.api_status_active else R.string.api_status_sync_models,
            )
            binding.tabShield.btnAskAiIgnore.isEnabled = hasModels
        } else {
            vault.ivApiStatusIcon.visibility = View.GONE
            vault.tvApiStatus.setText(R.string.api_status_none)
            vault.etApiKey.setText("")
            binding.tabShield.btnAskAiIgnore.isEnabled = false
            vault.btnRefreshModels.isEnabled = false
        }
    }

    private fun saveApiKeyInput() {
        val input = binding.tabVault.etApiKey.text?.toString()?.trim().orEmpty()
        val toastRes =
            when {
                input == MASKED_API_KEY -> R.string.api_key_unchanged
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

    private fun refreshModels() {
        if (currentSettings().geminiApiKey.isNullOrEmpty()) {
            Toast.makeText(context, R.string.configure_api_key_first, Toast.LENGTH_SHORT).show()
            return
        }
        binding.tabVault.btnRefreshModels.isEnabled = false
        Toast.makeText(context, R.string.refreshing_models, Toast.LENGTH_SHORT).show()
        scope.launch {
            onRefreshModels()
            if (isViewAlive()) {
                binding.tabVault.btnRefreshModels.isEnabled = true
                Toast.makeText(context, R.string.models_refreshed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val MASKED_API_KEY = "***********"
    }
}
