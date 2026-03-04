package com.tutu.miaohub.core.engine

import com.tutu.miaohub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 豆包（火山引擎 ARK）AI 实现。
 * OpenAI 兼容格式 + SSE 流式输出，支持图文多模态。
 */
class DoubaoAiProvider(
    private val apiKey: String = BuildConfig.DOUBAO_API_KEY,
    private val baseUrl: String = BuildConfig.DOUBAO_BASE_URL,
    private val modelId: String = BuildConfig.DOUBAO_MODEL_ID,
    private val maxTokens: Int = 30000
) : AiProvider {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 120_000
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(
        prompt: String,
        screenshotBase64: String?,
        uiNodesJson: String?,
        history: List<AiProvider.AiMessage>,
        onToken: ((String) -> Unit)?
    ): String = withContext(Dispatchers.IO) {
        val messages = buildJsonArray {
            addJsonObject {
                put("role", "user")
                put("content", prompt)
            }

            for (msg in history.takeLast(5)) {
                addJsonObject {
                    put("role", "user")
                    put("content", "[Previous context omitted]")
                }
                addJsonObject {
                    put("role", "assistant")
                    put("content", msg.content)
                }
            }

            addJsonObject {
                put("role", "user")
                put("content", buildUserContent(screenshotBase64, uiNodesJson))
            }
        }

        val requestBody = buildJsonObject {
            put("model", modelId)
            put("messages", messages)
            put("temperature", 0.0)
            put("max_tokens", maxTokens)
            put("stream", true)
            putJsonObject("stream_options") { put("include_usage", true) }
            putJsonObject("thinking") { put("type", "disabled") }
        }

        val url = URL("${baseUrl.trimEnd('/')}/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        conn.outputStream.use { out ->
            out.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            out.flush()
        }

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            throw Exception("AI API 错误: ${conn.responseCode} - $err")
        }

        val fullText = StringBuilder()
        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data: ")) continue
                val data = l.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                try {
                    val parsed = json.decodeFromString<JsonObject>(data)
                    if (parsed.containsKey("error")) {
                        throw Exception(parsed["error"]?.jsonPrimitive?.content ?: "AI error")
                    }
                    val token = parsed["choices"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("delta")?.jsonObject?.let { delta ->
                            delta["content"]?.jsonPrimitive?.contentOrNull
                                ?: delta["reasoning_content"]?.jsonPrimitive?.contentOrNull
                        } ?: ""
                    if (token.isNotEmpty()) {
                        fullText.append(token)
                        onToken?.invoke(token)
                    }
                } catch (_: Exception) {
                    // skip malformed SSE lines
                }
            }
        } finally {
            reader.close()
            conn.disconnect()
        }

        fullText.toString()
    }

    private fun buildUserContent(
        screenshotBase64: String?,
        uiNodesJson: String?
    ): JsonElement {
        val parts = buildJsonArray {
            if (!screenshotBase64.isNullOrEmpty()) {
                addJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") {
                        put("url", "data:image/jpeg;base64,$screenshotBase64")
                    }
                }
            }
            if (!uiNodesJson.isNullOrEmpty()) {
                addJsonObject {
                    put("type", "text")
                    put("text", "[UI Node Tree]\n$uiNodesJson")
                }
            }
            if (screenshotBase64.isNullOrEmpty() && uiNodesJson.isNullOrEmpty()) {
                addJsonObject {
                    put("type", "text")
                    put("text", "[No screen context available]")
                }
            }
        }
        return parts
    }
}
