package com.warden.app.feature.blocker.ui

import android.content.Context
import com.warden.app.R
import com.warden.app.data.models.WebsiteBlockProfile
import com.warden.app.feature.blocker.domain.GroupedRuleList
import com.warden.app.feature.blocker.domain.RuleActionKind
import com.warden.app.feature.blocker.domain.RuleBadgeKind
import com.warden.app.feature.blocker.domain.RuleItemSpec
import com.warden.app.feature.blocker.domain.RuleListFormatter
import com.warden.app.feature.blocker.domain.RuleListRow

/**
 * Off-main-thread projection builder that formats the domains of a single [WebsiteBlockProfile]
 * (merging pre-sorted built-in domains when `profile.isBuiltin` is true) into an `A..Z`, `#`
 * letter-grouped list.
 */
internal object WebsiteListBuilder {
    const val PREFIX_USER = "user:"
    const val PREFIX_BUILTIN = "builtin:"
    const val PREFIX_DISABLED = "disabled:"

    /** Builds the sorted, letter-grouped rows for [profile] filtered by [lowerQuery]. */
    fun buildForProfile(
        context: Context,
        profile: WebsiteBlockProfile,
        builtins: List<String>,
        lowerQuery: String,
    ): GroupedRuleList {
        val customSpecs = buildCustomSpecs(context, profile, lowerQuery)
        val merged =
            if (profile.isBuiltin) {
                val userSet = profile.domains.toHashSet()
                val disabledSet = profile.disabledBuiltinDomains.toHashSet()
                val builtinSpecs = buildBuiltinSpecs(context, builtins, userSet, disabledSet, lowerQuery)
                mergeSortedSpecs(customSpecs, builtinSpecs)
            } else {
                customSpecs
            }
        if (merged.isEmpty()) return GroupedRuleList(emptyList(), emptyMap())
        return groupMerged(merged)
    }

    private fun groupMerged(merged: List<RuleItemSpec>): GroupedRuleList {
        val rows = ArrayList<RuleListRow>(merged.size + 27)
        val positions = LinkedHashMap<String, Int>(27)
        var currentLetter: String? = null
        var headerIndex = -1
        var groupCount = 0

        merged.forEach { spec ->
            val letter = RuleListFormatter.bucketLetter(spec.label)
            if (letter != currentLetter) {
                val prevLetter = currentLetter
                if (headerIndex >= 0 && prevLetter != null) {
                    rows[headerIndex] = RuleListRow.Header(prevLetter, groupCount)
                }
                currentLetter = letter
                headerIndex = rows.size
                positions[letter] = headerIndex
                rows.add(RuleListRow.Header(letter, 0))
                groupCount = 0
            }
            rows.add(RuleListRow.Entry(spec))
            groupCount++
        }
        val lastLetter = currentLetter
        if (headerIndex >= 0 && lastLetter != null) {
            rows[headerIndex] = RuleListRow.Header(lastLetter, groupCount)
        }
        return GroupedRuleList(rows, positions)
    }

    private fun buildCustomSpecs(
        context: Context,
        profile: WebsiteBlockProfile,
        lowerQuery: String,
    ): List<RuleItemSpec> {
        val list = ArrayList<RuleItemSpec>()
        val yoursLabel = context.getString(R.string.badge_yours)
        val disabledLabel = context.getString(R.string.badge_disabled)
        profile.domains.filter { lowerQuery.isEmpty() || it.contains(lowerQuery) }.forEach { domain ->
            list.add(RuleItemSpec("$PREFIX_USER$domain", domain, yoursLabel, RuleBadgeKind.USER, actionKind = RuleActionKind.DELETE))
        }
        if (profile.isBuiltin) {
            profile.disabledBuiltinDomains.filter { lowerQuery.isEmpty() || it.contains(lowerQuery) }.forEach { domain ->
                list.add(
                    RuleItemSpec(
                        id = "$PREFIX_DISABLED$domain",
                        label = domain,
                        badgeText = disabledLabel,
                        badgeKind = RuleBadgeKind.DISABLED,
                        actionKind = RuleActionKind.ENABLE_BUILTIN,
                    ),
                )
            }
        }
        return list.sortedWith { a, b -> compareBucketAndLabel(a.label, b.label) }
    }

    private fun buildBuiltinSpecs(
        context: Context,
        builtins: List<String>,
        userSet: Set<String>,
        disabledSet: Set<String>,
        lowerQuery: String,
    ): List<RuleItemSpec> {
        val excluded =
            HashSet<String>(userSet.size + disabledSet.size).apply {
                addAll(userSet)
                addAll(disabledSet)
            }
        val label = context.getString(R.string.badge_builtin)
        val letters = ArrayList<RuleItemSpec>(if (lowerQuery.isEmpty()) builtins.size else 256)
        val nonLetters = ArrayList<RuleItemSpec>()
        for (domain in builtins) {
            val included = domain !in excluded && (lowerQuery.isEmpty() || domain.contains(lowerQuery))
            if (included) {
                val spec =
                    RuleItemSpec(
                        id = "$PREFIX_BUILTIN$domain",
                        label = domain,
                        badgeText = label,
                        badgeKind = RuleBadgeKind.BUILTIN,
                        actionKind = RuleActionKind.DISABLE_BUILTIN,
                    )
                if (RuleListFormatter.bucketLetter(domain) == "#") {
                    nonLetters.add(spec)
                } else {
                    letters.add(spec)
                }
            }
        }
        letters.addAll(nonLetters)
        return letters
    }

    private fun mergeSortedSpecs(
        a: List<RuleItemSpec>,
        b: List<RuleItemSpec>,
    ): List<RuleItemSpec> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val out = ArrayList<RuleItemSpec>(a.size + b.size)
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            if (compareBucketAndLabel(a[i].label, b[j].label) <= 0) {
                out.add(a[i++])
            } else {
                out.add(b[j++])
            }
        }
        while (i < a.size) out.add(a[i++])
        while (j < b.size) out.add(b[j++])
        return out
    }

    private fun compareBucketAndLabel(
        left: String,
        right: String,
    ): Int {
        val lb = RuleListFormatter.bucketLetter(left)
        val rb = RuleListFormatter.bucketLetter(right)
        if (lb != rb) {
            return when {
                lb == "#" -> 1
                rb == "#" -> -1
                else -> lb.compareTo(rb)
            }
        }
        return left.compareTo(right)
    }
}
