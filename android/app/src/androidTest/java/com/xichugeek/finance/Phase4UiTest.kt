package com.xichugeek.finance

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.xichugeek.finance.data.SessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class Phase4UiTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())

    @Test
    fun registerAccountTransactionEditDeleteLogoutLogin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { SessionStore(context).clear() }
        val email = "ui-${UUID.randomUUID()}@example.com"
        val password = UUID.randomUUID().toString()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("登录云端账本")
            click("还没有账户？注册")
            field("邮箱").performTextInput(email)
            field("密码（8–128 个字符）").performTextInput(password)
            click("注册并登录")
            waitFor("已同步到此设备")
            click("账户")
            field("自定义账户名称").performTextInput("虚构 UI 钱包")
            click("添加账户")
            waitFor("虚构 UI 钱包")
            click("交易")
            click("添加")
            field("金额（元）").performTextInput("12.34")
            field("描述").performTextInput("虚构 UI 午餐")
            compose.onNode(hasText("保存交易") and hasClickAction()).performScrollTo().performClick()
            waitFor("虚构 UI 午餐")
            click("虚构 UI 午餐")
            click("编辑")
            field("金额（元）").performTextReplacement("10.50")
            compose.onNode(hasText("保存交易") and hasClickAction()).performScrollTo().performClick()
            waitFor("交易详情")
            click("删除交易")
            waitFor("暂无交易。点击“添加”记录第一笔。")
            click("设置")
            click("退出登录")
            waitFor("登录云端账本")
            field("邮箱").performTextInput(email)
            field("密码（8–128 个字符）").performTextInput(password)
            click("登录")
            waitFor("已同步到此设备")
            click("交易")
            click("添加")
            field("金额（元）").performTextInput("10.50")
            field("描述").performTextInput("虚构离线验证午餐")
            compose.onNode(hasText("保存交易") and hasClickAction()).performScrollTo().performClick()
            waitFor("虚构离线验证午餐")
        }
    }
}
