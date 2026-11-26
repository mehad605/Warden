package com.warden.app.feature.gatekeeper.data

import com.warden.app.feature.gatekeeper.domain.EncodedImage
import com.warden.app.feature.gatekeeper.domain.GatekeeperClient
import com.warden.app.feature.gatekeeper.domain.GatekeeperPromptBuilder
import com.warden.app.feature.gatekeeper.domain.GatekeeperRequest
import com.warden.app.feature.gatekeeper.domain.GatekeeperVerdict
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lightweight Gemini REST implementation of [GatekeeperClient] using platform [HttpURLConnection]
 * and `org.json`.
 *
 * Replaces `com.google.ai.client.generativeai` (which pulled Ktor, OkHttp, and kotlinx.serialization
 * totaling ~16,500 DEX methods) with zero third-party runtime dependencies.
 *
 * @property baseUrl Base Gemini REST URL (overridable in tests with a local HTTP server).
 * @property ioDispatcher Coroutine dispatcher used for blocking socket I/O.
 */
class HttpGeminiGatekeeperClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onError: (Exception) -> Unit = {}
) : GatekeeperClient {

    override suspend fun fetchModels(apiKey: String): List<String> = withContext(ioDispatcher) {
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        val url = URL("$baseUrl/v1beta/models?key=$encodedKey")
        val connection = openConnection(url, method = "GET")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext emptyList()
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseModelsList(body)
        } catch (e: IOException) {
            onError(e)
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun evaluate(
        apiKey: String,
        modelName: String,
        request: GatekeeperRequest,
        image: EncodedImage?
    ): GatekeeperVerdict = withContext(ioDispatcher) {
        val normalizedModel = normalizeModelPath(modelName)
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        val url = URL("$baseUrl/v1beta/$normalizedModel:generateContent?key=$encodedKey")
        val payload = buildGenerateContentPayload(
            systemPrompt = GatekeeperPromptBuilder.buildSystemPrompt(request),
            userPrompt = request.userPrompt,
            image = image
        )
        val responseBody = executePost(url, payload)
        parseVerdictResponse(responseBody)
    }

    private fun openConnection(url: URL, method: String): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doInput = true
        }

    private fun executePost(url: URL, payload: String): String {
        val connection = openConnection(url, method = "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("Gemini API request failed (HTTP $code): $body")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    internal fun buildGenerateContentPayload(
        systemPrompt: String,
        userPrompt: String,
        image: EncodedImage?
    ): String {
        val userParts = JSONArray().apply {
            if (image != null) {
                put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", image.mimeType)
                            .put("data", image.base64Data)
                    )
                )
            }
            put(JSONObject().put("text", userPrompt))
        }
        return JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", userParts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            .toString()
    }

    private fun parseModelsList(body: String): List<String> {
        val root = JSONObject(body)
        val models = root.optJSONArray("models") ?: return emptyList()
        return buildList {
            for (i in 0 until models.length()) {
                val item = models.optJSONObject(i) ?: continue
                val methods = item.optJSONArray("supportedGenerationMethods") ?: continue
                if (methods.containsString("generateContent")) {
                    val name = item.optString("name")
                    if (name.isNotBlank()) add(name)
                }
            }
        }
    }

    private fun parseVerdictResponse(responseBody: String): GatekeeperVerdict {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IOException("Gemini response missing candidates")
        val firstCandidate = candidates.optJSONObject(0)
            ?: throw IOException("Gemini response returned empty candidates")
        val parts = firstCandidate.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Gemini response missing content parts")
        val rawText = parts.optJSONObject(0)?.optString("text").orEmpty()
        val cleaned = stripMarkdownCodeFences(rawText)
        val verdictJson = JSONObject(cleaned.ifBlank { "{}" })
        return GatekeeperVerdict(
            approved = verdictJson.optBoolean("approved", false),
            durationMinutes = verdictJson.optInt("durationMinutes", 0),
            reasoning = verdictJson.optString("reasoning", ""),
            botResponse = verdictJson.optString("botResponse", "No response from AI.")
        )
    }

    private fun normalizeModelPath(modelName: String): String {
        val trimmed = modelName.trim().ifEmpty { DEFAULT_MODEL }
        return if (trimmed.startsWith("models/")) trimmed else "models/$trimmed"
    }

    private fun stripMarkdownCodeFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        val closingFence = trimmed.lastIndexOf("```")
        return if (firstNewline in 0 until closingFence) {
            trimmed.substring(firstNewline + 1, closingFence).trim()
        } else {
            trimmed
        }
    }

    private fun JSONArray.containsString(value: String): Boolean {
        for (i in 0 until length()) {
            if (optString(i) == value) return true
        }
        return false
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"
        private const val DEFAULT_MODEL = "models/gemini-1.5-flash"
        private const val TIMEOUT_MS = 15_000
    }
}
