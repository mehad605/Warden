package com.warden.app.utils

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object GeminiManager {

    data class GeminiResult(
        val approved: Boolean,
        val durationMinutes: Int,
        val reasoning: String,
        val botResponse: String
    )

    private data class ModelListResponse(val models: List<ModelItem>?)
    private data class ModelItem(val name: String, val supportedGenerationMethods: List<String>?)

    fun validateUserText(text: String, isRemoval: Boolean = false): String? {
        if (isRemoval) return null

        val sentences = text.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        if (sentences.size < 7) {
            return "Your reasoning must contain at least 7 sentences (currently: ${sentences.size})."
        }
        
        for (i in sentences.indices) {
            val s = sentences[i]
            val words = s.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size < 5) {
                return "Sentence ${i + 1} is too short (\"$s\"). Each sentence must contain at least 5 words."
            }
            
            // Exclude sentence formats that are just word repetitions or comma-separated lists
            val distinctWords = words.map { it.lowercase(Locale.ROOT) }.distinct()
            if (distinctWords.size <= words.size / 2) {
                return "Sentence ${i + 1} is repetitive or list-like. Please write a natural explanation."
            }
        }
        
        return null // Valid
    }

    suspend fun fetchModelsList(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<String>()
        try {
            val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doInput = true
            
            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = Gson().fromJson(responseText, ModelListResponse::class.java)
                parsed.models?.forEach { model ->
                    if (model.supportedGenerationMethods?.contains("generateContent") == true) {
                        resultList.add(model.name)
                    }
                }
            } else {
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
        resultList
    }

    suspend fun requestExemption(
        apiKey: String,
        modelName: String,
        appName: String,
        packageName: String,
        userPrompt: String,
        image: Bitmap?,
        noCount: Int,
        isRemoval: Boolean
    ): GeminiResult = withContext(Dispatchers.IO) {
        val systemPrompt = if (isRemoval) {
            """
                You are the Gatekeeper of Block Words. The user wants to REMOVE '$appName' (package: '$packageName') from their whitelists/ignore lists so that keyword blocking starts active on it again.
                
                Strict Guidelines:
                1. Your default response must be YES (approved = true). The user is attempting to re-impose focus restrictions upon themselves.
                2. Be extremely lenient and eager to approve. Support their decision to stay disciplined.
                3. Set durationMinutes to 0 since they want to permanently remove it from the ignore list.
                4. Respond ONLY with a raw JSON object (do NOT wrap in markdown code blocks, just raw text):
                {
                  "approved": boolean,
                  "durationMinutes": 0,
                  "reasoning": "user wants to block app again, approved eagerly",
                  "botResponse": "Excellence decision. I have removed $appName from the ignore list. Keywords will be blocked on it again."
                }
            """.trimIndent()
        } else {
            """
                You are the Gatekeeper of Block Words. The user wants to temporarily add '$appName' (package: '$packageName') to the ignore list (bypassing keyword blocking).
                User's historical rejection count: $noCount.

                Strict Guidelines:
                1. Your default response must be NO. You are an anti-procrastination gatekeeper.
                2. Analyze the app context. If the app is a browser, messaging client, downloader, or social app, you must refuse whitelisting unless the justification is an absolute study/work emergency with convincing logic and/or image proof.
                3. If approved, durationMinutes must be strictly between 5 and 60 minutes. Grant a minimum reasonable duration just enough to complete the described task.
                4. You must respond ONLY with a raw JSON object matching the following structure (do NOT wrap in markdown code blocks, just raw text):
                {
                  "approved": boolean,
                  "durationMinutes": integer,
                  "reasoning": "your internal logic for this decision",
                  "botResponse": "message to display to the user explaining your decision"
                }
            """.trimIndent()
        }

        val model = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            },
            systemInstruction = content { text(systemPrompt) }
        )

        val response = if (image != null) {
            model.generateContent(content {
                image(image)
                text(userPrompt)
            })
        } else {
            model.generateContent(userPrompt)
        }

        val json = response.text ?: "{}"
        Gson().fromJson(json, GeminiResult::class.java)
    }

    suspend fun requestPauseBlocker(
        apiKey: String,
        modelName: String,
        userPrompt: String,
        image: Bitmap?,
        noCount: Int
    ): GeminiResult = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are the Gatekeeper of Block Words. The user is requesting to temporarily disable the entire keyword blocker.
            User's historical rejection count: $noCount.

            Strict Guidelines:
            1. Your default response must be NO. Disabling the blocker exposes the user to immediate distractions.
            2. Only say YES if the user's justification is extremely critical, AND the user has provided one of the following three:
               - An email address in their message (e.g. user@domain.com)
               - A web link/URL in their message (e.g. http://... or https://... or www....)
               - An attached image/screenshot showing proof of necessity
            3. If NONE of the three proofs (email, link, or image) are present, you MUST reject the request (set approved = false).
            4. If approved, durationMinutes must be strictly between 5 and 15 minutes.
            5. You must respond ONLY with a raw JSON object matching the following structure (do NOT wrap in markdown code blocks, just raw text):
            {
              "approved": boolean,
              "durationMinutes": integer,
              "reasoning": "your internal logic for this decision",
              "botResponse": "message explaining your decision. If rejected due to missing proof, explicitly tell the user that they must include an email, a link, or an image."
            }
        """.trimIndent()

        val model = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            },
            systemInstruction = content { text(systemPrompt) }
        )

        val response = if (image != null) {
            model.generateContent(content {
                image(image)
                text(userPrompt)
            })
        } else {
            model.generateContent(userPrompt)
        }
        val json = response.text ?: "{}"
        Gson().fromJson(json, GeminiResult::class.java)
    }

    suspend fun requestExtension(
        apiKey: String,
        modelName: String,
        appName: String,
        packageName: String,
        userPrompt: String,
        image: Bitmap?,
        noCount: Int
    ): GeminiResult = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are the Gatekeeper of Block Words. The user is requesting to EXTEND their current temporary ignore/whitelist duration for the app '$appName' (package: '$packageName').
            User's historical rejection count: $noCount.

            Strict Guidelines:
            1. Your default response must be NO. You are an anti-procrastination gatekeeper.
            2. Analyze the app context. If the app is a browser, messaging client, downloader, or social app, you must refuse extension unless the justification is an absolute study/work emergency with convincing logic and/or image proof.
            3. If approved, durationMinutes must be strictly between 5 and 60 minutes. Grant a minimum reasonable duration just enough to complete the described task.
            4. You must respond ONLY with a raw JSON object matching the following structure (do NOT wrap in markdown code blocks, just raw text):
            {
              "approved": boolean,
              "durationMinutes": integer,
              "reasoning": "your internal logic for this decision",
              "botResponse": "message explaining your decision"
            }
        """.trimIndent()

        val model = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            },
            systemInstruction = content { text(systemPrompt) }
        )

        val response = if (image != null) {
            model.generateContent(content {
                image(image)
                text(userPrompt)
            })
        } else {
            model.generateContent(userPrompt)
        }
        val json = response.text ?: "{}"
        Gson().fromJson(json, GeminiResult::class.java)
    }
}
