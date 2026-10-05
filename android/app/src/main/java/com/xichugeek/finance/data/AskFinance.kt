package com.xichugeek.finance.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

@Serializable
data class AskRequest(val question: String, val month: String)

@Serializable
data class AskResult(
    val question: String, val month: String, val intent: String,
    val answer: String, val data: JsonObject, val source: String,
    @SerialName("ai_enabled") val aiEnabled: Boolean,
    @SerialName("ai_status") val aiStatus: String,
)
