package com.warden.app

import com.warden.app.feature.gatekeeper.data.HttpGeminiGatekeeperClient
import com.warden.app.feature.gatekeeper.domain.EncodedImage
import com.warden.app.feature.gatekeeper.domain.GatekeeperRequest
import com.warden.app.feature.gatekeeper.domain.JustificationValidator
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Characterization tests locking in C11 (model discovery), C12 (local justification validation),
 * and C13 (Gemini REST gatekeeper evaluation including multimodal screenshot payloads).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class GeminiGatekeeperCharacterizationTest {

    private class LocalStubServer(
        private val responseStatus: Int = 200,
        private val responseBody: String
    ) : AutoCloseable {
        private val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        @Volatile
        var capturedRequestLine: String = ""
        @Volatile
        var capturedRequestBody: String = ""

        val baseUrl: String get() = "http://127.0.0.1:${serverSocket.localPort}"

        private val worker = thread(isDaemon = true) {
            try {
                serverSocket.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    capturedRequestLine = reader.readLine().orEmpty()
                    var contentLength = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.lowercase().startsWith("content-length:")) {
                            contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }
                    if (contentLength > 0) {
                        val buf = CharArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val n = reader.read(buf, read, contentLength - read)
                            if (n <= 0) break
                            read += n
                        }
                        capturedRequestBody = String(buf, 0, read)
                    }
                    val bodyBytes = responseBody.toByteArray(Charsets.UTF_8)
                    val headers = "HTTP/1.1 $responseStatus OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${bodyBytes.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(headers.toByteArray(Charsets.UTF_8))
                        write(bodyBytes)
                        flush()
                    }
                }
            } catch (_: Exception) {
                // Socket closed at end of test
            }
        }

        override fun close() {
            serverSocket.close()
            worker.join(1_000)
        }
    }

    private fun validJustification(): String = listOf(
        "I need to access my university portal to submit the final compiler assignment.",
        "The deadline closes in twenty minutes and my laptop battery just died completely.",
        "My professor requires a PDF upload directly through the mobile browser interface.",
        "I have already prepared the finished report document inside my downloads folder.",
        "Without submitting this file now I will lose twenty percent of my grade.",
        "I promise to close the browser tab immediately after receiving the confirmation receipt.",
        "Please grant a brief temporary exemption so I can finish this urgent academic task."
    ).joinToString(" ")

    @Test
    fun c12_removalRequestBypassesValidation() {
        assertNull(JustificationValidator.validate("remove", isRemoval = true))
        assertNull(JustificationValidator.validate("", isRemoval = true))
    }

    @Test
    fun c12_rejectsFewerThanSevenSentences() {
        val text = "This is one complete valid sentence. Here is a second complete valid sentence."
        val error = JustificationValidator.validate(text, isRemoval = false)
        assertNotNull(error)
        assertTrue(error!!.contains("at least 7 sentences"))
    }

    @Test
    fun c12_rejectsSentenceWithFewerThanFiveWords() {
        val text = listOf(
            "I need this app for urgent school work.",
            "Too short here.",
            "My professor requires a PDF upload right away.",
            "I prepared the finished report document already now.",
            "Without submitting this file I lose grade points.",
            "I will close the browser right after upload.",
            "Please grant a short exemption for this task."
        ).joinToString(" ")
        val error = JustificationValidator.validate(text, isRemoval = false)
        assertNotNull(error)
        assertTrue(error!!.contains("at least 5 words"))
    }

    @Test
    fun c12_rejectsRepetitiveSentence() {
        val text = listOf(
            "I need this app for urgent school work.",
            "please please please please please please.",
            "My professor requires a PDF upload right away.",
            "I prepared the finished report document already now.",
            "Without submitting this file I lose grade points.",
            "I will close the browser right after upload.",
            "Please grant a short exemption for this task."
        ).joinToString(" ")
        val error = JustificationValidator.validate(text, isRemoval = false)
        assertNotNull(error)
        assertTrue(error!!.contains("repetitive"))
    }

    @Test
    fun c12_acceptsValidSevenSentenceJustification() {
        assertNull(JustificationValidator.validate(validJustification(), isRemoval = false))
    }

    @Test
    fun c11_fetchModelsFiltersGenerateContentModels() = runTest {
        val json = """
            {
              "models": [
                {"name": "models/gemini-2.0-flash", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/embedding-001", "supportedGenerationMethods": ["embedContent"]},
                {"name": "models/gemini-1.5-pro", "supportedGenerationMethods": ["generateContent", "countTokens"]}
              ]
            }
        """.trimIndent()
        LocalStubServer(responseBody = json).use { server ->
            val dispatcher = StandardTestDispatcher(testScheduler)
            val client = HttpGeminiGatekeeperClient(baseUrl = server.baseUrl, ioDispatcher = dispatcher)
            val models = client.fetchModels("fake-key")
            assertEquals(listOf("models/gemini-2.0-flash", "models/gemini-1.5-pro"), models)
            assertTrue(server.capturedRequestLine.contains("/v1beta/models?key=fake-key"))
        }
    }

    @Test
    fun c13_evaluateSendsMultimodalPayloadAndParsesVerdict() = runTest {
        val responseJson = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "{\"approved\": true, \"durationMinutes\": 15, \"reasoning\": \"Verified academic proof\", \"botResponse\": \"Granted 15 minutes.\"}"
                  }]
                }
              }]
            }
        """.trimIndent()
        LocalStubServer(responseBody = responseJson).use { server ->
            val dispatcher = StandardTestDispatcher(testScheduler)
            val client = HttpGeminiGatekeeperClient(baseUrl = server.baseUrl, ioDispatcher = dispatcher)
            val verdict = client.evaluate(
                apiKey = "fake-key",
                modelName = "models/gemini-2.0-flash",
                request = GatekeeperRequest.AppExemption(
                    appName = "Chrome",
                    packageName = "com.android.chrome",
                    userPrompt = validJustification(),
                    rejectionCount = 2
                ),
                image = EncodedImage("image/jpeg", "QUJD")
            )
            assertTrue(verdict.approved)
            assertEquals(15, verdict.durationMinutes)
            assertEquals("Granted 15 minutes.", verdict.botResponse)
            assertTrue(server.capturedRequestLine.contains("/v1beta/models/gemini-2.0-flash:generateContent?key=fake-key"))
            assertTrue(server.capturedRequestBody.contains("\"inline_data\""))
            assertTrue(server.capturedRequestBody.contains("QUJD"))
            assertTrue(server.capturedRequestBody.contains("historical rejection count: 2"))
        }
    }

    @Test
    fun c13_evaluateStripsMarkdownFencesFromModelOutput() = runTest {
        val responseJson = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "```json\n{\"approved\": false, \"durationMinutes\": 0, \"reasoning\": \"No proof\", \"botResponse\": \"Include an email, link, or screenshot.\"}\n```"
                  }]
                }
              }]
            }
        """.trimIndent()
        LocalStubServer(responseBody = responseJson).use { server ->
            val dispatcher = StandardTestDispatcher(testScheduler)
            val client = HttpGeminiGatekeeperClient(baseUrl = server.baseUrl, ioDispatcher = dispatcher)
            val verdict = client.evaluate(
                apiKey = "fake-key",
                modelName = "gemini-1.5-flash",
                request = GatekeeperRequest.PauseBlocker("Emergency text", rejectionCount = 1),
                image = null
            )
            assertFalse(verdict.approved)
            assertEquals(0, verdict.durationMinutes)
            assertTrue(verdict.botResponse.contains("screenshot"))
        }
    }
}
