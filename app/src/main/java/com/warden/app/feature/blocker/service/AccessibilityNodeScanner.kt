package com.warden.app.feature.blocker.service

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.warden.app.feature.blocker.domain.KeywordMatch
import com.warden.app.feature.blocker.domain.KeywordMatcher

/**
 * Safely traverses Android [AccessibilityNodeInfo] trees with bounded depth and node budgets
 * and recycles every obtained child node in `finally` blocks on API < 33 so the accessibility
 * node pool is never leaked and oversized WebViews cannot cause CPU spikes.
 */
object AccessibilityNodeScanner {
    private const val MAX_NODES_PER_SCAN = 300
    private const val MAX_TREE_DEPTH = 32

    /**
     * Depth-first search over [node] inspecting `text` and `contentDescription` until [matchText]
     * finds a blocked keyword or domain. Recycles all intermediate child nodes before returning.
     */
    fun findFirstMatch(
        node: AccessibilityNodeInfo?,
        matchText: (String) -> KeywordMatch?,
    ): KeywordMatch? {
        node ?: return null
        return findFirstMatchBounded(node, matchText, depth = 0, remaining = intArrayOf(MAX_NODES_PER_SCAN))
    }

    /** Convenience overload using a [KeywordMatcher] directly. */
    fun findFirstMatch(
        node: AccessibilityNodeInfo?,
        matcher: KeywordMatcher,
    ): KeywordMatch? = findFirstMatch(node, matcher::findMatch)

    private fun findFirstMatchBounded(
        node: AccessibilityNodeInfo,
        matchText: (String) -> KeywordMatch?,
        depth: Int,
        remaining: IntArray,
    ): KeywordMatch? {
        if (depth > MAX_TREE_DEPTH || remaining[0] <= 0) return null
        remaining[0]--
        matchNodeText(node, matchText)?.let { return it }

        for (i in 0 until node.childCount) {
            if (remaining[0] <= 0) return null
            val child = node.getChild(i) ?: continue
            try {
                findFirstMatchBounded(child, matchText, depth + 1, remaining)?.let { return it }
            } finally {
                recycleNodeCompat(child)
            }
        }
        return null
    }

    private fun matchNodeText(
        node: AccessibilityNodeInfo,
        matchText: (String) -> KeywordMatch?,
    ): KeywordMatch? {
        val rawText = node.text
        if (!rawText.isNullOrEmpty()) {
            matchText(rawText.toString())?.let { return it }
        }
        val rawDesc = node.contentDescription
        return if (!rawDesc.isNullOrEmpty()) matchText(rawDesc.toString()) else null
    }

    /**
     * Collects all non-empty `text` and `contentDescription` values from [node] into [targetList],
     * bounded by [MAX_NODES_PER_SCAN] and [MAX_TREE_DEPTH], recycling every child node in `finally`.
     */
    fun collectScreenTexts(
        node: AccessibilityNodeInfo?,
        targetList: MutableList<String>,
    ) {
        node ?: return
        collectBounded(node, targetList, depth = 0, remaining = intArrayOf(MAX_NODES_PER_SCAN))
    }

    private fun collectBounded(
        node: AccessibilityNodeInfo,
        targetList: MutableList<String>,
        depth: Int,
        remaining: IntArray,
    ) {
        if (depth > MAX_TREE_DEPTH || remaining[0] <= 0) return
        remaining[0]--
        val rawText = node.text
        if (!rawText.isNullOrEmpty()) targetList.add(rawText.toString())
        val rawDesc = node.contentDescription
        if (!rawDesc.isNullOrEmpty()) targetList.add(rawDesc.toString())

        for (i in 0 until node.childCount) {
            if (remaining[0] <= 0) return
            val child = node.getChild(i) ?: continue
            try {
                collectBounded(child, targetList, depth + 1, remaining)
            } finally {
                recycleNodeCompat(child)
            }
        }
    }

    /** Recycles [node] on API < 33 where [AccessibilityNodeInfo] uses an object pool. */
    fun recycleNodeCompat(node: AccessibilityNodeInfo?) {
        if (node != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    /** Obtains a copy of [event] using the API-appropriate constructor or pool method. */
    fun copyEventCompat(event: AccessibilityEvent): AccessibilityEvent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityEvent(event)
        } else {
            @Suppress("DEPRECATION")
            AccessibilityEvent.obtain(event)
        }

    /** Recycles [event] on API < 33 where [AccessibilityEvent] uses an object pool. */
    fun recycleEventCompat(event: AccessibilityEvent?) {
        if (event != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            event.recycle()
        }
    }
}
