package com.warden.app.feature.blocker.service

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.warden.app.feature.blocker.domain.KeywordMatch
import com.warden.app.feature.blocker.domain.KeywordMatcher

/**
 * Safely traverses Android [AccessibilityNodeInfo] trees and recycles every obtained child node
 * in `finally` blocks on API < 33 so the accessibility node pool is never leaked.
 */
object AccessibilityNodeScanner {
    /**
     * Depth-first search over [node] inspecting `text` and `contentDescription` until [matcher]
     * finds a blocked keyword. Recycles all intermediate child nodes before returning.
     */
    fun findFirstMatch(
        node: AccessibilityNodeInfo?,
        matcher: KeywordMatcher,
    ): KeywordMatch? {
        node ?: return null
        matchNodeText(node, matcher)?.let { return it }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                findFirstMatch(child, matcher)?.let { return it }
            } finally {
                recycleNodeCompat(child)
            }
        }
        return null
    }

    private fun matchNodeText(
        node: AccessibilityNodeInfo,
        matcher: KeywordMatcher,
    ): KeywordMatch? {
        val nodeText = node.text?.toString()
        if (!nodeText.isNullOrEmpty()) {
            matcher.findMatch(nodeText)?.let { return it }
        }
        val contentDesc = node.contentDescription?.toString()
        return if (!contentDesc.isNullOrEmpty()) matcher.findMatch(contentDesc) else null
    }

    /**
     * Collects all non-empty `text` and `contentDescription` values from [node] into [targetList],
     * recycling every child node in `finally`.
     */
    fun collectScreenTexts(
        node: AccessibilityNodeInfo?,
        targetList: MutableList<String>,
    ) {
        node ?: return
        node.text?.toString()?.takeIf { it.isNotEmpty() }?.let { targetList.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotEmpty() }?.let { targetList.add(it) }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectScreenTexts(child, targetList)
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
