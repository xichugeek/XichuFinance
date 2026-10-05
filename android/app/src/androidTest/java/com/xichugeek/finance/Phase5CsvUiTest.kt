package com.xichugeek.finance

import android.app.Application
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
import java.util.UUID
import java.io.File
import android.net.Uri

class Phase5CsvUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun previewRequiresConfirmationAndSecondImportIsDuplicate() {
        assertTrue(BuildConfig.DEBUG && BuildConfig.API_BASE_URL == "http://10.0.2.2:8000/")
        val application = ApplicationProvider.getApplicationContext<Application>()
        val api = ApiClient.create()
        lateinit var session: UserSession
        runBlocking {
            val credentials = Credentials("csv-ui-${UUID.randomUUID()}@example.com", UUID.randomUUID().toString())
            val user = api.register(credentials)
            session = UserSession(user.id, user.email, api.login(credentials).token)
            api.addAccount(session.authorization, AccountRequest("虚构 CSV 钱包", "cash"))
            SessionStore(application).save(session)
        }
        val csv = "date,description,amount,type,account,category\n2026-09-01,虚构CSV早餐,15.00,expense,虚构 CSV 钱包,餐饮\n2026-09-02,虚构CSV咖啡,4.00,expense,虚构 CSV 钱包,餐饮\n2026-09-02,虚构CSV咖啡,4.0,expense,虚构 CSV 钱包,餐饮\n2026-09-02,无效金额,-1,expense,虚构 CSV 钱包,餐饮\n".toByteArray()
        val file = File(application.filesDir, "phase5_sample.csv").apply { writeBytes(csv) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(20_000) { compose.onAllNodesWithText("已同步到此设备").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("CSV 账单导入") and hasClickAction()).performScrollTo().performClick()
            scenario.onActivity { ViewModelProvider(it)[FinanceViewModel::class.java].previewCsv(Uri.fromFile(file)) }
            compose.waitUntil(20_000) { compose.onAllNodesWithText("导入预览").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("错误 1 · 重复 1").assertExists()
            runBlocking { assertTrue(api.transactions(session.authorization).isEmpty()) }
            compose.onNode(hasText("确认导入") and hasClickAction()).performScrollTo().performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("导入完成：2 笔，重复跳过 0 笔").fetchSemanticsNodes().isNotEmpty() }
            runBlocking { assertEquals(2, api.transactions(session.authorization).size) }
            lateinit var model: FinanceViewModel
            scenario.onActivity { model = ViewModelProvider(it)[FinanceViewModel::class.java] }
            compose.waitUntil(20_000) { !model.state.value.busy }
            scenario.onActivity { model.previewCsv(Uri.fromFile(file)) }
            compose.waitUntil(20_000) { compose.onAllNodesWithText("错误 1 · 重复 3").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("确认导入") and hasClickAction()).assertIsNotEnabled()
        }
    }
}
