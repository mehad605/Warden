package com.warden.app

import com.warden.app.core.data.SettingsJsonCodec
import com.warden.app.data.models.BlockedEntry
import com.warden.app.data.models.MatchMode
import com.warden.app.data.models.Settings
import com.warden.app.feature.blocker.domain.KeywordMatcher
import com.warden.app.feature.blocker.domain.KeywordNormalizer
import com.warden.app.feature.blocker.domain.TermValidationResult
import com.warden.app.feature.blocker.ui.BlockerAction
import com.warden.app.feature.websites.domain.WebsiteDomainMatcher
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Table-driven scoring harness (Section 8 & 9) measuring word/phrase matching, migration,
 * domain blocking, edge cases, and 10k-character p95 latency.
 *
 * Run via `bash scripts/score.sh`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class BlockerScoreSuiteTest {
    private data class CheckResult(
        val id: String,
        val passed: Boolean,
        val detail: String = "",
    )

    @Test
    fun runTableDrivenScoreSuite() {
        val raw =
            requireNotNull(javaClass.classLoader?.getResourceAsStream("blocker_score_fixtures.json")) {
                "Missing blocker_score_fixtures.json test resource"
            }.bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val results = mutableListOf<CheckResult>()

        evaluateWordCases(root, results)
        evaluateDomainCases(root, results)
        evaluateMigrationAndEdgeCases(results)
        val p95Ms = measureTenThousandCharP95Ms(results)

        val passed = results.count { it.passed }
        val total = results.size
        val failures = results.filterNot { it.passed }

        println("=== BLOCKER SCORE REPORT ===")
        println("PASS $passed/$total")
        println("P95_LATENCY_10K_MS=${"%.2f".format(p95Ms)}")
        if (failures.isNotEmpty()) {
            println("FAILING_CASES:")
            failures.forEach { println("  - ${it.id}: ${it.detail}") }
        }
        println("=== END SCORE REPORT ===")

        val reportOnly = System.getProperty("score.reportOnly")?.toBoolean() ?: false
        if (!reportOnly) {
            assertTrue(
                "Score $passed/$total (< 100%). Failing: ${failures.joinToString { "${it.id}(${it.detail})" }}",
                failures.isEmpty(),
            )
        }
    }

    private fun evaluateWordCases(
        root: JSONObject,
        out: MutableList<CheckResult>,
    ) {
        val cases = root.getJSONArray("wordCases")
        for (i in 0 until cases.length()) {
            val obj = cases.getJSONObject(i)
            val id = obj.getString("id")
            val blockedArr = obj.getJSONArray("blocked")
            val blockedEntries =
                List(blockedArr.length()) { idx ->
                    val entry = blockedArr.getJSONObject(idx)
                    BlockedEntry(
                        term = entry.getString("term"),
                        mode = MatchMode.fromWire(entry.optString("mode", "exact")),
                    )
                }
            val whitelistArr = obj.getJSONArray("whitelist")
            val whitelist = List(whitelistArr.length()) { idx -> whitelistArr.getString(idx) }
            val text = obj.getString("text")
            val expectedBlock = obj.getString("expected") == "BLOCK"

            try {
                val matcher = KeywordMatcher(blockedEntries, whitelist)
                val actualBlock = matcher.findMatch(text) != null
                out.add(
                    CheckResult(
                        id = id,
                        passed = actualBlock == expectedBlock,
                        detail =
                            if (actualBlock == expectedBlock) {
                                ""
                            } else {
                                "expected=${obj.getString("expected")} actual=${if (actualBlock) "BLOCK" else "ALLOW"}"
                            },
                    ),
                )
            } catch (e: IllegalArgumentException) {
                out.add(CheckResult(id = id, passed = false, detail = "threw ${e.javaClass.simpleName}: ${e.message}"))
            }
        }
    }

    private fun evaluateDomainCases(
        root: JSONObject,
        out: MutableList<CheckResult>,
    ) {
        val cases = root.getJSONArray("domainCases")
        for (i in 0 until cases.length()) {
            val obj = cases.getJSONObject(i)
            val id = obj.getString("id")
            val blockedArr = obj.getJSONArray("blockedDomains")
            val blocked = List(blockedArr.length()) { idx -> blockedArr.getString(idx) }.toSet()
            val whiteArr = obj.getJSONArray("whitelistedDomains")
            val white = List(whiteArr.length()) { idx -> whiteArr.getString(idx) }.toSet()
            val text = obj.getString("text")
            val expectedBlock = obj.getString("expected") == "BLOCK"

            val matcher = WebsiteDomainMatcher(isEnabled = true, blockedDomains = blocked, whitelistedDomains = white)
            val actualBlock = matcher.findMatchInText(text) != null
            out.add(
                CheckResult(
                    id = id,
                    passed = actualBlock == expectedBlock,
                    detail =
                        if (actualBlock == expectedBlock) {
                            ""
                        } else {
                            "expected=${obj.getString("expected")} actual=${if (actualBlock) "BLOCK" else "ALLOW"}"
                        },
                ),
            )
        }
    }

    private fun evaluateMigrationAndEdgeCases(out: MutableList<CheckResult>) {
        val legacyJson = """{"keywordBlockerConfig":{"isActive":true,"blockedKeywords":["magi","shorts"]}}"""
        val decoded = SettingsJsonCodec.decodeOrNull(legacyJson)
        val entries = decoded?.keywordBlockerConfig?.effectiveBlockedEntries().orEmpty()
        val migrationOk =
            entries.size == 2 &&
                entries.all { it.mode == MatchMode.EXACT } &&
                KeywordMatcher(entries, emptyList()).findMatch("magi") != null &&
                KeywordMatcher(entries, emptyList()).findMatch("magic") == null
        out.add(CheckResult("migration_legacy_loads_as_exact", migrationOk, if (migrationOk) "" else "legacy migration failed"))

        val s1 = BlockerAction.AddBlockedKeyword("porn", MatchMode.EXACT).applyTo(Settings(), 100L)
        val s2 = BlockerAction.AddBlockedKeyword("  PÖRN ", MatchMode.CONTAINS).applyTo(s1, 200L)
        val updatedEntries = s2.keywordBlockerConfig.effectiveBlockedEntries()
        val dupOk = updatedEntries.size == 1 && updatedEntries.first().term == "porn" && updatedEntries.first().mode == MatchMode.CONTAINS
        out.add(CheckResult("edge_duplicate_add_updates_mode", dupOk, if (dupOk) "" else "entries=$updatedEntries"))

        val over100 = KeywordNormalizer.validateAndNormalize("a".repeat(101))
        val lenOk = over100 is TermValidationResult.TooLong
        out.add(CheckResult("edge_reject_term_over_100_chars", lenOk, if (lenOk) "" else "got $over100"))
    }

    private fun measureTenThousandCharP95Ms(out: MutableList<CheckResult>): Double {
        val matcher =
            KeywordMatcher(
                listOf(
                    BlockedEntry("magi", MatchMode.EXACT),
                    BlockedEntry("porn", MatchMode.CONTAINS),
                    BlockedEntry("robert denero", MatchMode.EXACT),
                ),
                listOf("document", "cucumber"),
            )
        val chunk = "The quick brown fox jumps over a lazy dog near the river bank. "
        val tenKText = chunk.repeat((10_000 / chunk.length) + 1).take(10_000)
        repeat(5) { matcher.findMatch(tenKText) }
        val samples =
            List(20) {
                val startNs = System.nanoTime()
                matcher.findMatch(tenKText)
                (System.nanoTime() - startNs) / 1_000_000.0
            }.sorted()
        val p95 = samples[(samples.size * 0.95).toInt().coerceAtMost(samples.lastIndex)]
        val ok = p95 < 50.0
        out.add(CheckResult("perf_10k_chars_under_50ms", ok, if (ok) "" else "p95=${"%.2f".format(p95)}ms >= 50ms"))
        return p95
    }
}
