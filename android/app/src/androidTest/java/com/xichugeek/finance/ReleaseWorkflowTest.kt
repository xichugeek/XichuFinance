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
    private fun scrollClick(text: String) {
        compose.onNode(list).performScrollToNode(hasText(text) and hasClickAction())
        click(text)
    }

    @Test fun signedReleaseUsesProductionHttpsAndRetainsItsCoreWorkflow() {
        assertFalse(BuildConfig.DEBUG)
        assertEquals("1.0.1", BuildConfig.VERSION_NAME)
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
            click("账户")
            field("自定义账户名称").performTextInput("虚构 Release 钱包")
            scrollClick("添加账户")
            compose.waitUntil(60_000) {
                !model.state.value.busy && model.state.value.accounts.any { it.name == "虚构 Release 钱包" }
            }
            click("交易")
            click("添加")
            field("金额（元）").performTextInput("12.34")
            field("描述").performTextInput("虚构 Release 午餐")
            scrollClick("保存交易")
            waitFor("交易记录")
            waitFor("虚构 Release 午餐")
            click("虚构 Release 午餐")
            waitFor("交易详情")
            click("编辑")
            field("金额（元）").performTextReplacement("10.50")
            scrollClick("保存交易")
            waitFor("交易详情")
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
