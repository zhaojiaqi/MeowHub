package com.tutu.meowhub.core.engine

import com.tutu.meowhub.core.auth.MeowAppAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 通过 MeowApp 用户 access_token 调用 tutuai.me AI 接口。
 * 使用 Responses API 格式，走用户积分通道。
 */
class MeowAppAiProvider(
    private val authManager: MeowAppAuthManager,
    private val modelId: String = "doubao-seed-2-0-lite-260215",
    private val maxTokens: Int = 30000
) : AiProvider {

    companion object {
        private const val AI_RESPONSES_URL = "https://tutuai.me/api/meowapp/ai/responses.php"
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
        val accessToken = authManager.getAccessToken()
            ?: throw AiNotAuthenticatedException("未登录，无法使用 AI")

        val input = buildJsonArray {
            addJsonObject {
                put("type", "message")
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
                    put("type", "message")
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
                put("type", "message")
                put("role", "user")
                put("content", buildUserContent(screenshotBase64, uiNodesJson))
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

        val conn = (URL(AI_RESPONSES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        conn.outputStream.use { out ->
            out.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            out.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode == 401) {
            conn.disconnect()
            authManager.logout()
            throw AiNotAuthenticatedException("Token 已过期，请重新登录")
        }
        if (responseCode == 402) {
            val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            throw AiInsufficientCreditsException("积分不足，请充值", errBody)
        }
        if (responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            throw Exception("AI API 错误: $responseCode - $err")
        }

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
                        "response.completed" -> {
                            break
                        }
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

        fullText.toString()
    }

    private fun buildUserContent(
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
}

class AiNotAuthenticatedException(message: String) : Exception(message)
class AiInsufficientCreditsException(message: String, val rawResponse: String = "") : Exception(message)
