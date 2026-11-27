package com.warden.app.feature.blocker.ui

import com.warden.app.R
import com.warden.app.databinding.FragmentKeywordBlockerBinding
import com.warden.app.feature.onboarding.ui.OnboardingSpotlightController

/**
 * Defines the 6 live spotlight coach-mark steps across Sentinel's Shield, Rules, and Vault tabs.
 */
internal object SentinelSpotlightSteps {
    /** Builds the ordered spotlight steps targeting real views inside [binding]. */
    fun build(binding: FragmentKeywordBlockerBinding): List<OnboardingSpotlightController.Step> =
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
            OnboardingSpotlightController.Step(
                tabIndex = SentinelNavigationBinder.Tab.RULES.ordinal,
                targetView = { binding.tabRules.toggleRuleMode },
                titleRes = R.string.guide_step4_title,
                bodyRes = R.string.guide_step4_body,
            ),
            OnboardingSpotlightController.Step(
                tabIndex = SentinelNavigationBinder.Tab.RULES.ordinal,
                targetView = { binding.tabRules.cardGraceWindow },
                titleRes = R.string.guide_step5_title,
                bodyRes = R.string.guide_step5_body,
            ),
            OnboardingSpotlightController.Step(
                tabIndex = SentinelNavigationBinder.Tab.VAULT.ordinal,
                targetView = { binding.tabVault.headerGeminiEngine },
                titleRes = R.string.guide_step6_title,
                bodyRes = R.string.guide_step6_body,
            ),
        )
}
