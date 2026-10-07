package com.xichugeek.finance.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.startCoroutine

class CloudRequestsTest {
    private val user = RemoteUser(7, "fictional@example.com")
    private val resources = setOf("accounts", "categories", "transactions", "rules")

    private fun api(handler: suspend (String) -> Any?): FinanceApi = Proxy.newProxyInstance(
        FinanceApi::class.java.classLoader, arrayOf(FinanceApi::class.java),
    ) { _, method, arguments ->
        @Suppress("UNCHECKED_CAST")
        val continuation = arguments.last() as Continuation<Any?>
        val call: suspend () -> Any? = { handler(method.name) }
        call.startCoroutine(continuation)
        COROUTINE_SUSPENDED
    } as FinanceApi

    private fun response(name: String): Any = when (name) {
        "login" -> TokenResponse("fictional-token")
        "me" -> user
        "accounts" -> listOf(RemoteAccount(1, user.id, "虚构钱包", "cash", "123.45"))
        "categories" -> listOf(RemoteCategory(2, user.id, "餐饮", "expense"))
        "transactions" -> listOf(RemoteTransaction(3, user.id, 1, 2, "expense", "10.50", "CNY", "虚构午餐", "2026-10-01", "manual", null, "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z"))
        "rules" -> emptyList<RemoteRule>()
        else -> error("Unexpected API method: $name")
    }

    @Test fun independentReadsStartTogetherAndPreserveExactAmounts() = runBlocking {
        val started = mutableSetOf<String>()
        val allStarted = CompletableDeferred<Unit>()
        val fake = api { name ->
            if (name in resources) {
                started += name
                if (started == resources) allStarted.complete(Unit)
                allStarted.await() // A serial implementation cannot cross this barrier.
            }
            response(name)
        }
        val snapshot = readCloudSnapshot(fake, "Bearer fictional-token", user.id, timeoutMillis = 2_000)
        assertEquals(resources, started)
        assertEquals(12345L, snapshot.accounts.single().openingBalanceMinor)
        assertEquals(1050L, snapshot.transactions.single().amountMinor)
    }

    @Test fun freshLoginChecksIdentityOnceAndReusesItForFirstSync() = runBlocking {
        val calls = mutableListOf<String>()
        val fake = api { name -> calls += name; response(name) }
        val login = authenticateCloud(fake, Credentials(user.email, "fictional-password"), false)
        readCloudSnapshot(fake, "Bearer ${login.token.token}", user.id, login.user)
        assertEquals(1, calls.count { it == "login" })
        assertEquals(1, calls.count { it == "me" })
        assertEquals(resources, calls.drop(2).toSet())
    }

    @Test fun loginDeadlineCancelsStalledRequestAndDoesNotContinueToIdentity() = runBlocking {
        var cancelled = false
        var queriedIdentity = false
        val fake = api { name ->
            if (name == "me") queriedIdentity = true
            try { awaitCancellation() } finally { cancelled = true }
        }
        try {
            authenticateCloud(fake, Credentials(user.email, "fictional-password"), false, timeoutMillis = 100)
            fail("Stalled login was accepted")
        } catch (error: CloudRequestTimeout) {
            assertTrue(error.message!!.contains("登录连接超时"))
        }
        assertTrue(cancelled)
        assertFalse(queriedIdentity)
    }

    @Test fun callerCancellationPropagatesInsteadOfBecomingLoginTimeout() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var mappedToTimeout = false
        val fake = api { started.complete(Unit); awaitCancellation() }
        val work = launch {
            try { authenticateCloud(fake, Credentials(user.email, "fictional-password"), false) }
            catch (_: CloudRequestTimeout) { mappedToTimeout = true }
        }
        started.await(); work.cancelAndJoin()
        assertTrue(work.isCancelled)
        assertFalse(mappedToTimeout)
    }

    @Test fun failedReadCancelsItsSiblingsAndReturnsNoSnapshot() = runBlocking {
        val accountStarted = CompletableDeferred<Unit>()
        var accountCancelled = false
        val fake = api { name ->
            when (name) {
                "accounts" -> try { accountStarted.complete(Unit); awaitCancellation() } finally { accountCancelled = true }
                "transactions" -> { accountStarted.await(); throw IOException("fictional network failure") }
                else -> response(name)
            }
        }
        try {
            readCloudSnapshot(fake, "Bearer fictional-token", user.id, timeoutMillis = 2_000)
            fail("Incomplete snapshot was accepted")
        } catch (_: IOException) { }
        assertTrue(accountCancelled)
    }

    @Test fun foreignUserResponseIsRejectedBeforeReturningSnapshot() = runBlocking {
        val fake = api { name ->
            if (name == "categories") listOf(RemoteCategory(2, 99, "其他用户分类", "expense")) else response(name)
        }
        try {
            readCloudSnapshot(fake, "Bearer fictional-token", user.id)
            fail("Foreign records were accepted")
        } catch (_: IllegalStateException) { }
    }

    @Test fun wholeSyncDeadlineCancelsSlowReads() = runBlocking {
        var cancelled = false
        val fake = api { name ->
            if (name == "rules") try { awaitCancellation() } finally { cancelled = true } else response(name)
        }
        try {
            readCloudSnapshot(fake, "Bearer fictional-token", user.id, timeoutMillis = 100)
            fail("Stalled sync was accepted")
        } catch (error: CloudRequestTimeout) { assertTrue(error.message!!.contains("同步超时")) }
        assertTrue(cancelled)
    }
}
