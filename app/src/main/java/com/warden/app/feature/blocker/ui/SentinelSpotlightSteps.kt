package com.warden.app.feature.blocker.ui

import com.warden.app.R
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import com.warden.app.feature.onboarding.ui.OnboardingSpotlightController

/**
 * Defines the 8 live spotlight coach-mark steps across Sentinel's Shield, Rules
 * (Blocked words/match modes, Safe phrases, Website lists/profiles, Grace window), and Vault tabs.
 */
internal object SentinelSpotlightSteps {
    /** Builds the ordered spotlight steps targeting real views inside [binding]. */
    internal fun build(binding: FragmentKeywordBlockerBinding): List<OnboardingSpotlightController.Step> =
        shieldSteps(binding) + rulesSteps(binding) + vaultSteps(binding)

    private fun shieldSteps(binding: FragmentKeywordBlockerBinding): List<OnboardingSpotlightController.Step> =
        listOf(
            OnboardingSpotlightController.Step(
                tabIndex = SentinelNavigationBinder.Tab.SHIELD.ordinal,
                targetView = { binding.tabShield.orbShieldButton },
                titleRes = R.string.guide_step1_title,
                bodyRes = R.string.guide_step1_body,
                showAccessibilityCta = true,
            ),
            OnboardingSpotlightController.Step(
                tabIndex = SentinelNavigationBinder.Tab.SHIELD.ordinal,
                targetView = { binding.tabShield.layoutQuickStats },
                titleRes = R.string.guide_step2_title,
                bodyRes = R.string.guide_step2_body,
            ),
            OnboardingSpotlightController.Step(
                tabIndex = SentinelNavigationBinder.Tab.SHIELD.ordinal,
                targetView = { binding.tabShield.cardExemption },
                titleRes = R.string.guide_step3_title,
                bodyRes = R.string.guide_step3_body,
            ),
        )

    private fun rulesSteps(binding: FragmentKeywordBlockerBinding): List<OnboardingSpotlightController.Step> =
        listOf(
            OnboardingSpotlightController.Step(
                tabIndex = SentinelNavigationBinder.Tab.RULES.ordinal,
                targetView = { binding.tabRules.toggleRuleMode },
                titleRes = R.string.guide_step4_title,
                bodyRes = R.string.guide_step4_body,
                onPrepare = { binding.tabRules.toggleRuleMode.check(R.id.btn_mode_blocked) },
            ),
            OnboardingSpotlightController.Step(
                tabIndex = SentinelNavigationBinder.Tab.RULES.ordinal,
                targetView = { binding.tabRules.toggleAddMode },
                titleRes = R.string.guide_step5_title,
                bodyRes = R.string.guide_step5_body,
                onPrepare = { binding.tabRules.toggleRuleMode.check(R.id.btn_mode_blocked) },
            ),
            OnboardingSpotlightController.Step(
                tabIndex = SentinelNavigationBinder.Tab.RULES.ordinal,
                targetView = { binding.tabRules.panelWebsites },
                titleRes = R.string.guide_step6_title,
                bodyRes = R.string.guide_step6_body,
                onPrepare = { binding.tabRules.toggleRuleMode.check(R.id.btn_mode_websites) },
            ),
            OnboardingSpotlightController.Step(
                tabIndex = SentinelNavigationBinder.Tab.RULES.ordinal,
                targetView = { binding.tabRules.cardGraceWindow },
                titleRes = R.string.guide_step7_title,
                bodyRes = R.string.guide_step7_body,
            ),
        )

    private fun vaultSteps(binding: FragmentKeywordBlockerBinding): List<OnboardingSpotlightController.Step> =
        listOf(
            OnboardingSpotlightController.Step(
                tabIndex = SentinelNavigationBinder.Tab.VAULT.ordinal,
                targetView = { binding.tabVault.headerGeminiEngine },
                titleRes = R.string.guide_step8_title,
                bodyRes = R.string.guide_step8_body,
            ),
        )
}
