package com.auak.agent.core.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuakClawAppUser(
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
data class AuakClawAppBilling(
    @SerialName("credits_per_1k_tokens") val creditsPer1kTokens: Int = 1,
    @SerialName("min_credits_per_request") val minCreditsPerRequest: Int = 1
)

@Serializable
data class AuakClawAppProfile(
    val success: Boolean = false,
    val user: AuakClawAppUser? = null,
    val billing: AuakClawAppBilling? = null,
    @SerialName("recharge_url") val rechargeUrl: String? = null
)

@Serializable
data class AuakClawAppOrder(
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
data class AuakClawAppOrdersResponse(
    val success: Boolean = false,
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20,
    val orders: List<AuakClawAppOrder> = emptyList(),
    @SerialName("recharge_url") val rechargeUrl: String? = null
)

@Serializable
data class AuakClawAppKeyData(
    @SerialName("app_id") val appId: String = "",
    @SerialName("app_secret") val appSecret: String = "",
    @SerialName("app_name") val appName: String = "",
    val permissions: String = "",
    val status: kotlinx.serialization.json.JsonPrimitive? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class AuakClawAppKeyResponse(
    val success: Boolean = false,
    @SerialName("is_new") val isNew: Boolean = false,
    val data: AuakClawAppKeyData? = null
)

@Serializable
data class AuakClawAppCreditsResponse(
    val success: Boolean = false,
    val credits: Int = 0,
    @SerialName("recharge_url") val rechargeUrl: String? = null
)

@Serializable
data class AuakClawAppLoginResponse(
    val success: Boolean = false,
    val message: String? = null,
    @SerialName("is_new_user") val isNewUser: Boolean = false,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0,
    val user: AuakClawAppUser? = null
)

@Serializable
data class AuakClawAppSendCodeResponse(
    val success: Boolean = false,
    val message: String? = null,
    @SerialName("expire_seconds") val expireSeconds: Int = 300,
    @SerialName("retry_after") val retryAfter: Int? = null
)

@Serializable
data class AuakClawAppRefreshResponse(
    val success: Boolean = false,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0
)
