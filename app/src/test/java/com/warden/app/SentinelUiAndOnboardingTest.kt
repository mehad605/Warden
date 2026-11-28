package com.warden.app

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.warden.app.databinding.DialogAiChatBinding
import com.warden.app.feature.gatekeeper.ui.GatekeeperDialogMode
import com.warden.app.feature.gatekeeper.ui.GatekeeperDialogViewBinder
import com.warden.app.ui.activity.FragmentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric UI tests verifying Sentinel's 3-tab dashboard, Rules segmented mode toggle,
 * always-clickable Request Temporary Pass button, real API key persistence (no fake asterisks),
 * and the 6-step interactive spotlight coach-mark tour across Shield -> Rules -> Vault.
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
    fun requestTempPassButtonIsEnabledAndRedirectsToVaultWithoutApiKey() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        awaitMainLooper()

        val btnPass = activity.findViewById<MaterialButton>(R.id.btn_ask_ai_ignore)
        assertTrue("Request Temporary Pass button must remain enabled", btnPass.isEnabled)

        btnPass.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tab_vault).visibility)
    }

    @Test
    fun savingApiKeyKeepsRealValueInPasswordFieldForEyeReveal() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        awaitMainLooper()

        activity.findViewById<View>(R.id.nav_vault).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val etApiKey = activity.findViewById<EditText>(R.id.et_api_key)
        etApiKey.setText("AIzaSyTestKey12345")
        activity.findViewById<MaterialButton>(R.id.btn_save_api_key).performClick()
        awaitMainLooper()

        assertEquals("AIzaSyTestKey12345", etApiKey.text.toString())
    }

    @Test
    fun spotlightOnboardingWalksAcrossAllEightStepsAndSwitchesRulePanels() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        awaitMainLooper()

        activity.findViewById<View>(R.id.btn_open_guide).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val badge = requireNotNull(activity.findViewById<TextView>(R.id.tv_guide_step_badge))
        val nextBtn = requireNotNull(activity.findViewById<MaterialButton>(R.id.btn_guide_next))

        assertEquals("Step 1 of 8", badge.text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tab_shield).visibility)

        repeat(3) {
            nextBtn.performClick()
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertEquals("Step 4 of 8", badge.text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tab_rules).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.panel_blocked_words).visibility)

        // Step 5: Match modes & phrases (still on Blocked panel)
        nextBtn.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Step 5 of 8", badge.text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.panel_blocked_words).visibility)

        // Step 6: Website Lists & Profiles (auto-switches Rules panel to Websites!)
        nextBtn.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Step 6 of 8", badge.text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.panel_websites).visibility)

        // Step 7: Grace Window & Bottom Alerts
        nextBtn.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Step 7 of 8", badge.text.toString())

        // Step 8: Vault tab & final Done label
        nextBtn.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Step 8 of 8", badge.text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tab_vault).visibility)
        assertEquals(activity.getString(R.string.guide_done), nextBtn.text.toString())
        assertNotNull(activity.findViewById<View>(R.id.card_spotlight_tooltip))

        nextBtn.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(activity.findViewById<View>(R.id.card_spotlight_tooltip))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tab_shield).visibility)
    }

    @Test
    fun gatekeeperChatLayoutResizesCleanlyWithoutOverlapUnderHalfHeightKeyboard() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        awaitMainLooper()

        val inflater = LayoutInflater.from(activity)
        val chatBinding = DialogAiChatBinding.inflate(inflater)
        val binder = GatekeeperDialogViewBinder(activity, inflater, chatBinding)
        binder.bindModeHeader(GatekeeperDialogMode.TEMPORARY_IGNORE, rejectionCount = 2, targetAppLabel = "Chrome")
        binder.updateSentenceCounter(
            "I need Chrome for my university assignment today. My professor posted urgent lecture slides right now.",
            isRemoval = false,
        )

        val widthSpec = View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY)
        chatBinding.root.measure(widthSpec, heightSpec)
        chatBinding.root.layout(0, 0, 360, 320)

        assertTrue(chatBinding.tvSentenceCounter.text.toString().startsWith("2 / 7 sentences"))
        assertTrue(
            "Scrollable chat area must retain positive height above composer",
            chatBinding.scrollChatMessages.height > 0,
        )
        assertTrue(
            "Composer must sit cleanly inside the keyboard-resized viewport",
            chatBinding.layoutComposer.bottom <= 320,
        )
        assertTrue(
            "Chat thread must not overlap composer",
            chatBinding.scrollChatMessages.bottom <= chatBinding.layoutComposer.top,
        )
    }
}
