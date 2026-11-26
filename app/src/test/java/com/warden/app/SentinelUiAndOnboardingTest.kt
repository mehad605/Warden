package com.warden.app

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.warden.app.ui.activity.FragmentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Robolectric UI tests verifying the Sentinel 3-tab dashboard (Shield / Rules / Vault),
 * Rules segmented mode toggle, and the 4-step interactive onboarding walkthrough.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SentinelUiAndOnboardingTest {
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("agreed_to_terms", true)
            .commit()
    }

    private fun awaitMainLooper(waitMs: Long = 100L) {
        Thread.sleep(waitMs)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun sentinelTabsAndRulesSegmentedToggleSwitchCleanly() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        awaitMainLooper()

        val tabShield = activity.findViewById<View>(R.id.tab_shield)
        val tabRules = activity.findViewById<View>(R.id.tab_rules)
        val tabVault = activity.findViewById<View>(R.id.tab_vault)

        assertEquals(View.VISIBLE, tabShield.visibility)
        assertEquals(View.GONE, tabRules.visibility)
        assertEquals(View.GONE, tabVault.visibility)

        activity.findViewById<View>(R.id.nav_rules).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, tabShield.visibility)
        assertEquals(View.VISIBLE, tabRules.visibility)

        val blockedPanel = activity.findViewById<View>(R.id.panel_blocked_words)
        val whitelistPanel = activity.findViewById<View>(R.id.panel_whitelist_words)
        assertEquals(View.VISIBLE, blockedPanel.visibility)
        assertEquals(View.GONE, whitelistPanel.visibility)

        activity.findViewById<MaterialButtonToggleGroup>(R.id.toggle_rule_mode).check(R.id.btn_mode_whitelist)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, blockedPanel.visibility)
        assertEquals(View.VISIBLE, whitelistPanel.visibility)

        activity.findViewById<View>(R.id.nav_vault).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, tabRules.visibility)
        assertEquals(View.VISIBLE, tabVault.visibility)
    }

    @Test
    fun onboardingGuideWalksThroughAllFourStepsAndDismisses() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        awaitMainLooper()

        activity.findViewById<View>(R.id.btn_open_guide).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = requireNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)
        assertTrue(dialog.isShowing)

        val badge = requireNotNull(dialog.findViewById<TextView>(R.id.tv_guide_step_badge))
        val nextBtn = requireNotNull(dialog.findViewById<MaterialButton>(R.id.btn_guide_next))

        assertEquals("Step 1 of 4", badge.text.toString())
        nextBtn.performClick()
        assertEquals("Step 2 of 4", badge.text.toString())
        nextBtn.performClick()
        assertEquals("Step 3 of 4", badge.text.toString())
        nextBtn.performClick()
        assertEquals("Step 4 of 4", badge.text.toString())
        assertEquals(activity.getString(R.string.guide_done), nextBtn.text.toString())

        nextBtn.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(dialog.isShowing)
    }
}
