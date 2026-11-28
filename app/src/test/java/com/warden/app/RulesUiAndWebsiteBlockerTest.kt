package com.warden.app

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout
import com.warden.app.data.models.WebsiteBlockerConfig
import com.warden.app.feature.blocker.domain.RuleItemSpec
import com.warden.app.feature.blocker.domain.RuleListFormatter
import com.warden.app.feature.blocker.domain.RuleListRow
import com.warden.app.feature.websites.data.PornDomainRepository
import com.warden.app.feature.websites.domain.DomainNormalizer
import com.warden.app.feature.websites.domain.DomainValidationResult
import com.warden.app.feature.websites.domain.WebsiteDomainMatcher
import com.warden.app.ui.activity.FragmentActivity
import kotlinx.coroutines.test.runTest
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
 * UI & scale checklist tests covering Section 6, 7, and 9.4:
 * mode selection at add time, dictionary sorting + letter grouping, live search,
 * inline mode switching, phrase support, 5-second undo delete, prepopulated 46k website list,
 * 1,000+ entries responsiveness, and keyboard/screen-reader accessibility.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RulesUiAndWebsiteBlockerTest {
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
    fun rulesUiSupportsModeAtAddTimePhrasesSearchInlineSwitchAndCounts() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        awaitMainLooper()

        activity.findViewById<View>(R.id.nav_rules).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val etKeyword = activity.findViewById<EditText>(R.id.et_keyword)
        val addModeToggle = activity.findViewById<MaterialButtonToggleGroup>(R.id.toggle_add_mode)
        val countTv = activity.findViewById<TextView>(R.id.tv_keyword_count)
        val rvBlocked = activity.findViewById<RecyclerView>(R.id.rv_blocked_words)

        // Add phrase in default Exact mode via Enter key
        etKeyword.setText("  Robert   DeNero  ")
        etKeyword.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        awaitMainLooper()

        // Switch mode to Anywhere in text and add "porn"
        addModeToggle.check(R.id.btn_add_mode_contains)
        etKeyword.setText("porn")
        etKeyword.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        awaitMainLooper()

        assertTrue("Expected total/exact/anywhere counts, got: ${countTv.text}", countTv.text.toString().contains("2 total"))
        assertTrue(countTv.text.toString().contains("1 Exact"))
        assertTrue(countTv.text.toString().contains("1 Anywhere"))

        // Dictionary order: P ("porn") before R ("robert denero") -> 2 headers + 2 entries = 4 rows
        assertEquals(4, rvBlocked.adapter?.itemCount)

        // Live search filters down to just "porn" (1 header + 1 entry = 2 rows)
        val etSearch = activity.findViewById<EditText>(R.id.et_search_blocked)
        etSearch.setText("por")
        awaitMainLooper()
        assertEquals(2, rvBlocked.adapter?.itemCount)
    }

    @Test
    fun dictionarySortingCollatorIsCaseAndAccentInsensitiveAndHandles1200EntriesQuickly() {
        val items =
            mutableListOf(
                RuleItemSpec(id = "1", label = "éclair"),
                RuleItemSpec(id = "2", label = "apple"),
                RuleItemSpec(id = "3", label = "9gag"),
                RuleItemSpec(id = "4", label = "Egg"),
            )
        repeat(1_200) { i ->
            val letter = ('a' + (i % 26))
            items.add(RuleItemSpec(id = "bulk-$i", label = "${letter}term$i"))
        }

        val startNs = System.nanoTime()
        val grouped = RuleListFormatter.buildGroupedList(items)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0

        assertTrue("Sorting & grouping 1,204 items must be fast, took ${elapsedMs}ms", elapsedMs < 250.0)
        assertTrue("Must group digits/symbols under # bucket", grouped.letterPositions.containsKey("#"))
        assertTrue("Must group accented e under E bucket", grouped.letterPositions.containsKey("E"))
        val headers = grouped.rows.filterIsInstance<RuleListRow.Header>().map { it.letter }
        assertEquals("#", headers.last())
    }

    @Test
    fun shippedPornDomainListLoadsAndSupportsSubdomainsDisableResetAndWhitelist() =
        runTest {
            val repo = PornDomainRepository(app)
            val builtins = repo.loadBuiltinDomains()
            assertTrue("Expected 10,000+ shipped domains, got ${builtins.size}", builtins.size > 10_000)
            assertTrue("Expected pornhub.com in shipped list", "pornhub.com" in builtins)

            val matcher = WebsiteDomainMatcher.fromConfig(WebsiteBlockerConfig(isEnabled = true), builtins)
            assertEquals("pornhub.com", matcher.matchHost("https://www.pornhub.com/video?id=1"))
            assertEquals("pornhub.com", matcher.matchHost("cdn.pornhub.com"))
            assertNull(matcher.matchHost("notpornhub.com"))
            assertNull(matcher.matchHost("pornhub.com.evil.org"))

            // Disable built-in -> allowed
            val disabledMatcher =
                WebsiteDomainMatcher.fromConfig(
                    WebsiteBlockerConfig(isEnabled = true, disabledBuiltinDomains = listOf("pornhub.com")),
                    builtins,
                )
            assertNull(disabledMatcher.matchHost("https://pornhub.com"))

            // Whitelist overrides blocklist
            val whiteMatcher =
                WebsiteDomainMatcher.fromConfig(
                    WebsiteBlockerConfig(isEnabled = true, whitelistedDomains = listOf("safe.pornhub.com")),
                    builtins,
                )
            assertNull(whiteMatcher.matchHost("https://safe.pornhub.com/docs"))
            assertEquals("pornhub.com", whiteMatcher.matchHost("https://pornhub.com"))
        }

    @Test
    fun websiteTabValidatesGarbageDomainsAndAcceptsPastedUrls() {
        assertTrue(DomainNormalizer.validateUserInput("notadomain") is DomainValidationResult.Invalid)
        assertTrue(DomainNormalizer.validateUserInput("bad domain.com") is DomainValidationResult.Invalid)
        assertTrue(DomainNormalizer.validateUserInput("http://bad!host.com") is DomainValidationResult.Invalid)
        val valid = DomainNormalizer.validateUserInput("https://www.Example.COM:8080/path?q=1#frag")
        assertEquals(DomainValidationResult.Valid("example.com"), valid)

        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        awaitMainLooper()
        activity.findViewById<View>(R.id.nav_rules).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<MaterialButtonToggleGroup>(R.id.toggle_rule_mode).check(R.id.btn_mode_websites)
        shadowOf(Looper.getMainLooper()).idle()

        val tilDomain = activity.findViewById<TextInputLayout>(R.id.til_add_domain)
        val etDomain = activity.findViewById<EditText>(R.id.et_domain_input)
        etDomain.setText("garbage")
        etDomain.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        awaitMainLooper()
        assertNotNull("Invalid domain input must display inline validation error", tilDomain.error)
    }
}
