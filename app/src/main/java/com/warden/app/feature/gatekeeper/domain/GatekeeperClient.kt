package com.warden.app.feature.gatekeeper.domain

/**
 * Contract for evaluating AI Gatekeeper requests and discovering supported models.
 *
 * Isolating the network transport behind this interface lets the UI and domain remain
 * independent of Gemini REST/HTTP details and makes the AI backend trivially replaceable.
 */
interface GatekeeperClient {
    /** Returns available model identifiers that support content generation for [apiKey]. */
    suspend fun fetchModels(apiKey: String): List<String>

    /** Evaluates [request] with optional [image] proof using [modelName] and [apiKey]. */
    suspend fun evaluate(
        apiKey: String,
        modelName: String,
        request: GatekeeperRequest,
        image: EncodedImage?,
    ): GatekeeperVerdict
}
