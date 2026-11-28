package com.warden.app.feature.blocker.domain

import java.text.Collator
import java.util.Locale

/** Display badge style for a row inside the shared sorted rule list. */
enum class RuleBadgeKind {
    NONE,
    EXACT,
    CONTAINS,
    BUILTIN,
    USER,
    DISABLED,
    ALLOWED,
}

/** Action semantics for a rule row, used to render its icon and TalkBack description lazily. */
enum class RuleActionKind {
    DELETE,
    DISABLE_BUILTIN,
    ENABLE_BUILTIN,
}

/** Single data item passed into [RuleListFormatter] for dictionary sorting and letter grouping. */
data class RuleItemSpec(
    val id: String,
    val label: String,
    val badgeText: String? = null,
    val badgeKind: RuleBadgeKind = RuleBadgeKind.NONE,
    val badgeClickable: Boolean = false,
    val actionKind: RuleActionKind = RuleActionKind.DELETE,
)

/** Flat row rendered by the virtualized `RecyclerView` (either a letter section header or a rule item). */
sealed class RuleListRow {
    /** Stable key for `DiffUtil`. */
    abstract val stableId: String

    /** Section header for a letter bucket (`A`..`Z` or `#`). */
    data class Header(
        val letter: String,
        val count: Int,
    ) : RuleListRow() {
        override val stableId: String = "header:$letter"
    }

    /** Single interactive rule row. */
    data class Entry(
        val spec: RuleItemSpec,
    ) : RuleListRow() {
        override val stableId: String = "entry:${spec.id}"
    }
}

/**
 * Sorted, letter-grouped projection of rule entries ready for virtualized rendering.
 *
 * @property rows Flat list of interleaved [RuleListRow.Header] and [RuleListRow.Entry] items.
 * @property letterPositions Map from each non-empty bucket letter to its adapter index in [rows].
 */
data class GroupedRuleList(
    val rows: List<RuleListRow>,
    val letterPositions: Map<String, Int>,
)

/**
 * Pure Kotlin dictionary sorter and letter-bucket grouper using `java.text.Collator`
 * (`PRIMARY` strength: case-insensitive and accent-insensitive).
 */
object RuleListFormatter {
    /**
     * Filters [items] by [query] (normalized substring), sorts in dictionary order, and groups
     * under non-empty letter headers (`A`..`Z` and `#` for digits/symbols).
     */
    fun buildGroupedList(
        items: List<RuleItemSpec>,
        query: String = "",
    ): GroupedRuleList {
        val normQuery = TextNormalizer.normalizeTerm(query)
        val filtered =
            if (normQuery.isEmpty()) {
                items
            } else {
                items.filter { TextNormalizer.normalize(it.label).contains(normQuery) }
            }
        if (filtered.isEmpty()) return GroupedRuleList(emptyList(), emptyMap())
        return groupSorted(sortDictionaryOrder(filtered))
    }

    private fun sortDictionaryOrder(items: List<RuleItemSpec>): List<RuleItemSpec> {
        val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
        val keyed = items.map { it to collator.getCollationKey(it.label) }
        return keyed
            .sortedWith { a, b ->
                val cmp = a.second.compareTo(b.second)
                if (cmp != 0) cmp else a.first.label.compareTo(b.first.label)
            }
            .map { it.first }
    }

    private fun groupSorted(sorted: List<RuleItemSpec>): GroupedRuleList {
        val buckets = LinkedHashMap<String, MutableList<RuleItemSpec>>()
        sorted.forEach { item ->
            buckets.getOrPut(bucketLetter(item.label)) { mutableListOf() }.add(item)
        }
        val orderedKeys = buckets.keys.sortedWith(::compareBucketKeys)
        val rows = ArrayList<RuleListRow>(sorted.size + orderedKeys.size)
        val positions = LinkedHashMap<String, Int>(orderedKeys.size)
        orderedKeys.forEach { letter ->
            val group = buckets[letter].orEmpty()
            positions[letter] = rows.size
            rows.add(RuleListRow.Header(letter = letter, count = group.size))
            group.forEach { rows.add(RuleListRow.Entry(it)) }
        }
        return GroupedRuleList(rows = rows, letterPositions = positions)
    }

    private fun compareBucketKeys(
        a: String,
        b: String,
    ): Int =
        when {
            a == b -> 0
            a == "#" -> 1
            b == "#" -> -1
            else -> a.compareTo(b)
        }

    /** Computes the uppercase ASCII bucket letter (`A`..`Z`) or `#` for digits/symbols/other scripts. */
    fun bucketLetter(label: String): String {
        val rawFirst = label.firstOrNull()
        return when {
            rawFirst == null -> "#"
            rawFirst in 'a'..'z' -> rawFirst.uppercaseChar().toString()
            rawFirst in 'A'..'Z' -> rawFirst.toString()
            else -> {
                val first = TextNormalizer.normalizeTerm(label).firstOrNull()
                if (first != null && first in 'a'..'z') first.uppercaseChar().toString() else "#"
            }
        }
    }
}
