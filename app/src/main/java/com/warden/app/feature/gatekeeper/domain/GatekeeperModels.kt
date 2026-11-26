package com.warden.app.feature.gatekeeper.domain

/**
 * Structured verdict returned by the AI Gatekeeper for an exemption, pause, or extension request.
 *
 * @property approved Whether the gatekeeper granted the requested relaxation.
 * @property durationMinutes Duration in minutes granted (`0` for permanent ignore-list removal).
 * @property reasoning Internal rationale produced by the model.
 * @property botResponse User-facing message displayed in the gatekeeper dialog.
 */
data class GatekeeperVerdict(
    val approved: Boolean,
    val durationMinutes: Int,
    val reasoning: String,
    val botResponse: String
)

/**
 * Platform-independent base64-encoded image attachment for multimodal gatekeeper requests.
 *
 * @property mimeType MIME type such as `image/jpeg`.
 * @property base64Data Base64-encoded image bytes without line breaks.
 */
data class EncodedImage(
    val mimeType: String,
    val base64Data: String
)

/**
 * Typed requests handled by [GatekeeperClient].
 */
sealed class GatekeeperRequest {
    /** User-written justification text sent to the gatekeeper. */
    abstract val userPrompt: String

    /** Historical count of rejected requests, used to tighten scrutiny. */
    abstract val rejectionCount: Int

    /** Request to temporarily exempt [packageName] from keyword blocking. */
    data class AppExemption(
        val appName: String,
        val packageName: String,
        override val userPrompt: String,
        override val rejectionCount: Int
    ) : GatekeeperRequest()

    /** Request to remove [packageName] from the ignore list and restore keyword blocking. */
    data class RemoveFromIgnore(
        val appName: String,
        val packageName: String,
        override val userPrompt: String,
        override val rejectionCount: Int
    ) : GatekeeperRequest()

    /** Request to temporarily pause the entire keyword blocker. */
    data class PauseBlocker(
        override val userPrompt: String,
        override val rejectionCount: Int
    ) : GatekeeperRequest()

    /** Request to extend an active temporary exemption for [packageName]. */
    data class ExtendExemption(
        val appName: String,
        val packageName: String,
        override val userPrompt: String,
        override val rejectionCount: Int
    ) : GatekeeperRequest()
}
