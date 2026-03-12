package com.tutu.meowhub.core.engine

import com.tutu.meowhub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI Provider 实现，支持两种 API 格式：
 * - 豆包/火山引擎：Responses API + SSE，含联网搜索
 * - 其他 OpenAI 兼容接口：Chat Completions API + SSE
 */
class DoubaoAiProvider(
    private val apiKey: String = BuildConfig.DOUBAO_API_KEY,
    private val baseUrl: String = BuildConfig.DOUBAO_BASE_URL,
    private val modelId: String = BuildConfig.DOUBAO_MODEL_ID,
    private val maxTokens: Int = 30000
) : AiProvider {

    companion object {
        private const val TAG = "DoubaoAiProvider"
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
        if (isDoubaoEndpoint()) {
            analyzeDoubao(prompt, screenshotBase64, uiNodesJson, history, onToken)
        } else {
            analyzeOpenAi(prompt, screenshotBase64, uiNodesJson, history, onToken)
        }
    }

    // ── 豆包 Responses API ──

    private fun analyzeDoubao(
        prompt: String,
        screenshotBase64: String?,
        uiNodesJson: String?,
        history: List<AiProvider.AiMessage>,
        onToken: ((String) -> Unit)?
    ): String {
        val input = buildJsonArray {
            addJsonObject {
                put("role", "system")
                put("content", buildJsonArray {
                    addJsonObject {
                        put("type", "input_text")
                        put("text", prompt)
                    }
                })
            }

            for (msg in history) {
                addJsonObject {
                    put("role", msg.role)
                    put("content", buildJsonArray {
                        addJsonObject {
                            put("type", "input_text")
                            put("text", msg.content)
                        }
                    })
                }
            }

            addJsonObject {
                put("role", "user")
                put("content", buildDoubaoUserContent(screenshotBase64, uiNodesJson))
            }
        }

        val requestBody = buildJsonObject {
            put("model", modelId)
            put("input", input)
            put("max_output_tokens", maxTokens)
            put("stream", true)
            put("temperature", 0.0)
            put("tools", buildJsonArray {
                addJsonObject {
                    put("type", "web_search")
                    put("max_keyword", 3)
                }
            })
        }

        val conn = openConnection("${baseUrl.trimEnd('/')}/responses", requestBody)

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            throw Exception("AI API 错误: ${conn.responseCode} - $err")
        }

        return readDoubaoSse(conn, onToken)
    }

    private fun readDoubaoSse(conn: HttpURLConnection, onToken: ((String) -> Unit)?): String {
        val fullText = StringBuilder()
        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.startsWith("event:")) continue
                if (!l.startsWith("data:")) continue
                val data = l.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue

                try {
                    val parsed = json.decodeFromString<JsonObject>(data)
                    val type = parsed["type"]?.jsonPrimitive?.contentOrNull ?: ""

                    when (type) {
                        "response.output_text.delta" -> {
                            val delta = parsed["delta"]?.jsonPrimitive?.contentOrNull ?: ""
                            if (delta.isNotEmpty()) {
                                fullText.append(delta)
                                onToken?.invoke(delta)
                            }
                        }
                        "response.completed" -> break
                        "error" -> {
                            val errMsg = parsed["message"]?.jsonPrimitive?.contentOrNull
                                ?: parsed.toString()
                            throw Exception("AI error: $errMsg")
                        }
                    }
                } catch (e: Exception) {
                    if (e.message?.startsWith("AI error") == true) throw e
                }
            }
        } finally {
            reader.close()
            conn.disconnect()
        }
        return fullText.toString()
    }

    private fun buildDoubaoUserContent(
        screenshotBase64: String?,
        uiNodesJson: String?
    ): JsonArray {
        return buildJsonArray {
            if (!screenshotBase64.isNullOrEmpty()) {
                addJsonObject {
                    put("type", "input_image")
                    put("image_url", "data:image/jpeg;base64,$screenshotBase64")
                }
            }
            if (!uiNodesJson.isNullOrEmpty()) {
                addJsonObject {
                    put("type", "input_text")
                    put("text", "[UI Node Tree]\n$uiNodesJson")
                }
            }
            if (screenshotBase64.isNullOrEmpty() && uiNodesJson.isNullOrEmpty()) {
                addJsonObject {
                    put("type", "input_text")
                    put("text", "[No screen context available]")
                }
            }
        }
    }

    // ── OpenAI Chat Completions API ──

    private fun analyzeOpenAi(
        prompt: String,
        screenshotBase64: String?,
        uiNodesJson: String?,
        history: List<AiProvider.AiMessage>,
        onToken: ((String) -> Unit)?
    ): String {
        val messages = buildJsonArray {
            addJsonObject {
                put("role", "system")
                put("content", prompt)
            }

            for (msg in history) {
                addJsonObject {
                    put("role", msg.role)
                    put("content", msg.content)
                }
            }

            addJsonObject {
                put("role", "user")
                put("content", buildOpenAiUserContent(screenshotBase64, uiNodesJson))
            }
        }

        val requestBody = buildJsonObject {
            put("model", modelId)
            put("messages", messages)
            put("max_tokens", maxTokens)
            put("stream", true)
            put("temperature", 0.0)
        }

        val conn = openConnection("${baseUrl.trimEnd('/')}/chat/completions", requestBody)

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            throw Exception("AI API 错误: ${conn.responseCode} - $err")
        }

        return readOpenAiSse(conn, onToken)
    }

    private fun readOpenAiSse(conn: HttpURLConnection, onToken: ((String) -> Unit)?): String {
        val fullText = StringBuilder()
        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data:")) continue
                val data = l.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue

                try {
                    val parsed = json.decodeFromString<JsonObject>(data)
                    val choices = parsed["choices"]?.jsonArray ?: continue
                    val choice = choices.firstOrNull()?.jsonObject ?: continue
                    val delta = choice["delta"]?.jsonObject ?: continue
                    val content = delta["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (content.isNotEmpty()) {
                        fullText.append(content)
                        onToken?.invoke(content)
                    }
                    val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
                    if (finishReason == "stop") break
                } catch (e: Exception) {
                    if (e.message?.startsWith("AI error") == true) throw e
                }
            }
        } finally {
            reader.close()
            conn.disconnect()
        }
        return fullText.toString()
    }

    private fun buildOpenAiUserContent(
        screenshotBase64: String?,
        uiNodesJson: String?
    ): JsonElement {
        val parts = mutableListOf<JsonObject>()

        if (!screenshotBase64.isNullOrEmpty()) {
            parts.add(buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject {
                    put("url", "data:image/jpeg;base64,$screenshotBase64")
                })
            })
        }

        if (!uiNodesJson.isNullOrEmpty()) {
            parts.add(buildJsonObject {
                put("type", "text")
                put("text", "[UI Node Tree]\n$uiNodesJson")
            })
        }

        if (parts.isEmpty()) {
            parts.add(buildJsonObject {
                put("type", "text")
                put("text", "[No screen context available]")
            })
        }

        // 纯文本时直接返回字符串，兼容不支持 content 数组的接口
        if (parts.size == 1 && parts[0]["type"]?.jsonPrimitive?.contentOrNull == "text") {
            return parts[0]["text"]!!.jsonPrimitive
        }
        return JsonArray(parts)
    }

    // ── 公共工具方法 ──

    private fun openConnection(endpoint: String, body: JsonObject): HttpURLConnection {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        conn.outputStream.use { out ->
            out.write(body.toString().toByteArray(Charsets.UTF_8))
            out.flush()
        }
        return conn
    }

    /**
     * 判断当前 baseUrl 是否指向豆包/火山引擎 API，
     * 只有豆包端点才走 Responses API + web_search 等专有参数。
     */
    private fun isDoubaoEndpoint(): Boolean {
        val url = baseUrl.lowercase()
        return url.contains("volces.com") || url.contains("volcengine.com")
    }
}
