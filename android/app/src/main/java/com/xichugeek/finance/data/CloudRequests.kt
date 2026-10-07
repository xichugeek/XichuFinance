package com.xichugeek.finance.data

import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

internal class CloudRequestTimeout(message: String) : IOException(message)

internal data class CloudLoginResult(val token: TokenResponse, val user: RemoteUser)

/** One deadline covers the whole sign-in, including DNS/connect retries in Retrofit calls. */
internal suspend fun authenticateCloud(
    api: FinanceApi,
    credentials: Credentials,
    register: Boolean,
    timeoutMillis: Long = if (register) 45_000 else 25_000,
): CloudLoginResult = withTimeoutOrNull(timeoutMillis) {
    if (register) api.register(credentials)
    val token = api.login(credentials)
    val user = api.me("Bearer ${token.token}")
    check(token.token.isNotBlank() && user.id > 0 && user.email.equals(credentials.email, ignoreCase = true)) {
        "登录用户不匹配，请重新登录"
    }
    CloudLoginResult(token, user)
} ?: throw CloudRequestTimeout("登录连接超时，请检查网络后重试")

internal data class CloudSnapshot(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val rules: List<RuleEntity>,
)

/** Read independent endpoints together; cancellation or validation failure returns no partial snapshot. */
internal suspend fun readCloudSnapshot(
    api: FinanceApi,
    authorization: String,
    userId: Long,
    verifiedUser: RemoteUser? = null,
    timeoutMillis: Long = 30_000,
): CloudSnapshot = withTimeoutOrNull(timeoutMillis) {
    val user = verifiedUser ?: api.me(authorization)
    check(user.id == userId) { "登录用户不匹配，请重新登录" }
    val accounts = async { api.accounts(authorization).onEach { check(it.userId == userId) }.map { it.entity() } }
    val categories = async { api.categories(authorization).onEach { check(it.userId == userId) }.map { it.entity() } }
    val transactions = async { api.transactions(authorization).onEach { check(it.userId == userId) }.map { it.entity() } }
    val rules = async { api.rules(authorization).onEach { check(it.userId == userId) }.map { it.entity() } }
    CloudSnapshot(accounts.await(), categories.await(), transactions.await(), rules.await())
} ?: throw CloudRequestTimeout("账本同步超时，请在设置中重试；已缓存的账目会保留")
