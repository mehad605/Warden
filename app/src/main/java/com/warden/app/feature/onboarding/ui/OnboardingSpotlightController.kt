package com.warden.app.feature.onboarding.ui

import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.widget.NestedScrollView
import com.warden.app.R
import com.warden.app.databinding.ViewSpotlightTooltipBinding

/**
 * Interactive spotlight coach-mark tour overlaid directly onto the live Sentinel dashboard.
 *
 * Highlights real UI controls across Shield, Rules, and Vault—auto-switching tabs and
 * scrolling each target into view—while positioning a scroll-safe tooltip card above or
 * below the cutout so nothing ever clips or overflows on any screen size.
 */
class OnboardingSpotlightController(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val rootContainer: FrameLayout,
    private val scrollContainer: NestedScrollView,
    private val steps: List<Step>,
    private val onSelectTab: (Int) -> Unit,
    private val isAccessibilityEnabled: () -> Boolean,
    private val onOpenAccessibilitySettings: () -> Unit,
    private val onFinished: () -> Unit = {},
) {
    /** Single spotlight step bound to a tab index, optional sub-view preparation, and target view. */
    data class Step(
        val tabIndex: Int,
        val targetView: () -> View,
        @StringRes val titleRes: Int,
        @StringRes val bodyRes: Int,
        val showAccessibilityCta: Boolean = false,
        val onPrepare: () -> Unit = {},
    )

    private var hostLayer: FrameLayout? = null
    private var overlayView: SpotlightOverlayView? = null
    private var tooltipBinding: ViewSpotlightTooltipBinding? = null
    private var stepIndex = 0

    /** Returns `true` when the spotlight tour is currently attached and visible. */
    fun isShowing(): Boolean = hostLayer != null

    /** Attaches and starts the spotlight tour from step 1. */
    fun show() {
        if (steps.isEmpty()) return
        dismiss()
        stepIndex = 0

        val container = FrameLayout(context)
        val overlay = SpotlightOverlayView(context)
        val tooltip = ViewSpotlightTooltipBinding.inflate(layoutInflater, container, false)
        container.addView(
            overlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        val marginPx = dpToPx(TOOLTIP_MARGIN_DP)
        val tooltipParams =
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
                marginStart = marginPx
                marginEnd = marginPx
            }
        container.addView(tooltip.root, tooltipParams)
        rootContainer.removeAllViews()
        rootContainer.visibility = View.VISIBLE
        rootContainer.addView(
            container,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        hostLayer = container
        overlayView = overlay
        tooltipBinding = tooltip

        bindTooltipControls(tooltip)
        renderCurrentStep()
    }

    /** Detaches the spotlight overlay cleanly without leaking views. */
    fun dismiss() {
        hostLayer?.let { rootContainer.removeView(it) }
        rootContainer.visibility = View.GONE
        hostLayer = null
        overlayView = null
        tooltipBinding = null
    }

    private fun bindTooltipControls(tooltip: ViewSpotlightTooltipBinding) {
        tooltip.btnGuideAction.setOnClickListener { onOpenAccessibilitySettings() }
        tooltip.btnGuideSkip.setOnClickListener { finishTour() }
        tooltip.btnGuideBack.setOnClickListener {
            if (stepIndex > 0) {
                stepIndex--
                renderCurrentStep()
            }
        }
        tooltip.btnGuideNext.setOnClickListener {
            if (stepIndex < steps.lastIndex) {
                stepIndex++
                renderCurrentStep()
            } else {
                finishTour()
            }
        }
    }

    private fun finishTour() {
        dismiss()
        onSelectTab(0)
        onFinished()
    }

    private fun renderCurrentStep() {
        val tooltip = tooltipBinding ?: return
        val step = steps[stepIndex]

        tooltip.tvGuideStepBadge.text = context.getString(R.string.guide_step_fmt, stepIndex + 1, steps.size)
        tooltip.tvGuideTitle.setText(step.titleRes)
        tooltip.tvGuideBody.setText(step.bodyRes)
        tooltip.btnGuideBack.visibility = if (stepIndex == 0) View.INVISIBLE else View.VISIBLE
        tooltip.btnGuideNext.setText(if (stepIndex == steps.lastIndex) R.string.guide_done else R.string.guide_next)

        val needsA11y = step.showAccessibilityCta && !isAccessibilityEnabled()
        tooltip.btnGuideAction.visibility = if (needsA11y) View.VISIBLE else View.GONE

        onSelectTab(step.tabIndex)
        step.onPrepare()
        rootContainer.post { scrollAndHighlight(step.targetView()) }
    }

    private fun scrollAndHighlight(target: View) {
        if (hostLayer == null) return
        scrollTargetIntoView(target)
        target.post {
            if (hostLayer == null) return@post
            val rect = computeTargetRect(target)
            overlayView?.setTargetRect(rect)
            positionTooltip(rect)
        }
    }

    private fun scrollTargetIntoView(target: View) {
        var y = 0
        var current: View? = target
        while (current != null && current !== scrollContainer) {
            y += current.top
            current = current.parent as? View
        }
        val desiredScroll = (y - dpToPx(SCROLL_TOP_PADDING_DP)).coerceAtLeast(0)
        scrollContainer.scrollTo(0, desiredScroll)
    }

    private fun computeTargetRect(target: View): RectF? {
        if (target.width <= 0 || target.height <= 0) return null
        val rootLoc = IntArray(2)
        val targetLoc = IntArray(2)
        rootContainer.getLocationInWindow(rootLoc)
        target.getLocationInWindow(targetLoc)

        val pad = dpToPx(SPOTLIGHT_PADDING_DP).toFloat()
        val left = (targetLoc[0] - rootLoc[0] - pad).coerceAtLeast(dpToPx(4).toFloat())
        val top = (targetLoc[1] - rootLoc[1] - pad).coerceAtLeast(dpToPx(4).toFloat())
        val right = (targetLoc[0] - rootLoc[0] + target.width + pad).coerceAtMost(rootContainer.width - dpToPx(4).toFloat())
        val bottom = (targetLoc[1] - rootLoc[1] + target.height + pad).coerceAtMost(rootContainer.height - dpToPx(4).toFloat())
        return RectF(left, top, right, bottom)
    }

    private fun positionTooltip(targetRect: RectF?) {
        val tooltip = tooltipBinding ?: return
        val card = tooltip.root
        val params = card.layoutParams as FrameLayout.LayoutParams
        val rootHeight = rootContainer.height
        val edgeMargin = dpToPx(TOOLTIP_MARGIN_DP)
        val gap = dpToPx(TOOLTIP_GAP_DP)

        if (targetRect == null || rootHeight <= 0) {
            params.gravity = Gravity.CENTER_VERTICAL
            params.topMargin = edgeMargin
            params.bottomMargin = edgeMargin
            card.layoutParams = params
            return
        }

        val spaceAbove = (targetRect.top - edgeMargin - gap).toInt().coerceAtLeast(0)
        val spaceBelow = (rootHeight - targetRect.bottom - edgeMargin - gap).toInt().coerceAtLeast(0)
        val minSpace = dpToPx(MIN_TOOLTIP_SPACE_DP)

        when {
            spaceBelow >= spaceAbove && spaceBelow >= minSpace -> {
                params.gravity = Gravity.TOP
                params.topMargin = (targetRect.bottom + gap).toInt().coerceAtLeast(edgeMargin)
                params.bottomMargin = edgeMargin
            }
            spaceAbove >= minSpace -> {
                params.gravity = Gravity.BOTTOM
                params.topMargin = edgeMargin
                params.bottomMargin = (rootHeight - targetRect.top + gap).toInt().coerceAtLeast(edgeMargin)
            }
            else -> {
                params.gravity = Gravity.BOTTOM
                params.topMargin = edgeMargin
                params.bottomMargin = edgeMargin
            }
        }
        card.layoutParams = params
        tooltip.scrollSpotlightTooltip.scrollTo(0, 0)
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val TOOLTIP_MARGIN_DP = 16
        private const val TOOLTIP_GAP_DP = 12
        private const val SPOTLIGHT_PADDING_DP = 8
        private const val SCROLL_TOP_PADDING_DP = 24
        private const val MIN_TOOLTIP_SPACE_DP = 220
    }
}
