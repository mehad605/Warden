package com.warden.app.feature.blocker.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.warden.app.R

/**
 * Renders a compact horizontal letter-jump strip (`A`..`Z`, `#`) for non-empty letter buckets
 * so users can jump straight to any section in long rule lists.
 */
internal object LetterJumpBarBinder {
    /** Populates [container] with focusable jump chips that scroll [recyclerView] to each letter. */
    fun bind(
        context: Context,
        container: LinearLayout,
        scrollWrapper: View,
        letterPositions: Map<String, Int>,
        recyclerView: RecyclerView,
    ) {
        container.removeAllViews()
        if (letterPositions.size <= 1) {
            scrollWrapper.visibility = View.GONE
            return
        }
        scrollWrapper.visibility = View.VISIBLE
        val bg = MaterialColors.getColor(container, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val fg = MaterialColors.getColor(container, com.google.android.material.R.attr.colorPrimary)
        letterPositions.forEach { (letter, position) ->
            container.addView(createChip(context, letter, position, bg, fg, recyclerView))
        }
    }

    private fun createChip(
        context: Context,
        letter: String,
        position: Int,
        bgColor: Int,
        fgColor: Int,
        recyclerView: RecyclerView,
    ): TextView {
        val density = context.resources.displayMetrics.density
        val padH = (9 * density).toInt()
        val padV = (4 * density).toInt()
        return TextView(context).apply {
            text = letter
            gravity = Gravity.CENTER
            setPadding(padH, padV, padH, padV)
            setBackgroundResource(R.drawable.bg_rounded_999)
            backgroundTintList = ColorStateList.valueOf(bgColor)
            setTextColor(fgColor)
            textSize = 12f
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.cd_jump_to_letter, letter)
            setOnClickListener {
                val lm = recyclerView.layoutManager as? LinearLayoutManager
                lm?.scrollToPositionWithOffset(position, 0) ?: recyclerView.scrollToPosition(position)
            }
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = (4 * density).toInt() }
        }
    }
}
