package com.warden.app.feature.onboarding.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.warden.app.R
import com.warden.app.databinding.DialogOnboardingGuideBinding

/**
 * Interactive 4-step onboarding walkthrough explaining how Warden's Shield, Rules,
 * AI Gatekeeper, and Vault fit together.
 */
class OnboardingGuideDialog(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val isAccessibilityEnabled: () -> Boolean,
    private val onOpenAccessibilitySettings: () -> Unit,
    private val onFinished: () -> Unit = {},
) {
    private data class GuideStep(
        @DrawableRes val iconRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val bodyRes: Int,
        @StringRes val tipRes: Int,
        val showAccessibilityCta: Boolean = false,
    )

    private val steps =
        listOf(
            GuideStep(
                iconRes = R.drawable.ic_nav_shield_24,
                titleRes = R.string.guide_step1_title,
                bodyRes = R.string.guide_step1_body,
                tipRes = R.string.guide_step1_tip,
                showAccessibilityCta = true,
            ),
            GuideStep(
                iconRes = R.drawable.ic_nav_rules_24,
                titleRes = R.string.guide_step2_title,
                bodyRes = R.string.guide_step2_body,
                tipRes = R.string.guide_step2_tip,
            ),
            GuideStep(
                iconRes = R.drawable.ic_spark_24,
                titleRes = R.string.guide_step3_title,
                bodyRes = R.string.guide_step3_body,
                tipRes = R.string.guide_step3_tip,
            ),
            GuideStep(
                iconRes = R.drawable.ic_nav_vault_24,
                titleRes = R.string.guide_step4_title,
                bodyRes = R.string.guide_step4_body,
                tipRes = R.string.guide_step4_tip,
            ),
        )

    /** Displays the 4-step interactive onboarding modal starting at step 1. */
    fun show(): AlertDialog {
        val binding = DialogOnboardingGuideBinding.inflate(layoutInflater)
        var stepIndex = 0
        val dialog = MaterialAlertDialogBuilder(context).setView(binding.root).create()

        binding.btnGuideAction.setOnClickListener { onOpenAccessibilitySettings() }
        binding.btnGuideSkip.setOnClickListener {
            onFinished()
            dialog.dismiss()
        }
        binding.btnGuideBack.setOnClickListener {
            if (stepIndex > 0) {
                stepIndex--
                renderStep(binding, stepIndex)
            }
        }
        binding.btnGuideNext.setOnClickListener {
            if (stepIndex < steps.lastIndex) {
                stepIndex++
                renderStep(binding, stepIndex)
            } else {
                onFinished()
                dialog.dismiss()
            }
        }

        renderStep(binding, stepIndex)
        dialog.show()
        return dialog
    }

    private fun renderStep(
        binding: DialogOnboardingGuideBinding,
        stepIndex: Int,
    ) {
        val step = steps[stepIndex]
        binding.tvGuideStepBadge.text = context.getString(R.string.guide_step_fmt, stepIndex + 1, steps.size)
        binding.ivGuideIcon.setImageResource(step.iconRes)
        binding.tvGuideTitle.setText(step.titleRes)
        binding.tvGuideBody.setText(step.bodyRes)
        binding.tvGuideTip.setText(step.tipRes)
        binding.btnGuideBack.visibility = if (stepIndex == 0) View.INVISIBLE else View.VISIBLE
        binding.btnGuideNext.setText(if (stepIndex == steps.lastIndex) R.string.guide_done else R.string.guide_next)

        val needsA11y = step.showAccessibilityCta && !isAccessibilityEnabled()
        binding.btnGuideAction.visibility = if (needsA11y) View.VISIBLE else View.GONE
        if (needsA11y) {
            binding.btnGuideAction.setText(R.string.guide_step1_cta)
        }
    }
}
