package com.tutu.meowhub.core.network

import com.tutu.meowhub.core.model.MeowSkillDetail
import com.tutu.meowhub.core.model.MeowSkillDetailResponse
import com.tutu.meowhub.core.model.MeowSkillListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

class MeowHubApiClient {

    companion object {
        private const val BASE_URL = "https://tutuai.me"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchSkillList(
        category: String = "",
        sort: String = "featured",
        page: Int = 1,
        limit: Int = 12
    ): Result<MeowSkillListResponse> = withContext(Dispatchers.IO) {
        try {
            val queryParams = buildString {
                append("?sort=$sort&limit=$limit&page=$page")
                if (category.isNotEmpty()) append("&category=$category")
            }
            val url = URL("$BASE_URL/api/miaohub/list.php$queryParams")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = conn.responseCode
            val body = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            }
            conn.disconnect()

            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP $responseCode: $body"))
            }

            val response = json.decodeFromString<MeowSkillListResponse>(body)
            if (response.success) {
                Result.success(response)
            } else {
                Result.failure(Exception(response.message ?: "请求失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSkillDetail(slug: String): Result<MeowSkillDetail> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$BASE_URL/api/miaohub/detail.php?slug=${java.net.URLEncoder.encode(slug, "UTF-8")}")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                }

                val responseCode = conn.responseCode
                val body = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                }
                conn.disconnect()

                if (responseCode !in 200..299) {
                    return@withContext Result.failure(Exception("HTTP $responseCode: $body"))
                }

                val response = json.decodeFromString<MeowSkillDetailResponse>(body)
                if (response.success && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception(response.message ?: "获取 Skill 详情失败"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
