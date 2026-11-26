package com.warden.app.feature.gatekeeper.ui

import android.graphics.Bitmap
import android.util.Base64
import com.warden.app.feature.gatekeeper.domain.EncodedImage
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Downscales and JPEG-compresses user-attached screenshots before sending them to Gemini.
 *
 * Bounding resolution to 1024px caps memory spikes on 4 GB RAM devices and reduces upload size.
 */
object BitmapImageEncoder {

    private const val MAX_DIMENSION_PX = 1024
    private const val JPEG_QUALITY = 80
    private const val MIME_JPEG = "image/jpeg"

    /** Encodes [bitmap] to a bounded JPEG [EncodedImage]. */
    fun encode(bitmap: Bitmap): EncodedImage {
        val scaled = scaleDownIfNeeded(bitmap)
        val out = ByteArrayOutputStream()
        try {
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            return EncodedImage(mimeType = MIME_JPEG, base64Data = base64)
        } finally {
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap): Bitmap {
        val maxEdge = max(bitmap.width, bitmap.height)
        if (maxEdge <= MAX_DIMENSION_PX) return bitmap
        val ratio = MAX_DIMENSION_PX.toFloat() / maxEdge.toFloat()
        val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
