package com.warden.app.ui.fragments.main.reducers.blockertools.keywordBlocker

import com.warden.app.R

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.core.content.ContextCompat
import android.graphics.drawable.Drawable
import android.widget.LinearLayout
import android.widget.TextView
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import com.warden.app.databinding.DialogAiChatBinding
import com.warden.app.databinding.ItemIgnoredAppBinding
import com.warden.app.utils.PermissionUtils
import com.warden.app.services.WardenService
import com.warden.app.utils.GeminiManager
import com.warden.app.receivers.WardenDeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import java.util.Locale

class KeywordBlockerFragment : Fragment() {

    private var _binding: FragmentKeywordBlockerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: KeywordBlockerViewModel by activityViewModels()
    private var isUpdatingUi = false

    private var currentIgnoredApps = listOf<String>()
    private val appInfoCache = mutableMapOf<String, Pair<String, Drawable>>()
    private var countdownJob: Job? = null

    private var onImageAttachedCallback: ((Uri) -> Unit)? = null

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImageAttachedCallback?.invoke(uri)
        }
    }



    // Intent launcher to create (export) the JSON document
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportSettings { json ->
                try {
                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    }
                    Toast.makeText(requireContext(), getString(R.string.state_exported), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), getString(R.string.failed_to_export_data), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Intent launcher to open (import) the JSON document
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    val json = inputStream.bufferedReader().use { it.readText() }
                    viewModel.importSettings(json) { success ->
                        if (success) {
                            Toast.makeText(requireContext(), getString(R.string.state_imported), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.failed_to_import), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), getString(R.string.failed_to_import), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeywordBlockerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityPermission()
        startCountdownTimer()
        syncAntiUninstallState()
    }

    private fun syncAntiUninstallState() {
        try {
            val dpm = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(requireContext(), WardenDeviceAdminReceiver::class.java)
            val isCurrentAdminActive = dpm.isAdminActive(adminComponent)
            if (isCurrentAdminActive != viewModel.antiUninstallEnabled.value) {
                viewModel.setAntiUninstallEnabled(isCurrentAdminActive)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        countdownJob?.cancel()
    }

    private fun checkAccessibilityPermission() {
        val isEnabled = PermissionUtils.isAccessibilityServiceEnabled(requireContext(), WardenService::class.java)
        if (isEnabled) {
            binding.cardAccessibilityWarning.visibility = View.GONE
        } else {
            binding.cardAccessibilityWarning.visibility = View.VISIBLE
        }
    }

    private fun setupListeners() {
        binding.btnEnableAccessibility.setOnClickListener {
            PermissionUtils.openAccessibilityServiceScreen(requireContext(), WardenService::class.java)
        }

        binding.switchEnableBlocker.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                if (!isChecked) {
                    isUpdatingUi = true
                    binding.switchEnableBlocker.isChecked = true
                    isUpdatingUi = false
                    showAiChatDialog(DialogType.PAUSE_BLOCKER)
                } else {
                    viewModel.setIsActive(true)
                }
            }
        }

        binding.switchAntiUninstall.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                val context = requireContext()
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, WardenDeviceAdminReceiver::class.java)
                val isAdminActive = dpm.isAdminActive(adminComponent)

                if (isChecked) {
                    if (!isAdminActive) {
                        isUpdatingUi = true
                        binding.switchAntiUninstall.isChecked = false
                        isUpdatingUi = false

                        viewModel.setDeviceAdminActivationRequestedAt(System.currentTimeMillis())

                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Enable this to prevent Block Words from being uninstalled without permission."
                            )
                        }
                        startActivity(intent)
                    } else {
                        viewModel.setAntiUninstallEnabled(true)
                    }
                } else {
                    isUpdatingUi = true
                    binding.switchAntiUninstall.isChecked = true
                    isUpdatingUi = false

                    val passwordHash = viewModel.passwordHash.value
                    if (passwordHash != null) {
                        showPasswordPromptDialog(
                            title = "Disable Anti-Uninstall",
                            message = "Enter your password to deactivate anti-uninstall protection."
                        ) {
                            try {
                                dpm.removeActiveAdmin(adminComponent)
                                viewModel.setAntiUninstallEnabled(false)
                                isUpdatingUi = true
                                binding.switchAntiUninstall.isChecked = false
                                isUpdatingUi = false
                                Toast.makeText(context, "Anti-Uninstall protection disabled.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error disabling: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        MaterialAlertDialogBuilder(context)
                            .setTitle("Disable Anti-Uninstall")
                            .setMessage("Are you sure you want to disable anti-uninstall protection? Your app can now be uninstalled normally.")
                            .setPositiveButton("Disable") { dialog, _ ->
                                try {
                                    dpm.removeActiveAdmin(adminComponent)
                                    viewModel.setAntiUninstallEnabled(false)
                                    isUpdatingUi = true
                                    binding.switchAntiUninstall.isChecked = false
                                    isUpdatingUi = false
                                    Toast.makeText(context, "Anti-Uninstall protection disabled.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error disabling: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                dialog.dismiss()
                            }
                            .setNegativeButton("Cancel") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    }
                }
            }
        }

        binding.btnAddKeyword.setOnClickListener {
            var keyword = binding.etKeyword.text.toString()
            if (keyword.isNotBlank()) {
                if (Patterns.WEB_URL.matcher(keyword).matches()) {
                    keyword = keyword
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .removePrefix("www.")
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.warning_link_blocker_may_not_work),
                        Toast.LENGTH_LONG
                    ).show()
                }
                viewModel.addKeyword(keyword)
                binding.etKeyword.setText("")
            }
        }



        binding.sliderGracePeriod.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setIgnoreGracePeriodSeconds(value.toInt())
            }
        }

        binding.btnSaveApiKey.setOnClickListener {
            val input = binding.etApiKey.text?.toString()?.trim() ?: ""
            if (input == "***********") {
                Toast.makeText(requireContext(), "API Key kept unchanged.", Toast.LENGTH_SHORT).show()
            } else if (input.isEmpty()) {
                viewModel.setGeminiApiKey(null)
                Toast.makeText(requireContext(), "API Key removed.", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.setGeminiApiKey(input)
                Toast.makeText(requireContext(), "API Key saved.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAskAiIgnore.setOnClickListener {
            showAiChatDialog(DialogType.TEMPORARY_IGNORE)
        }

        binding.spinnerGeminiModels.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingUi) {
                    val models = viewModel.availableGeminiModels.value
                    if (position >= 0 && position < models.size) {
                        viewModel.setSelectedGeminiModel(models[position])
                    }
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.btnRefreshModels.setOnClickListener {
            val apiKey = viewModel.geminiApiKey.value
            if (apiKey.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Please configure an API key first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.btnRefreshModels.isEnabled = false
            Toast.makeText(requireContext(), "Refreshing model list...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val list = GeminiManager.fetchModelsList(apiKey)
                viewModel.setAvailableGeminiModels(list)
                binding.btnRefreshModels.isEnabled = true
                Toast.makeText(requireContext(), "Model list refreshed!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSetPassword.setOnClickListener {
            showSetPasswordDialog()
        }

        binding.btnRemovePassword.setOnClickListener {
            showRemovePasswordDialog()
        }

        binding.btnExportSettings.setOnClickListener {
            exportLauncher.launch("BlockWords.json")
        }

        binding.btnImportSettings.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.keywordBlockerConfig.collectLatest { config ->
                isUpdatingUi = true

                if (binding.switchEnableBlocker.isChecked != config.isActive) {
                    binding.switchEnableBlocker.isChecked = config.isActive
                }

                currentIgnoredApps = config.ignoredApps
                updateIgnoredAppsList()

                updateKeywordsList(config.blockedKeywords)

                isUpdatingUi = false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.passwordHash.collectLatest { hash ->
                if (hash != null) {
                    binding.tvPasswordStatus.text = "Password protection is active (required for keyword removal)"
                    binding.btnSetPassword.text = getString(R.string.change_password)
                    binding.btnRemovePassword.visibility = View.VISIBLE
                } else {
                    binding.tvPasswordStatus.text = "No password set (keywords can be deleted freely)"
                    binding.btnSetPassword.text = getString(R.string.set_password)
                    binding.btnRemovePassword.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.geminiApiKey.collectLatest { _ ->
                updateAiButtonsState()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ignoreGracePeriodSeconds.collectLatest { seconds ->
                isUpdatingUi = true
                binding.sliderGracePeriod.value = seconds.toFloat()
                binding.tvGracePeriodTitle.text = "Blocker Grace Period: ${seconds}s"
                isUpdatingUi = false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.availableGeminiModels.collectLatest { models ->
                isUpdatingUi = true
                val cleanNames = models.map { it.removePrefix("models/") }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, cleanNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerGeminiModels.adapter = adapter

                val currentSelected = viewModel.selectedGeminiModel.value.removePrefix("models/")
                val index = cleanNames.indexOf(currentSelected)
                if (index >= 0) {
                    binding.spinnerGeminiModels.setSelection(index)
                }
                isUpdatingUi = false
                updateAiButtonsState()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedGeminiModel.collectLatest { model ->
                val models = viewModel.availableGeminiModels.value
                val cleanNames = models.map { it.removePrefix("models/") }
                val currentSelected = model.removePrefix("models/")
                val index = cleanNames.indexOf(currentSelected)
                if (index >= 0 && binding.spinnerGeminiModels.adapter != null) {
                    isUpdatingUi = true
                    binding.spinnerGeminiModels.setSelection(index)
                    isUpdatingUi = false
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.temporaryIgnoredApps.collectLatest {
                updateIgnoredAppsList()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.antiUninstallEnabled.collectLatest { enabled ->
                isUpdatingUi = true
                binding.switchAntiUninstall.isChecked = enabled
                isUpdatingUi = false
            }
        }
    }

    private fun updateAiButtonsState() {
        val key = viewModel.geminiApiKey.value
        val models = viewModel.availableGeminiModels.value
        val hasKey = !key.isNullOrEmpty()
        val hasModels = models.isNotEmpty()

        isUpdatingUi = true
        if (hasKey) {
            binding.btnRefreshModels.isEnabled = true
            if (hasModels) {
                binding.tvApiStatus.text = "API Key is configured. AI Gatekeeper features are enabled."
                binding.etApiKey.setText("***********")
                binding.btnAskAiIgnore.isEnabled = true
            } else {
                binding.tvApiStatus.text = "API Key is configured. Tap Sync/Refresh icon to sync available models before using AI features."
                binding.etApiKey.setText("***********")
                binding.btnAskAiIgnore.isEnabled = false
            }
        } else {
            binding.tvApiStatus.text = "No API Key configured. AI Gatekeeper features are disabled."
            binding.etApiKey.setText("")
            binding.btnAskAiIgnore.isEnabled = false
            binding.btnRefreshModels.isEnabled = false
        }
        isUpdatingUi = false
    }

    private fun updateKeywordsList(keywords: List<String>) {
        binding.cgKeywords.removeAllViews()
        for (keyword in keywords) {
            val chip = Chip(requireContext()).apply {
                text = keyword
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    val passwordHash = viewModel.passwordHash.value
                    if (passwordHash != null) {
                        showPasswordPromptDialog(
                            title = getString(R.string.password_prompt_title),
                            message = getString(R.string.password_required_to_delete)
                        ) {
                            viewModel.removeKeyword(keyword)
                            Toast.makeText(requireContext(), "Keyword removed!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        viewModel.removeKeyword(keyword)
                    }
                }
            }
            binding.cgKeywords.addView(chip)
        }
    }

    private fun showPasswordPromptDialog(title: String, message: String, onVerified: () -> Unit) {
        val context = requireContext()
        val inputLayout = TextInputLayout(context).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(24, 8, 24, 8)
            hint = context.getString(R.string.enter_password)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val editText = TextInputEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(inputLayout)
            .setPositiveButton(context.getString(R.string.proceed)) { dialog, _ ->
                val enteredPassword = editText.text.toString()
                if (viewModel.verifyPassword(enteredPassword)) {
                    onVerified()
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, context.getString(R.string.incorrect_password), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(context.getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun isPasswordStrong(password: String): Boolean {
        if (password.length < 6) return false
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        return hasLetter && hasDigit
    }

    private fun showSetPasswordDialog() {
        val oldHash = viewModel.passwordHash.value
        if (oldHash != null) {
            showPasswordPromptDialog(
                title = "Verify Old Password",
                message = "Enter your current password to set a new one"
            ) {
                showNewPasswordInputFragment()
            }
        } else {
            showNewPasswordInputFragment()
        }
    }

    private fun showNewPasswordInputFragment() {
        val context = requireContext()
        val inputLayout = TextInputLayout(context).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(24, 8, 24, 8)
            hint = context.getString(R.string.password_hint)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            helperText = context.getString(R.string.strong_password_rules)
        }
        val editText = TextInputEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.set_password))
            .setView(inputLayout)
            .setPositiveButton(context.getString(R.string.save), null)
            .setNegativeButton(context.getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .create().apply {
                setOnShowListener {
                    val button = getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val newPassword = editText.text.toString()
                        if (isPasswordStrong(newPassword)) {
                            viewModel.setPassword(newPassword)
                            Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                            dismiss()
                        } else {
                            inputLayout.error = context.getString(R.string.strong_password_rules)
                        }
                    }
                }
            }.show()
    }

    private fun showRemovePasswordDialog() {
        showPasswordPromptDialog(
            title = "Remove Password",
            message = "Enter your password to disable protection"
        ) {
            viewModel.removePassword()
            Toast.makeText(requireContext(), requireContext().getString(R.string.password_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAiChatDialog(type: DialogType, targetPackage: String? = null) {
        val apiKey = viewModel.geminiApiKey.value
        if (apiKey.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please configure a Gemini API key first.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogAiChatBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        var attachedBitmap: Bitmap? = null

        if (type != DialogType.PAUSE_BLOCKER) {
            val loadingAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("Loading installed apps..."))
            dialogBinding.spinnerApps.adapter = loadingAdapter
            dialogBinding.spinnerApps.isEnabled = false
            dialogBinding.btnSubmit.isEnabled = false

            lifecycleScope.launch {
                try {
                    val filteredApps = withContext(Dispatchers.Default) {
                        val pm = requireContext().packageManager
                        pm.getInstalledApplications(PackageManager.GET_META_DATA)
                            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != requireContext().packageName }
                            .map { AppInfoItem(it.loadLabel(pm).toString(), it.packageName) }
                            .sortedBy { it.label.lowercase(Locale.ROOT) }
                    }

                    if (dialog.isShowing) {
                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filteredApps)
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        dialogBinding.spinnerApps.adapter = adapter
                        if (targetPackage != null) {
                            val index = filteredApps.indexOfFirst { it.packageName == targetPackage }
                            if (index >= 0) {
                                dialogBinding.spinnerApps.setSelection(index)
                            }
                            dialogBinding.spinnerApps.isEnabled = false
                        } else {
                            dialogBinding.spinnerApps.isEnabled = true
                        }
                        dialogBinding.btnSubmit.isEnabled = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (dialog.isShowing) {
                        Toast.makeText(requireContext(), "Failed to load apps list.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        when (type) {
            DialogType.TEMPORARY_IGNORE -> {
                dialogBinding.tvDialogTitle.text = "Convince AI: Ignore App"
                dialogBinding.tvDialogSubtitle.text = "Tell the AI why you must temporarily ignore keyword blocking in this app. (Min 7 sentences)"

                dialogBinding.spinnerApps.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val selectedItem = dialogBinding.spinnerApps.selectedItem
                        if (selectedItem is AppInfoItem) {
                            val app = selectedItem
                            val isAlreadyIgnored = currentIgnoredApps.contains(app.packageName)
                            if (isAlreadyIgnored) {
                                dialogBinding.tvDialogTitle.text = "Convince AI: Remove from Ignore List"
                                dialogBinding.tvDialogSubtitle.text = "Ask the AI to restore blocking on ${app.label}. The AI will approve this request eagerly."
                                dialogBinding.layoutAttachment.visibility = View.GONE
                                dialogBinding.tilJustification.hint = "Your justification (No restrictions, min 1 word)"
                            } else {
                                dialogBinding.tvDialogTitle.text = "Convince AI: Ignore App"
                                dialogBinding.tvDialogSubtitle.text = "Explain why you must temporarily ignore keyword blocking in ${app.label}. (Min 7 sentences)"
                                dialogBinding.layoutAttachment.visibility = View.VISIBLE
                                dialogBinding.tilJustification.hint = "Your justification (Min 7 sentences, 5 words/sentence)"
                            }
                        } else {
                            dialogBinding.layoutAttachment.visibility = View.GONE
                        }
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }
            }
            DialogType.PAUSE_BLOCKER -> {
                dialogBinding.tvDialogTitle.text = "Convince AI: Pause Blocker"
                dialogBinding.tvDialogSubtitle.text = "Explain the emergency reason to temporarily disable ALL keyword blocking. (Min 7 sentences)"
                dialogBinding.tvSelectAppLabel.visibility = View.GONE
                dialogBinding.spinnerApps.visibility = View.GONE
                dialogBinding.layoutAttachment.visibility = View.VISIBLE
                dialogBinding.tilJustification.hint = "Your justification (Min 7 sentences, 5 words/sentence)"
            }
            DialogType.EXTEND_IGNORE -> {
                dialogBinding.tvDialogTitle.text = "Convince AI: Extend Ignore"
                val appLabel = appInfoCache[targetPackage]?.first ?: targetPackage ?: "app"
                dialogBinding.tvDialogSubtitle.text = "Explain why you must extend the ignore duration for $appLabel. (Min 7 sentences)"
                dialogBinding.tvSelectAppLabel.visibility = View.VISIBLE
                dialogBinding.spinnerApps.visibility = View.VISIBLE
                dialogBinding.layoutAttachment.visibility = View.VISIBLE
                dialogBinding.tilJustification.hint = "Your justification (Min 7 sentences, 5 words/sentence)"
            }
        }

        dialogBinding.btnAttachImage.setOnClickListener {
            onImageAttachedCallback = { uri ->
                val bitmap = loadBitmapFromUri(uri)
                if (bitmap != null) {
                    attachedBitmap = bitmap
                    dialogBinding.cardImagePreview.visibility = View.VISIBLE
                    dialogBinding.ivPreview.setImageBitmap(bitmap)
                }
            }
            selectImageLauncher.launch("image/*")
        }

        dialogBinding.btnRemoveImage.setOnClickListener {
            attachedBitmap = null
            dialogBinding.cardImagePreview.visibility = View.GONE
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSubmit.setOnClickListener {
            val justification = dialogBinding.etJustification.text?.toString() ?: ""
            val selectedApp = dialogBinding.spinnerApps.selectedItem as? AppInfoItem

            if (type != DialogType.PAUSE_BLOCKER && selectedApp == null) {
                Toast.makeText(requireContext(), "Please select an app.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isAlreadyIgnored = type == DialogType.TEMPORARY_IGNORE && selectedApp != null &&
                    currentIgnoredApps.contains(selectedApp.packageName)

            val validationError = GeminiManager.validateUserText(justification, isRemoval = isAlreadyIgnored)
            if (validationError != null) {
                Toast.makeText(requireContext(), validationError, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            dialogBinding.layoutLoading.visibility = View.VISIBLE
            dialogBinding.btnSubmit.isEnabled = false
            dialogBinding.btnCancel.isEnabled = false

            lifecycleScope.launch {
                try {
                    val noCount = viewModel.noCount.value
                    val selectedModel = viewModel.selectedGeminiModel.value
                    val result = when (type) {
                        DialogType.TEMPORARY_IGNORE -> GeminiManager.requestExemption(apiKey, selectedModel, selectedApp!!.label, selectedApp.packageName, justification, attachedBitmap, noCount, isAlreadyIgnored)
                        DialogType.PAUSE_BLOCKER -> GeminiManager.requestPauseBlocker(apiKey, selectedModel, justification, attachedBitmap, noCount)
                        DialogType.EXTEND_IGNORE -> GeminiManager.requestExtension(apiKey, selectedModel, selectedApp!!.label, selectedApp.packageName, justification, attachedBitmap, noCount)
                        else -> null
                    }

                    if (result?.approved == true) {
                        dialogBinding.tvVerdictTitle.text = "Verdict: APPROVED"
                        dialogBinding.tvVerdictTitle.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                        dialogBinding.tvVerdictMessage.text = result.botResponse

                        when (type) {
                            DialogType.TEMPORARY_IGNORE -> {
                                val targetApp = selectedApp!!
                                if (isAlreadyIgnored) {
                                    val newIgnored = currentIgnoredApps.filter { it != targetApp.packageName }
                                    viewModel.setIgnoredApps(newIgnored)
                                    val currentTemp = viewModel.temporaryIgnoredApps.value.toMutableMap()
                                    currentTemp.remove(targetApp.packageName)
                                    viewModel.setTemporaryIgnoredApps(currentTemp)
                                } else {
                                    val currentTemp = viewModel.temporaryIgnoredApps.value.toMutableMap()
                                    currentTemp[targetApp.packageName] = System.currentTimeMillis() + (result.durationMinutes * 60 * 1000L)
                                    viewModel.setTemporaryIgnoredApps(currentTemp)
                                }
                            }
                            DialogType.EXTEND_IGNORE -> {
                                val targetApp = selectedApp!!
                                val currentTemp = viewModel.temporaryIgnoredApps.value.toMutableMap()
                                val currentExpiry = currentTemp[targetApp.packageName] ?: System.currentTimeMillis()
                                currentTemp[targetApp.packageName] = currentExpiry + (result.durationMinutes * 60 * 1000L)
                                viewModel.setTemporaryIgnoredApps(currentTemp)
                            }
                            DialogType.PAUSE_BLOCKER -> viewModel.setIsActive(false)
                        }
                        dialogBinding.btnSubmit.postDelayed({ dialog.dismiss() }, 3000)
                    } else {
                        dialogBinding.tvVerdictTitle.text = "Verdict: REJECTED"
                        dialogBinding.tvVerdictTitle.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                        dialogBinding.tvVerdictMessage.text = result?.botResponse ?: "Error"
                        viewModel.setNoCount(noCount + 1)
                        dialogBinding.btnSubmit.isEnabled = true
                        dialogBinding.btnCancel.isEnabled = true
                    }
                } catch (e: Exception) {
                    dialogBinding.layoutLoading.visibility = View.GONE
                    dialogBinding.btnSubmit.isEnabled = true
                    dialogBinding.btnCancel.isEnabled = true
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val contentResolver = requireContext().contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private enum class DialogType {
        TEMPORARY_IGNORE, PAUSE_BLOCKER, EXTEND_IGNORE
    }

    private data class AppInfoItem(val label: String, val packageName: String) {
        override fun toString(): String = label
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val currentTemp = viewModel.temporaryIgnoredApps.value
                val expiredApps = currentTemp.filter { now >= it.value }.keys
                if (expiredApps.isNotEmpty()) {
                    val updatedTemp = currentTemp.filter { now < it.value }
                    viewModel.setTemporaryIgnoredApps(updatedTemp)
                }

                refreshCountdownTexts()
                delay(1000)
            }
        }
    }

    private fun refreshCountdownTexts() {
        val now = System.currentTimeMillis()
        val tempApps = viewModel.temporaryIgnoredApps.value
        if (_binding == null) return
        for (i in 0 until binding.layoutIgnoredApps.childCount) {
            val childView = binding.layoutIgnoredApps.getChildAt(i)
            val packageName = childView.tag as? String ?: continue
            val expiry = tempApps[packageName]
            val tvRemainingTime = childView.findViewById<TextView>(R.id.tv_remaining_time) ?: continue
            if (expiry != null) {
                val remainingMs = expiry - now
                if (remainingMs > 0) {
                    val remainingSeconds = (remainingMs / 1000) % 60
                    val remainingMinutes = (remainingMs / (1000 * 60))
                    tvRemainingTime.text = String.format(Locale.ROOT, "%dm %02ds", remainingMinutes, remainingSeconds)
                } else {
                    tvRemainingTime.text = "0m 00s"
                }
            } else {
                tvRemainingTime.text = "Permanent"
            }
        }
    }

    private fun updateIgnoredAppsList() {
        val context = context ?: return
        val pm = context.packageManager
        val tempApps = viewModel.temporaryIgnoredApps.value
        val now = System.currentTimeMillis()

        val activeTempApps = tempApps.filter { it.value > now }
        val allIgnoredPackages = (currentIgnoredApps + activeTempApps.keys).distinct()

        if (allIgnoredPackages.isEmpty()) {
            binding.cardIgnoredAppsList.visibility = View.GONE
            binding.layoutIgnoredApps.removeAllViews()
            return
        }

        binding.cardIgnoredAppsList.visibility = View.VISIBLE
        binding.layoutIgnoredApps.removeAllViews()

        for (packageName in allIgnoredPackages) {
            val itemBinding = ItemIgnoredAppBinding.inflate(layoutInflater, binding.layoutIgnoredApps, false)
            itemBinding.root.tag = packageName

            val cached = appInfoCache[packageName]
            var appLabel = packageName
            if (cached != null) {
                appLabel = cached.first
                itemBinding.ivAppIcon.setImageDrawable(cached.second)
            } else {
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val label = appInfo.loadLabel(pm).toString()
                    val icon = appInfo.loadIcon(pm)
                    appInfoCache[packageName] = Pair(label, icon)
                    appLabel = label
                    itemBinding.ivAppIcon.setImageDrawable(icon)
                } catch (e: Exception) {
                    itemBinding.ivAppIcon.setImageDrawable(ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon))
                }
            }

            itemBinding.tvAppName.text = appLabel
            itemBinding.tvPackageName.text = packageName

            val isTemp = activeTempApps.containsKey(packageName)
            if (isTemp) {
                val expiry = activeTempApps[packageName]!!
                val remainingMs = expiry - now
                val remainingSeconds = (remainingMs / 1000) % 60
                val remainingMinutes = (remainingMs / (1000 * 60))
                itemBinding.tvRemainingTime.text = String.format(Locale.ROOT, "%dm %02ds", remainingMinutes, remainingSeconds)

                itemBinding.tvRemainingTime.setOnClickListener {
                    showDecreaseTimeDialog(packageName, appLabel, expiry)
                }

                itemBinding.btnExtendTime.visibility = View.VISIBLE
                itemBinding.btnExtendTime.setOnClickListener {
                    showAiChatDialog(DialogType.EXTEND_IGNORE, packageName)
                }
            } else {
                itemBinding.tvRemainingTime.text = "Permanent"
                itemBinding.tvRemainingTime.isClickable = false
                itemBinding.tvRemainingTime.isFocusable = false
                itemBinding.btnExtendTime.visibility = View.GONE
            }

            binding.layoutIgnoredApps.addView(itemBinding.root)
        }
    }

    private fun showDecreaseTimeDialog(packageName: String, appLabel: String, expiry: Long) {
        val now = System.currentTimeMillis()
        val remainingMs = expiry - now
        if (remainingMs <= 0) return

        val remainingMinutes = (remainingMs / (1000 * 60)).toInt()
        val maxMinutes = if (remainingMinutes < 1) 1 else remainingMinutes

        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }

        val tvValue = TextView(context).apply {
            text = "New Duration: $remainingMinutes minutes"
            androidx.core.widget.TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 0, 0, 8)
        }

        val slider = com.google.android.material.slider.Slider(context).apply {
            valueFrom = 0.0f
            valueTo = maxMinutes.toFloat()
            value = remainingMinutes.toFloat()
            stepSize = 1.0f
            addOnChangeListener { _, value, _ ->
                tvValue.text = "New Duration: ${value.toInt()} minutes"
            }
        }

        container.addView(tvValue)
        container.addView(slider)

        MaterialAlertDialogBuilder(context)
            .setTitle("Decrease Ignore Time")
            .setMessage("Set a shorter duration for $appLabel. You cannot increase the time.")
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                val newMinutes = slider.value.toInt()
                val currentTemp = viewModel.temporaryIgnoredApps.value.toMutableMap()
                if (newMinutes <= 0) {
                    currentTemp.remove(packageName)
                } else {
                    currentTemp[packageName] = System.currentTimeMillis() + (newMinutes * 60 * 1000L)
                }
                viewModel.setTemporaryIgnoredApps(currentTemp)
                Toast.makeText(context, "Duration updated.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        const val FRAGMENT_ID = "keyword_blocker"
    }
}
