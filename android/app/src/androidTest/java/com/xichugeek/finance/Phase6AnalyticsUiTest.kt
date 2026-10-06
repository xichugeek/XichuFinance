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
import java.math.BigInteger
import java.time.YearMonth
import java.util.UUID

class Phase6AnalyticsUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun cachedMoneyMatchesDatabaseAndChartsSwitchMonths() {
        assertTrue(BuildConfig.DEBUG && BuildConfig.API_BASE_URL == "http://10.0.2.2:8000/")
        val application = ApplicationProvider.getApplicationContext<Application>()
        val api = ApiClient.create()
        val month = YearMonth.now()
        lateinit var remote: AnalyticsSummary
        runBlocking {
            val credentials = Credentials("analytics-ui-${UUID.randomUUID()}@example.com", UUID.randomUUID().toString())
            val user = api.register(credentials)
            val session = UserSession(user.id, user.email, api.login(credentials).token)
            val account = api.addAccount(session.authorization, AccountRequest("虚构统计钱包", "cash"))
            val categories = api.categories(session.authorization).associateBy { it.name to it.type }
            suspend fun transaction(amount: String, description: String, category: String, day: Int, type: String = "expense", selected: YearMonth = month) {
                api.addTransaction(session.authorization, TransactionRequest(account.id, categories.getValue(category to type).id,
                    type, amount, description = description, transactionDate = selected.atDay(day).toString()))
            }
            transaction("0.01", "虚构精度一", "餐饮", 1)
            transaction("0.10", "虚构精度二", "餐饮", 1)
            transaction("0.20", "虚构精度三", "交通", 2)
            transaction("15.00", "虚构早餐", "餐饮", 1)
            transaction("4.00", "虚构地铁", "交通", 2)
            transaction("28.00", "虚构咖啡", "餐饮", 3)
            transaction("6000.00", "虚构工资", "工资", 1, "income")
            transaction("25.00", "虚构上月支出", "交通", 1, selected = month.minusMonths(1))
            remote = api.summary(session.authorization, month.atDay(1).toString())
            assertEquals("47.31", remote.expense)
            assertEquals("5952.69", remote.balance)
            SessionStore(application).save(session)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: FinanceViewModel
            scenario.onActivity { model = ViewModelProvider(it)[FinanceViewModel::class.java] }
            compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.transactions.size == 8 }
            val state = model.state.value
            val local = FinanceAnalytics.calculate(month, state.transactions, state.categories)
            fun cents(value: String) = value.toBigDecimal().movePointRight(2).toBigIntegerExact()
            assertEquals(cents(remote.income), local.summary.incomeMinor)
            assertEquals(cents(remote.expense), local.summary.expenseMinor)
            assertEquals(cents(remote.balance), local.summary.balanceMinor)
            assertEquals(cents(remote.previousExpense), local.previous.expenseMinor)
            assertEquals(listOf(BigInteger("1511"), BigInteger("420"), BigInteger("2800")), local.daily.take(3))
            assertEquals(listOf(BigInteger("4311"), BigInteger("420")), local.categories.map { it.amountMinor })
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("统计分析") and hasClickAction())
            compose.onNode(hasText("统计分析") and hasClickAction()).performClick()
            compose.onNodeWithText("${month.year} 年 ${month.monthValue} 月").assertIsDisplayed()
            compose.onNodeWithText(Money.formatMinor(local.summary.expenseMinor)).assertExists()
            screenshot(application, "phase6_analytics.png")
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("支出分类占比"))
            compose.onNodeWithContentDescription("支出分类环形图").performScrollTo().assertIsDisplayed()
            screenshot(application, "phase6_charts.png")
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("最大五笔支出"))
            compose.onNodeWithText("最大五笔支出").assertIsDisplayed()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("上个月"))
            compose.onNodeWithText("上个月").performClick()
            val previous = month.minusMonths(1)
            compose.onNodeWithText("${previous.year} 年 ${previous.monthValue} 月").assertIsDisplayed()
            compose.onNodeWithText(Money.formatMinor(2500L)).assertExists()
            compose.onNodeWithText("下个月").performClick()
            compose.onNodeWithText("${month.year} 年 ${month.monthValue} 月").assertIsDisplayed()
        }
    }

    private fun screenshot(application: Application, name: String) {
        File(application.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
