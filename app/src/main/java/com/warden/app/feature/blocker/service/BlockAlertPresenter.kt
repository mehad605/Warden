package com.warden.app.feature.blocker.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import com.warden.app.R
import com.warden.app.databinding.ViewBlockAlertBannerBinding

/**
 * Displays a single, non-stacking, bottom-anchored Sentinel glass-pill overlay banner using
 * `TYPE_ACCESSIBILITY_OVERLAY` (no extra permissions needed) with smooth slide-up entrance and
 * automatic short dismissal so browser URL bars at the top of the screen are never covered.
 *
 * Updates in place if already visible so rapid triggers never queue up stale alerts, and falls
 * back gracefully to a single cancelled `Toast.LENGTH_SHORT` if an OEM rejects overlay attachment.
 */
class BlockAlertPresenter(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val displayDurationMs: Long = DEFAULT_DISPLAY_DURATION_MS,
) {
    /** Structured title and detail extracted from a raw blocker alert message. */
    data class AlertCopy(
        val title: String,
        val detail: String,
    )

    private var overlayBinding: ViewBlockAlertBannerBinding? = null
    private var fallbackToast: Toast? = null
    private var isShutDown = false

    private val autoDismissRunnable = Runnable { animateOutAndDetach() }

    /** Shows or updates the floating alert pill for [message] and resets the dismiss timer. */
    fun show(message: String) {
        mainHandler.post {
            if (isShutDown) return@post
            val copy = parseMessage(message)
            if (!showOverlay(copy)) {
                showFallbackToast(message)
            }
        }
    }

    /** Immediately hides any visible alert banner or fallback toast. */
    fun dismiss() {
        mainHandler.post {
            mainHandler.removeCallbacks(autoDismissRunnable)
            fallbackToast?.cancel()
            fallbackToast = null
            animateOutAndDetach()
        }
    }

    /** Detaches overlay views and cancels timers permanently when the service stops. */
    fun shutdown() {
        mainHandler.post {
            isShutDown = true
            mainHandler.removeCallbacks(autoDismissRunnable)
            fallbackToast?.cancel()
            fallbackToast = null
            detachOverlayImmediately()
        }
    }

    private fun showOverlay(copy: AlertCopy): Boolean {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val existing = overlayBinding
        if (existing != null && existing.root.isAttachedToWindow) {
            bindCopy(existing, copy)
            existing.root.animate().cancel()
            existing.root.alpha = 1f
            existing.root.translationY = 0f
            scheduleAutoDismiss()
            return true
        }
        detachOverlayImmediately()
        val themedContext = ContextThemeWrapper(context, R.style.Theme_BlockWords)
        val banner = ViewBlockAlertBannerBinding.inflate(LayoutInflater.from(themedContext))
        bindCopy(banner, copy)
        val slidePx = SLIDE_OFFSET_DP * context.resources.displayMetrics.density
        banner.root.alpha = 0f
        banner.root.translationY = slidePx

        return try {
            windowManager.addView(banner.root, buildLayoutParams(windowManager))
            overlayBinding = banner
            banner.root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ENTER_ANIM_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
            scheduleAutoDismiss()
            true
        } catch (_: WindowManager.BadTokenException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    private fun bindCopy(
        banner: ViewBlockAlertBannerBinding,
        copy: AlertCopy,
    ) {
        banner.tvAlertTitle.text = copy.title
        banner.tvAlertDetail.text = copy.detail
    }

    private fun buildLayoutParams(windowManager: WindowManager): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = resolveBottomOffsetPx(windowManager)
        }

    private fun resolveBottomOffsetPx(windowManager: WindowManager): Int {
        val density = context.resources.displayMetrics.density
        val navBarPx =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                (DEFAULT_NAV_BAR_DP * density).toInt()
            }
        return navBarPx + (BOTTOM_EXTRA_MARGIN_DP * density).toInt()
    }

    private fun scheduleAutoDismiss() {
        mainHandler.removeCallbacks(autoDismissRunnable)
        mainHandler.postDelayed(autoDismissRunnable, displayDurationMs)
    }

    private fun animateOutAndDetach() {
        val banner = overlayBinding ?: return
        if (!banner.root.isAttachedToWindow) {
            overlayBinding = null
            return
        }
        val slideOutPx = SLIDE_EXIT_DP * context.resources.displayMetrics.density
        banner.root.animate().cancel()
        banner.root.animate()
            .alpha(0f)
            .translationY(slideOutPx)
            .setDuration(EXIT_ANIM_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (overlayBinding === banner) {
                    detachOverlayImmediately()
                }
            }
            .start()
    }

    private fun detachOverlayImmediately() {
        val banner = overlayBinding ?: return
        overlayBinding = null
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        try {
            if (banner.root.isAttachedToWindow) {
                windowManager.removeViewImmediate(banner.root)
            }
        } catch (_: IllegalArgumentException) {
            // Already detached by window manager.
        }
    }

    private fun showFallbackToast(message: String) {
        fallbackToast?.cancel()
        fallbackToast = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    /** Formats [message] into a concise banner headline and detail line. */
    fun parseMessage(message: String): AlertCopy {
        val match = BLOCKED_MESSAGE_REGEX.matchEntire(message.trim())
        if (match != null) {
            val keyword = match.groupValues[1].trim()
            val snippet = match.groupValues[2].trim()
            return AlertCopy(
                title = context.getString(R.string.alert_banner_blocked_title_fmt, keyword),
                detail = context.getString(R.string.alert_banner_snippet_fmt, snippet),
            )
        }
        return AlertCopy(
            title = context.getString(R.string.alert_banner_default_title),
            detail = message.trim(),
        )
    }

    companion object {
        private const val DEFAULT_DISPLAY_DURATION_MS = 2_000L
        private const val ENTER_ANIM_MS = 220L
        private const val EXIT_ANIM_MS = 180L
        private const val SLIDE_OFFSET_DP = 28f
        private const val SLIDE_EXIT_DP = 20f
        private const val DEFAULT_NAV_BAR_DP = 48
        private const val BOTTOM_EXTRA_MARGIN_DP = 16
        private val BLOCKED_MESSAGE_REGEX = Regex("""^Blocked word (.+?) was found\s*:\s*"\s*(.*?)\s*"$""")
    }
}
