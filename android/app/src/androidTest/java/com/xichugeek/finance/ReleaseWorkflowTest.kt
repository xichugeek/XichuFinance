package com.xichugeek.finance

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
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

class ReleaseWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val list = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)
    private fun waitFor(text: String) = compose.waitUntil(120_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())
    private fun scrollField(label: String): SemanticsNodeInteraction {
        compose.onNode(list).performScrollToNode(hasText(label) and hasSetTextAction())
        return field(label)
    }
    private fun scrollClick(text: String) {
        compose.onNode(list).performScrollToNode(hasText(text) and hasClickAction())
        click(text)
    }

    @Test fun signedReleaseUsesProductionHttpsAndRetainsItsCoreWorkflow() {
        assertFalse(BuildConfig.DEBUG)
        assertEquals("1.0.4", BuildConfig.VERSION_NAME)
        assertEquals("https://finance-api.demo.xichugeek.com/", BuildConfig.API_BASE_URL)
        val application = ApplicationProvider.getApplicationContext<Application>()
        runBlocking { SessionStore(application).clear() }
        val email = "release-${UUID.randomUUID()}@example.com"
        val password = UUID.randomUUID().toString()
        val month = YearMonth.now()
        val file = File(application.filesDir, "release-fictional.csv").apply {
            writeText("date,description,amount,type,account,category\n${month.atDay(1)},虚构 Release 早餐,15.00,expense,虚构 Release 钱包,餐饮\n${month.atDay(2)},虚构 Release 地铁,4.00,expense,虚构 Release 钱包,交通\n")
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: FinanceViewModel
            scenario.onActivity { model = ViewModelProvider(it)[FinanceViewModel::class.java] }
            waitFor("登录云端账本")
            click("还没有账户？注册")
            field("邮箱").performTextInput(email)
            field("密码（8–128 个字符）").performTextInput(password)
            scrollClick("注册并登录")
            compose.waitUntil(75_000) { !model.state.value.busy && (model.state.value.hasLedger || model.error.value != null) }
            assertTrue("Cloud authentication did not activate a ledger: ${model.error.value ?: "no result"}", model.state.value.hasLedger)
            waitFor("已同步到此设备")
            compose.onNodeWithText("云端 · 设置").assertDoesNotExist()
            compose.onNodeWithText("云端设置").assertDoesNotExist()
            click("分类")
            compose.onNode(list).performScrollToNode(hasSetTextAction() and hasText("分类名称"))
            field("分类名称").performTextInput("虚构 Release 分类")
            scrollClick("添加分类")
            compose.waitUntil(60_000) { !model.state.value.busy && model.state.value.categories.any { it.name == "虚构 Release 分类" } }
            val categoryId = model.state.value.categories.single { it.name == "虚构 Release 分类" }.id
            compose.onNode(list).performScrollToNode(hasTestTag("category-edit-$categoryId"))
            compose.onNodeWithTag("category-edit-$categoryId").performClick()
            compose.onNodeWithTag("edit-category-name").performTextReplacement("虚构 Release 改名分类")
            click("保存修改")
            compose.waitUntil(60_000) { !model.state.value.busy && model.state.value.categories.any { it.id == categoryId && it.name == "虚构 Release 改名分类" } }
            compose.onNodeWithTag("category-delete-$categoryId").performClick()
            click("取消")
            assertTrue(model.state.value.categories.any { it.id == categoryId })
            screenshot(application, "release-categories.png")
            compose.onNodeWithTag("category-delete-$categoryId").performClick()
            click("确认删除")
            compose.waitUntil(60_000) { !model.state.value.busy && model.state.value.categories.none { it.id == categoryId } }
            runBlocking { assertTrue(ApiClient.create().categories(requireNotNull(SessionStore(application).load()).authorization).none { it.id == categoryId }) }
            assertTrue(model.state.value.accounts.isEmpty())
            click("交易")
            click("添加")
            field("金额（元）").performTextInput("12.34")
            field("描述").performTextInput("虚构 Release 午餐")
            field("日期 YYYY-MM-DD").performTextReplacement(month.atDay(3).toString())
            compose.onNode(list).performScrollToNode(hasText("保存交易") and hasClickAction())
            compose.onNode(hasText("保存交易") and hasClickAction()).assertIsNotEnabled()
            scrollClick("添加账户")
            compose.onNodeWithText("添加记账账户").assertExists()
            compose.onNode(hasText("取消") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(list).performScrollToNode(hasText("金额（元）") and hasSetTextAction())
            field("金额（元）").assertTextContains("12.34")
            scrollClick("添加账户")
            compose.onNodeWithTag("transaction-account-name").performTextInput("虚构 Release 钱包")
            click("添加并选择")
            compose.waitUntil(60_000) { !model.state.value.busy && model.state.value.accounts.size == 1 }
            compose.onNodeWithText("账户：虚构 Release 钱包").assertExists()
            compose.onNode(list).performScrollToNode(hasText("描述") and hasSetTextAction())
            scrollField("描述").assertTextContains("虚构 Release 午餐")
            scrollField("日期 YYYY-MM-DD").assertTextContains(month.atDay(3).toString())
            screenshot(application, "release-inline-account.png")
            scrollClick("保存交易")
            waitFor("交易记录")
            waitFor("虚构 Release 午餐")
            compose.waitUntil(60_000) { !model.state.value.busy && model.state.value.transactions.size == 1 }
            val expense = model.state.value.transactions.single()
            assertEquals(1234L, expense.amountMinor)
            assertEquals("expense", expense.type)
            assertEquals(model.state.value.accounts.single().id, expense.accountId)
            assertEquals(month.atDay(3), LedgerDates.decode(expense.transactionDate))
            click("虚构 Release 午餐")
            waitFor("交易详情")
            click("编辑")
            field("金额（元）").performTextReplacement("10.50")
            scrollClick("保存交易")
            waitFor("交易详情")
            click("删除交易")
            waitFor("暂无交易。点击“添加”记录第一笔。")
            click("添加")
            click("收入")
            field("金额（元）").performTextInput("1234.56")
            field("描述").performTextInput("虚构 Release 工资")
            field("日期 YYYY-MM-DD").performTextReplacement(month.atDay(4).toString())
            scrollClick("自动分类")
            compose.waitUntil(60_000) { !model.state.value.busy }
            scrollClick("账户：虚构 Release 钱包")
            click("＋ 添加账户")
            compose.onNodeWithTag("transaction-account-name").performTextInput("虚构 Release 工资卡")
            click("账户类型：现金")
            click("银行卡")
            click("添加并选择")
            compose.waitUntil(60_000) { !model.state.value.busy && model.state.value.accounts.size == 2 }
            compose.onNodeWithText("账户：虚构 Release 工资卡").assertExists()
            scrollClick("账户：虚构 Release 工资卡")
            click("虚构 Release 钱包")
            scrollClick("账户：虚构 Release 钱包")
            click("虚构 Release 工资卡")
            val selectedId = model.state.value.accounts.single { it.name == "虚构 Release 工资卡" }.id
            scenario.onActivity { model.addAccount("虚构 Release 备用账户", "other") }
            compose.waitUntil(60_000) { !model.state.value.busy && model.state.value.accounts.size == 3 }
            compose.onNodeWithText("账户：虚构 Release 工资卡").assertExists()
            compose.onNode(list).performScrollToNode(hasText("金额（元）") and hasSetTextAction())
            scrollField("金额（元）").assertTextContains("1234.56")
            scrollField("描述").assertTextContains("虚构 Release 工资")
            scrollField("日期 YYYY-MM-DD").assertTextContains(month.atDay(4).toString())
            scrollClick("保存交易")
            waitFor("虚构 Release 工资")
            compose.waitUntil(60_000) { !model.state.value.busy && model.state.value.transactions.size == 1 }
            val income = model.state.value.transactions.single()
            assertEquals("income", income.type)
            assertEquals(123456L, income.amountMinor)
            assertEquals(selectedId, income.accountId)
            assertEquals("bank", model.state.value.accounts.single { it.id == selectedId }.kind)
            assertEquals("工资", model.state.value.categories.single { it.id == income.categoryId }.name)
            assertEquals(month.atDay(4), LedgerDates.decode(income.transactionDate))
            runBlocking {
                val remote = ApiClient.create().transactions(requireNotNull(SessionStore(application).load()).authorization).single()
                assertEquals("income", remote.type)
                assertEquals("1234.56", remote.amount)
                assertEquals(selectedId, remote.accountId)
            }
            click("虚构 Release 工资")
            click("删除交易")
            waitFor("暂无交易。点击“添加”记录第一笔。")
            click("概览")
            scrollClick("CSV 账单导入")
            compose.waitUntil(60_000) { !model.state.value.busy }
            scenario.onActivity { model.previewCsv(Uri.fromFile(file)) }
            waitFor("导入预览")
            assertTrue(model.state.value.transactions.isEmpty())
            compose.onNode(hasText("确认导入") and hasClickAction()).performScrollTo().performClick()
            waitFor("导入完成：2 笔，重复跳过 0 笔")
            compose.waitUntil(60_000) { !model.state.value.busy && model.state.value.transactions.size == 2 }
            scenario.onActivity { model.previewCsv(Uri.fromFile(file)) }
            waitFor("错误 0 · 重复 2")
            compose.onNode(hasText("确认导入") and hasClickAction()).assertIsNotEnabled()
            scrollClick("返回")
            click("分类")
            val usedCategory = model.state.value.categories.single { it.name == "餐饮" && it.type == "expense" }
            compose.onNode(list).performScrollToNode(hasTestTag("category-delete-${usedCategory.id}"))
            compose.onNodeWithTag("category-delete-${usedCategory.id}").performClick()
            compose.onNodeWithText("暂时无法删除").assertExists()
            compose.onNodeWithText("确认删除").assertDoesNotExist()
            click("知道了")
            assertEquals(2, model.state.value.transactions.size)
            click("概览")
            scrollClick("统计分析")
            compose.onNodeWithText(Money.formatMinor(1900L)).assertExists()
            screenshot(application, "release-analytics.png")
            scrollClick("返回")
            scrollClick("问问我的账单")
            field("输入账单问题").performTextInput("这个月花了多少钱？")
            scrollClick("查询账单")
            compose.waitUntil(60_000) { !model.state.value.busy && model.state.value.askResult != null }
            compose.onNode(list).performScrollToNode(hasText("${month.year}年${month.monthValue}月支出 19.00 元。"))
            compose.onNodeWithText("${month.year}年${month.monthValue}月支出 19.00 元。").assertIsDisplayed()
            assertEquals("database_template", model.state.value.askResult?.source)
            assertEquals(false, model.state.value.askResult?.aiEnabled)
            screenshot(application, "release-ask.png")
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("已同步到此设备")
            lateinit var model: FinanceViewModel
            scenario.onActivity { model = ViewModelProvider(it)[FinanceViewModel::class.java] }
            compose.waitUntil(60_000) { !model.state.value.busy && model.state.value.transactions.size == 2 }
            click("交易")
            waitFor("虚构 Release 早餐")
            waitFor("虚构 Release 地铁")
            click("设置")
            click("退出登录")
            waitFor("登录云端账本")
            field("邮箱").performTextInput(email)
            field("密码（8–128 个字符）").performTextInput(password)
            scrollClick("登录")
            waitFor("已同步到此设备")
            screenshot(application, "release-dashboard.png")
            runBlocking {
                val session = requireNotNull(SessionStore(application).load())
                assertEquals(2, ApiClient.create().transactions(session.authorization).size)
            }
        }
    }

    private fun screenshot(application: Application, name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        File(application.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
