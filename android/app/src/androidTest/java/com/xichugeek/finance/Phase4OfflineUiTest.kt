package com.xichugeek.finance

import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.xichugeek.finance.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class Phase4OfflineUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun startupWithRefusedNetworkStillShowsCachedLedger() {
        assertTrue(BuildConfig.DEBUG && BuildConfig.API_BASE_URL == "http://10.0.2.2:8000/")
        val application = ApplicationProvider.getApplicationContext<Application>()
        runBlocking {
            val api = ApiClient.create()
            val credentials = Credentials("offline-${UUID.randomUUID()}@example.com", UUID.randomUUID().toString())
            val user = api.register(credentials)
            val token = api.login(credentials).token
            val session = UserSession(user.id, user.email, token)
            val repo = FinanceRepository(FinanceDatabase.forUser(application, BuildConfig.API_BASE_URL, user.id), api, session)
            repo.refresh()
            repo.addAccount("虚构离线钱包", "cash")
            repo.saveTransaction(TransactionEntity(
                accountId = repo.accounts.first().single().id,
                categoryId = repo.categories.first().first { it.name == "餐饮" }.id,
                type = "expense", amountMinor = 1050, description = "虚构离线午餐",
                transactionDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            ))
            SessionStore(application).save(session)
        }
        val modelStore = ViewModelStore()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val factory = object : ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FinanceViewModel(application, ApiClient.create("http://127.0.0.1:1/")) as T
                    }
                }
                val model = ViewModelProvider(modelStore, factory)[FinanceViewModel::class.java]
                activity.setContent { FinanceApp(model) }
            }
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("连接失败，当前显示已缓存数据").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("虚构离线午餐").assertIsDisplayed()
            compose.onNodeWithText("本月支出").assertIsDisplayed()
            val screenshot = File(application.getExternalFilesDir(null), "phase4_offline.png")
            screenshot.outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            scenario.onActivity { modelStore.clear() }
        }
    }
}
