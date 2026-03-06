package com.tutu.meowhub.core.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeowAppUser(
    val id: Int = 0,
    val nickname: String = "",
    val credits: Int = 0,
    val phone: String? = null,
    val email: String? = null
) {
    val displayContact: String
        get() = phone ?: email ?: ""

    val avatarLetter: String
        get() = nickname.firstOrNull()?.uppercase() ?: "M"
}

@Serializable
data class MeowAppBilling(
    @SerialName("credits_per_1k_tokens") val creditsPer1kTokens: Int = 1,
    @SerialName("min_credits_per_request") val minCreditsPerRequest: Int = 1
)

@Serializable
data class MeowAppProfile(
    val success: Boolean = false,
    val user: MeowAppUser? = null,
    val billing: MeowAppBilling? = null,
    @SerialName("recharge_url") val rechargeUrl: String? = null
)

@Serializable
data class MeowAppOrder(
    @SerialName("order_no") val orderNo: String = "",
    val amount: String = "0.00",
    val credits: Int = 0,
    val status: String = "",
    @SerialName("pay_method") val payMethod: String = "",
    @SerialName("trade_no") val tradeNo: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("created_at") val createdAt: String = ""
) {
    val statusText: String
        get() = when (status) {
            "paid" -> "已支付"
            "pending" -> "待支付"
            "failed" -> "已关闭"
            else -> status
        }
}

@Serializable
data class MeowAppOrdersResponse(
    val success: Boolean = false,
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20,
    val orders: List<MeowAppOrder> = emptyList(),
    @SerialName("recharge_url") val rechargeUrl: String? = null
)

@Serializable
data class MeowAppKeyData(
    @SerialName("app_id") val appId: String = "",
    @SerialName("app_secret") val appSecret: String = "",
    @SerialName("app_name") val appName: String = "",
    val permissions: String = "",
    val status: kotlinx.serialization.json.JsonPrimitive? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class MeowAppKeyResponse(
    val success: Boolean = false,
    @SerialName("is_new") val isNew: Boolean = false,
    val data: MeowAppKeyData? = null
)

@Serializable
data class MeowAppCreditsResponse(
    val success: Boolean = false,
    val credits: Int = 0,
    @SerialName("recharge_url") val rechargeUrl: String? = null
)

@Serializable
data class MeowAppLoginResponse(
    val success: Boolean = false,
    val message: String? = null,
    @SerialName("is_new_user") val isNewUser: Boolean = false,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0,
    val user: MeowAppUser? = null
)

@Serializable
data class MeowAppSendCodeResponse(
    val success: Boolean = false,
    val message: String? = null,
    @SerialName("expire_seconds") val expireSeconds: Int = 300,
    @SerialName("retry_after") val retryAfter: Int? = null
)

@Serializable
data class MeowAppRefreshResponse(
    val success: Boolean = false,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0
)
