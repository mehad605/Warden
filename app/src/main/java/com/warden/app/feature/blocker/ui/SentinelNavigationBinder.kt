package com.warden.app.feature.blocker.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.color.MaterialColors
import com.warden.app.R
import com.warden.app.data.models.Settings
import com.warden.app.databinding.FragmentKeywordBlockerBinding

/**
 * Coordinates the Sentinel 3-tab bottom bar (Shield / Rules / Vault), Rules segmented mode
 * toggle (Blocked vs. Safe Phrases), and live Shield hero status & quick-stat tiles.
 */
internal class SentinelNavigationBinder(
    private val context: Context,
    private val binding: FragmentKeywordBlockerBinding,
    private val onToggleShieldOrb: () -> Unit,
    private val onOpenGuide: () -> Unit,
) {
    /** Active top-level Sentinel dashboard tab. */
    enum class Tab { SHIELD, RULES, VAULT }

    private var activeTab: Tab = Tab.SHIELD

    /** Wires bottom-nav tabs, Rules segmented switcher, quick-stat jumps, and guide buttons. */
    fun bindListeners() {
        binding.navShield.setOnClickListener { selectTab(Tab.SHIELD) }
        binding.navRules.setOnClickListener { selectTab(Tab.RULES) }
        binding.navVault.setOnClickListener { selectTab(Tab.VAULT) }
        binding.btnOpenGuide.setOnClickListener { onOpenGuide() }
        binding.tabVault.cardReplayGuide.setOnClickListener { onOpenGuide() }
        binding.tabShield.orbShieldButton.setOnClickListener { onToggleShieldOrb() }

        binding.tabShield.tileStatBlocked.setOnClickListener {
            selectTab(Tab.RULES)
            binding.tabRules.toggleRuleMode.check(R.id.btn_mode_blocked)
        }
        binding.tabShield.tileStatWhitelist.setOnClickListener {
            selectTab(Tab.RULES)
            binding.tabRules.toggleRuleMode.check(R.id.btn_mode_whitelist)
        }
        binding.tabShield.tileStatGrace.setOnClickListener { selectTab(Tab.RULES) }

        binding.tabRules.toggleRuleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            binding.tabRules.panelBlockedWords.visibility = if (checkedId == R.id.btn_mode_blocked) View.VISIBLE else View.GONE
            binding.tabRules.panelWhitelistWords.visibility = if (checkedId == R.id.btn_mode_whitelist) View.VISIBLE else View.GONE
            binding.tabRules.panelWebsites.visibility = if (checkedId == R.id.btn_mode_websites) View.VISIBLE else View.GONE
        }
        selectTab(Tab.SHIELD)
    }

    /** Switches the visible tab and updates bottom-bar highlight styling. */
    fun selectTab(tab: Tab) {
        activeTab = tab
        binding.tabShield.root.visibility = if (tab == Tab.SHIELD) View.VISIBLE else View.GONE
        binding.tabRules.root.visibility = if (tab == Tab.RULES) View.VISIBLE else View.GONE
        binding.tabVault.root.visibility = if (tab == Tab.VAULT) View.VISIBLE else View.GONE
        binding.scrollContent.scrollTo(0, 0)

        styleNavItem(binding.navShield, tab == Tab.SHIELD)
        styleNavItem(binding.navRules, tab == Tab.RULES)
        styleNavItem(binding.navVault, tab == Tab.VAULT)
    }

    /** Updates header Accessibility status subtitle. */
    fun renderAccessibilityStatus(enabled: Boolean) {
        binding.tvHeaderStatus.setText(
            if (enabled) R.string.header_status_online else R.string.header_status_offline,
        )
        binding.tabShield.cardAccessibilityWarning.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    /** Updates the Shield hero orb, top status pill, 3 stat tiles, and AI rejection counter. */
    fun renderSummary(settings: Settings) {
        val config = settings.keywordBlockerConfig
        val graceLabel =
            if (settings.ignoreGracePeriodSeconds == 0) {
                context.getString(R.string.grace_period_off)
            } else {
                context.getString(R.string.grace_period_seconds_fmt, settings.ignoreGracePeriodSeconds)
            }

        renderHeroState(isActive = config.isActive, graceLabel = graceLabel)

        binding.tabShield.tvStatBlockedNum.text = context.getString(R.string.stat_count_fmt, config.blockedKeywords.size)
        binding.tabShield.tvStatWhitelistNum.text = context.getString(R.string.stat_count_fmt, config.whitelistedKeywords.size)
        binding.tabShield.tvStatGraceNum.text = graceLabel
        binding.tabShield.tvRejectionBadge.text =
            context.resources.getQuantityString(R.plurals.rejection_count, settings.noCount, settings.noCount)

        val hasAiReady = !settings.geminiApiKey.isNullOrEmpty() && settings.availableGeminiModels.isNotEmpty()
        binding.tabShield.tvGatekeeperSummary.setText(
            if (hasAiReady) R.string.ai_gatekeeper_card_subtitle else R.string.ai_gatekeeper_setup_hint,
        )
    }

    private fun renderHeroState(
        isActive: Boolean,
        graceLabel: String,
    ) {
        binding.tabShield.orbShieldButton.setBackgroundResource(
            if (isActive) R.drawable.bg_sentinel_orb else R.drawable.bg_sentinel_orb_paused,
        )
        binding.tabShield.tvHeroTitle.setText(
            if (isActive) R.string.hero_shield_armed else R.string.hero_shield_paused,
        )
        binding.tabShield.tvHeroSubtitle.text =
            if (isActive) {
                context.getString(R.string.hero_shield_armed_sub, graceLabel)
            } else {
                context.getString(R.string.hero_shield_paused_sub)
            }

        val pillBg =
            if (isActive) {
                ContextCompat.getColor(context, R.color.status_active_bg)
            } else {
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceContainerHigh)
            }
        val pillFg =
            if (isActive) {
                ContextCompat.getColor(context, R.color.status_active)
            } else {
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
        binding.tvHeaderPill.setText(if (isActive) R.string.status_armed_pill else R.string.status_paused_pill)
        binding.tvHeaderPill.backgroundTintList = ColorStateList.valueOf(pillBg)
        binding.tvHeaderPill.setTextColor(pillFg)
    }

    private fun styleNavItem(
        tabView: TextView,
        selected: Boolean,
    ) {
        if (selected) {
            tabView.setBackgroundResource(R.drawable.bg_nav_pill_active)
        } else {
            tabView.background = null
        }
        val colorAttr =
            if (selected) {
                com.google.android.material.R.attr.colorPrimary
            } else {
                com.google.android.material.R.attr.colorOnSurfaceVariant
            }
        val tint = MaterialColors.getColor(binding.root, colorAttr)
        TextViewCompat.setCompoundDrawableTintList(tabView, ColorStateList.valueOf(tint))
        tabView.setTextColor(tint)
    }
}
