package com.xichugeek.finance.data

import com.xichugeek.finance.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@Serializable
data class Credentials(val email: String, val password: String) {
    override fun toString() = "Credentials(email=$email, password=[redacted])"
}

@Serializable
data class TokenResponse(@SerialName("access_token") val token: String) {
    override fun toString() = "TokenResponse([redacted])"
}

@Serializable
data class RemoteUser(val id: Long, val email: String)

@Serializable
data class AccountRequest(
    val name: String,
    val kind: String,
    @SerialName("opening_balance") val openingBalance: String = "0.00",
)

@Serializable
data class RemoteAccount(
    val id: Long,
    @SerialName("user_id") val userId: Long,
    val name: String,
    val kind: String,
    @SerialName("opening_balance") val openingBalance: String,
) {
    fun entity() = AccountEntity(id, name, kind, BigDecimal(openingBalance).movePointRight(2).longValueExact())
}

@Serializable
data class CategoryRequest(val name: String, val type: String)

@Serializable
data class RemoteCategory(val id: Long, @SerialName("user_id") val userId: Long, val name: String, val type: String) {
    fun entity() = CategoryEntity(id, name, type)
}

@Serializable
data class TransactionRequest(
    @SerialName("account_id") val accountId: Long,
    @SerialName("category_id") val categoryId: Long,
    val type: String,
    val amount: String,
    val currency: String = "CNY",
    val description: String,
    @SerialName("transaction_date") val transactionDate: String,
)

@Serializable
data class CsvPreviewRow(
    @SerialName("row_number") val rowNumber: Int,
    val status: String, val message: String, val description: String,
    val amount: String, val type: String, val account: String, val category: String,
)

@Serializable
data class CsvPreview(
    @SerialName("preview_token") val token: String,
    @SerialName("total_rows") val totalRows: Int,
    @SerialName("valid_rows") val validRows: Int,
    @SerialName("error_rows") val errorRows: Int,
    @SerialName("duplicate_rows") val duplicateRows: Int,
    val rows: List<CsvPreviewRow>,
) { override fun toString() = "CsvPreview(total=$totalRows, token=[redacted])" }

@Serializable
data class CsvCommitRequest(@SerialName("preview_token") val token: String) {
    override fun toString() = "CsvCommitRequest([redacted])"
}

@Serializable
data class CsvCommitResult(val imported: Int, val duplicates: Int)

@Serializable
data class AnalyticsSummary(
    val month: String, val income: String, val expense: String, val balance: String,
    @SerialName("previous_month_expense") val previousExpense: String,
    @SerialName("expense_change") val expenseChange: String,
)

@Serializable
data class RemoteTransaction(
    val id: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("account_id") val accountId: Long,
    @SerialName("category_id") val categoryId: Long,
    val type: String,
    val amount: String,
    val currency: String,
    val description: String,
    @SerialName("transaction_date") val transactionDate: String,
    val source: String,
    @SerialName("external_id") val externalId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    fun entity(): TransactionEntity {
        require(currency == "CNY") { "暂不支持此币种" }
        return TransactionEntity(
            id = id, accountId = accountId, categoryId = categoryId, type = type,
            amountMinor = Money.parseMinor(amount), currency = currency, description = description,
            transactionDate = LocalDate.parse(transactionDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            source = source, externalId = externalId,
            createdAt = Instant.parse(createdAt).toEpochMilli(), updatedAt = Instant.parse(updatedAt).toEpochMilli(),
        )
    }
}

interface FinanceApi {
    @POST("auth/register") suspend fun register(@Body credentials: Credentials): RemoteUser
    @POST("auth/login") suspend fun login(@Body credentials: Credentials): TokenResponse
    @GET("me") suspend fun me(@Header("Authorization") authorization: String): RemoteUser
    @GET("accounts") suspend fun accounts(@Header("Authorization") authorization: String): List<RemoteAccount>
    @POST("accounts") suspend fun addAccount(@Header("Authorization") authorization: String, @Body item: AccountRequest): RemoteAccount
    @PUT("accounts/{id}") suspend fun updateAccount(@Header("Authorization") authorization: String, @Path("id") id: Long, @Body item: AccountRequest): RemoteAccount
    @DELETE("accounts/{id}") suspend fun deleteAccount(@Header("Authorization") authorization: String, @Path("id") id: Long)
    @GET("categories") suspend fun categories(@Header("Authorization") authorization: String): List<RemoteCategory>
    @POST("categories") suspend fun addCategory(@Header("Authorization") authorization: String, @Body item: CategoryRequest): RemoteCategory
    @GET("transactions") suspend fun transactions(@Header("Authorization") authorization: String): List<RemoteTransaction>
    @POST("transactions") suspend fun addTransaction(@Header("Authorization") authorization: String, @Body item: TransactionRequest): RemoteTransaction
    @PUT("transactions/{id}") suspend fun updateTransaction(@Header("Authorization") authorization: String, @Path("id") id: Long, @Body item: TransactionRequest): RemoteTransaction
    @DELETE("transactions/{id}") suspend fun deleteTransaction(@Header("Authorization") authorization: String, @Path("id") id: Long)
    @Multipart @POST("imports/csv/preview")
    suspend fun previewCsv(@Header("Authorization") authorization: String, @Part file: MultipartBody.Part): CsvPreview
    @POST("imports/csv/commit")
    suspend fun commitCsv(@Header("Authorization") authorization: String, @Body request: CsvCommitRequest): CsvCommitResult
    @GET("analytics/summary")
    suspend fun summary(@Header("Authorization") authorization: String, @Query("month") month: String): AnalyticsSummary
}

object ApiClient {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun create(baseUrl: String = BuildConfig.API_BASE_URL): FinanceApi {
        require(BuildConfig.DEBUG || baseUrl.startsWith("https://")) { "Release API 必须使用 HTTPS" }
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(FinanceApi::class.java)
    }
}
