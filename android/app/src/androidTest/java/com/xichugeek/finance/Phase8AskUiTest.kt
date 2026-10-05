package com.xichugeek.finance

import android.app.Application
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.xichugeek.finance.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.YearMonth
import java.util.UUID

class Phase8AskUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun asksRealDatabaseAndDisplaysExactTemplateAnswer() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val api = ApiClient.create()
        val month = YearMonth.now()
        runBlocking {
            val credentials = Credentials("ask-ui-${UUID.randomUUID()}@example.com", UUID.randomUUID().toString())
            val user = api.register(credentials)
            val session = UserSession(user.id, user.email, api.login(credentials).token)
            val account = api.addAccount(session.authorization, AccountRequest("虚构问答钱包", "cash"))
            val food = api.categories(session.authorization).first { it.name == "餐饮" }
            for (amount in listOf("0.01", "0.10", "0.20")) {
                api.addTransaction(session.authorization, TransactionRequest(account.id, food.id, "expense", amount,
                    description = "虚构问答午餐", transactionDate = month.atDay(1).toString()))
            }
            SessionStore(application).save(session)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: FinanceViewModel
            scenario.onActivity { model = ViewModelProvider(it)[FinanceViewModel::class.java] }
            compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.transactions.size == 3 }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("问问我的账单") and hasClickAction())
            compose.onNode(hasText("问问我的账单") and hasClickAction()).performClick()
            compose.onNodeWithText("AI Enhancement Disabled").assertIsDisplayed()
            compose.onNodeWithText("输入账单问题").performTextInput("这个月花了多少钱？")
            compose.onNodeWithText("查询账单").performClick()
            compose.waitUntil(20_000) { model.state.value.askResult != null && !model.state.value.busy }
            val answer = "${month.year}年${month.monthValue}月支出 0.31 元。"
            compose.onNodeWithText(answer).assertIsDisplayed()
            assertEquals("database_template", model.state.value.askResult?.source)
            assertEquals(false, model.state.value.askResult?.aiEnabled)
            File(application.getExternalFilesDir(null), "phase8_ask.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
