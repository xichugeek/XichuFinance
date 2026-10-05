package com.xichugeek.finance

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xichugeek.finance.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import java.io.IOException
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Writes fictional data only to the running local Docker API from an actual Android device. */
@RunWith(AndroidJUnit4::class)
class Phase4IntegrationTest {
    @Test
    fun realApiCrudSecureSessionAndIsolatedOfflineCache(): Unit = runBlocking {
        assertTrue("This test must use the local debug API", BuildConfig.DEBUG && BuildConfig.API_BASE_URL == "http://10.0.2.2:8000/")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val api = ApiClient.create()
        suspend fun register(label: String): UserSession {
            val random = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val credentials = Credentials("android-$label-${UUID.randomUUID()}@example.com", random)
            val registered = api.register(credentials)
            val login = api.login(credentials)
            val current = api.me("Bearer ${login.token}")
            assertEquals(registered.id, current.id)
            try {
                api.login(credentials.copy(password = "wrong-password"))
                fail("Wrong password accepted")
            } catch (error: HttpException) { assertEquals(401, error.code()) }
            return UserSession(current.id, current.email, login.token)
        }

        val userA = register("a")
        val userB = register("b")
        val dbA = FinanceDatabase.forUser(context, BuildConfig.API_BASE_URL, userA.id)
        val dbB = FinanceDatabase.forUser(context, BuildConfig.API_BASE_URL, userB.id)
        val repoA = FinanceRepository(dbA, api, userA)
        val repoB = FinanceRepository(dbB, api, userB)
        repoA.refresh(); repoB.refresh()
        assertTrue(repoA.transactions.first().isEmpty())
        assertTrue(repoB.accounts.first().isEmpty())
        assertEquals(14, repoA.categories.first().size)

        repoA.addAccount("虚构云端钱包", "cash")
        var account = repoA.accounts.first().single()
        repoA.updateAccount(account.copy(name = "虚构测试钱包"))
        account = repoA.accounts.first().single()
        assertEquals("虚构测试钱包", account.name)
        repoA.addCategory("虚构测试分类", "expense")
        val category = repoA.categories.first().first { it.name == "餐饮" }
        val item = TransactionEntity(accountId = account.id, categoryId = category.id, type = "expense",
            amountMinor = 1234, description = "虚构云端午餐",
            transactionDate = LedgerDates.encode(LocalDate.now()))
        repoA.saveTransaction(item)
        val transaction = repoA.transactions.first().single()
        assertEquals(1234L, transaction.amountMinor)
        repoA.saveTransaction(transaction.copy(amountMinor = 1050))
        assertEquals("10.50", api.transactions(userA.authorization).single().amount)
        repoA.refresh()
        assertEquals(1050L, repoA.transactions.first().single().amountMinor)
        assertTrue(repoB.transactions.first().isEmpty())
        assertTrue(api.transactions(userB.authorization).isEmpty())
        try {
            api.deleteTransaction(userB.authorization, transaction.id)
            fail("Cross-user delete accepted")
        } catch (error: HttpException) { assertEquals(404, error.code()) }

        // A real refused network connection must preserve all previously cached data.
        val offline = FinanceRepository(dbA, ApiClient.create("http://127.0.0.1:1/"), userA)
        try { offline.refresh(); fail("Offline request unexpectedly succeeded") }
        catch (_: IOException) { }
        assertEquals(1050L, offline.transactions.first().single().amountMinor)
        assertEquals("虚构测试钱包", offline.accounts.first().single().name)

        repoA.deleteTransaction(transaction.id)
        assertTrue(api.transactions(userA.authorization).isEmpty())
        repoA.deleteAccount(account.id)
        assertTrue(api.accounts(userA.authorization).isEmpty())
        // Retain one fictional record for subsequent launch/offline screen verification.
        repoA.addAccount("虚构验证钱包", "cash")
        repoA.saveTransaction(item.copy(accountId = repoA.accounts.first().single().id, amountMinor = 1050))
        repoA.refresh()
        val readOnly = FinanceRepository(dbA)
        assertEquals(1050L, readOnly.transactions.first().single().amountMinor)
        assertTrue(repoB.transactions.first().isEmpty())

        val store = SessionStore(context)
        store.save(userA)
        val restored = store.load()!!
        assertEquals(userA.id, restored.id)
        assertTrue(userA.token == restored.token) // Avoid including token values in assertion failures.
        val bytes = java.io.File(context.filesDir, "datastore/secure_session.preferences_pb").readBytes()
        val persisted = bytes.toString(Charsets.ISO_8859_1)
        assertFalse(persisted.contains(userA.token))
        assertFalse(persisted.contains(userA.email))
        store.clear()
        assertNull(store.load())
        store.save(userA)
    }
}
