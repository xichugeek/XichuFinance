package com.xichugeek.finance

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xichugeek.finance.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy
import java.net.SocketTimeoutException

class Phase9ErrorHandlingTest {
    @Test fun failedRefreshKeepsCacheAndAlwaysResetsBusyState() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val cases = listOf(
            HttpException(Response.error<Any>(400, "{}".toResponseBody())) to "请求无效",
            HttpException(Response.error<Any>(401, "{}".toResponseBody())) to "登录已过期",
            HttpException(Response.error<Any>(500, "{}".toResponseBody())) to "服务暂时不可用",
            SerializationException("fictional malformed JSON") to "服务返回格式异常",
            SocketTimeoutException("fictional timeout") to "连接失败",
            IOException("fictional offline") to "连接失败",
        )
        for ((index, item) in cases.withIndex()) {
            val (failure, expected) = item
            val id = System.currentTimeMillis() * 10 + index
            val session = UserSession(id, "error@example.com", "fictional-test-token")
            val database = FinanceDatabase.forUser(application, BuildConfig.API_BASE_URL, id)
            val dao = database.dao()
            dao.cacheAccount(AccountEntity(1, "虚构保留钱包", "cash"))
            dao.cacheCategory(CategoryEntity(2, "餐饮", "expense"))
            dao.cacheTransaction(TransactionEntity(3, 1, 2, "expense", 31, description = "虚构保留交易", transactionDate = LedgerDates.encode(java.time.LocalDate.now())))
            SessionStore(application).save(session)
            val api = Proxy.newProxyInstance(FinanceApi::class.java.classLoader, arrayOf(FinanceApi::class.java)) { _, method, arguments ->
                when (method.name) {
                    "me" -> RemoteUser(id, session.email)
                    "accounts" -> listOf(RemoteAccount(1, id, "应被丢弃的不完整快照", "cash", "0.00"))
                    "categories" -> listOf(RemoteCategory(2, id, "餐饮", "expense"))
                    "transactions" -> {
                        // Suspend APIs report checked IO errors through their continuation;
                        // throwing them from a Java proxy would wrap them in another exception.
                        @Suppress("UNCHECKED_CAST")
                        val continuation = arguments.last() as kotlin.coroutines.Continuation<Any?>
                        android.os.Handler(android.os.Looper.getMainLooper()).post { continuation.resumeWith(Result.failure(failure)) }
                        kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                    }
                    "rules" -> emptyList<RemoteRule>()
                    else -> error("Unexpected API method")
                }
            } as FinanceApi
            lateinit var model: FinanceViewModel
            val store = ViewModelStore()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model = FinanceViewModel(application, api); store.put("test", model) }
            try {
                val message = withTimeout(15_000) { model.error.first { it != null } }
                assertTrue("case $index expected $expected, received $message", message!!.contains(expected))
                val state = withTimeout(15_000) { model.state.first { !it.loading && !it.busy && it.transactions.isNotEmpty() } }
                assertEquals("虚构保留钱包", state.accounts.single().name)
                assertEquals(31L, state.transactions.single().amountMinor)
                assertEquals(failure is HttpException && failure.code() == 401, state.loginExpired)
                assertEquals("虚构保留钱包", dao.observeAccounts().first().single().name)
            } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() } }
        }
        SessionStore(application).clear()
    }
}
