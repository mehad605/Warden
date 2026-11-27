package com.warden.app.feature.onboarding.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import com.warden.app.R

/**
 * Full-bleed translucent scrim view that punches a smooth rounded-rectangle spotlight cutout
 * around a live UI target and outlines it with a Sentinel Teal highlight ring.
 */
internal class SpotlightOverlayView(
    context: Context,
) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val cutoutCornerRadiusPx = CUTOUT_CORNER_DP * density
    private val scrimPath = Path()
    private var spotlightRect: RectF? = null

    private val scrimPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SCRIM_COLOR
            style = Paint.Style.FILL
        }

    private val ringPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.sentinel_orb_ring)
            style = Paint.Style.STROKE
            strokeWidth = RING_STROKE_DP * density
        }

    init {
        isClickable = true
        isFocusable = true
    }

    /** Updates the highlighted [rect] in this view's coordinate space and redraws. */
    fun setTargetRect(rect: RectF?) {
        spotlightRect = rect
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        scrimPath.reset()
        scrimPath.fillType = Path.FillType.EVEN_ODD
        scrimPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        val rect = spotlightRect
        if (rect != null && !rect.isEmpty) {
            scrimPath.addRoundRect(rect, cutoutCornerRadiusPx, cutoutCornerRadiusPx, Path.Direction.CW)
        }
        canvas.drawPath(scrimPath, scrimPaint)
        if (rect != null && !rect.isEmpty) {
            canvas.drawRoundRect(rect, cutoutCornerRadiusPx, cutoutCornerRadiusPx, ringPaint)
        }
    }

    companion object {
        private val SCRIM_COLOR = Color.argb(204, 9, 11, 20)
        private const val CUTOUT_CORNER_DP = 20f
        private const val RING_STROKE_DP = 2.5f
    }
}
