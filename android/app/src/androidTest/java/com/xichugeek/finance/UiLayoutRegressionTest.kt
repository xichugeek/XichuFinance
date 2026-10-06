package com.xichugeek.finance

import android.app.Application
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelProvider
import com.xichugeek.finance.data.SessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Runs against the real Activity and local fixture ledger, also at 320 dp / 1.3 font scale. */
class UiLayoutRegressionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val list = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)
    private fun click(label: String) = compose.onNode(hasText(label) and hasClickAction()).performClick()
    private fun scroll(label: String) = compose.onNode(list).performScrollToNode(hasText(label))

    @Test fun localTransactionCanBeSavedEditedAndDeletedWithKeyboardInsets() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        runBlocking { SessionStore(application).useLocalMode() }
        val description = "虚构布局验证-${UUID.randomUUID().toString().take(8)}"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: FinanceViewModel
            scenario.onActivity { model = ViewModelProvider(it)[FinanceViewModel::class.java] }
            compose.waitUntil(30_000) { !model.state.value.loading && model.state.value.accounts.isNotEmpty() }
            val originalCount = model.state.value.transactions.size
            click("交易"); click("添加")
            compose.onNode(hasText("金额（元）") and hasSetTextAction()).performTextInput("12.34")
            compose.onNode(hasText("描述") and hasSetTextAction()).performTextInput(description)
            compose.onNode(list).performScrollToNode(hasText("保存交易") and hasClickAction())
            click("保存交易")
            compose.waitUntil(30_000) { !model.state.value.busy && model.state.value.transactions.any { it.description == description } }
            compose.onNodeWithText("交易记录").assertIsDisplayed()
            val created = model.state.value.transactions.single { it.description == description }
            assertEquals(1234L, created.amountMinor)
            assertEquals(originalCount + 1, model.state.value.transactions.size)
            scroll(description); click(description); click("编辑")
            compose.onNode(hasText("金额（元）") and hasSetTextAction()).performTextReplacement("10.50")
            compose.onNode(list).performScrollToNode(hasText("保存交易") and hasClickAction())
            click("保存交易")
            compose.waitUntil(30_000) { !model.state.value.busy && model.state.value.transactions.any { it.id == created.id && it.amountMinor == 1050L } }
            compose.onNodeWithText("交易详情").assertIsDisplayed()
            click("删除交易")
            compose.waitUntil(30_000) { !model.state.value.busy && model.state.value.transactions.none { it.id == created.id } }
            assertEquals(originalCount, model.state.value.transactions.size)
            compose.onNodeWithText("交易记录").assertIsDisplayed()
        }
    }

    @Test fun navigationLabelsAndScrollableContentStaySeparate() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        runBlocking { SessionStore(application).useLocalMode() }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(30_000) { compose.onAllNodes(tab).fetchSemanticsNodes().size == 5 }
            val density = application.resources.displayMetrics.density
            val labels = listOf("概览", "交易", "账户", "分类", "设置")
            val rects = labels.map { label ->
                val node = compose.onNode(tab and hasText(label))
                node.assertIsDisplayed()
                val bounds = node.fetchSemanticsNode().boundsInRoot
                assertTrue("Tab must have a 48 dp touch target", bounds.height >= 48 * density && bounds.width >= 48 * density)
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNode(hasText(label) and hasAnyAncestor(tab), useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(1, layouts.single().lineCount)
                val result = layouts.single()
                assertFalse("Navigation label $label must not clip or wrap: size=${result.size}, paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}, widthOverflow=${result.didOverflowWidth}, heightOverflow=${result.didOverflowHeight}", result.hasVisualOverflow)
                bounds
            }
            rects.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
            // The action remains above navigation after scrolling, even on a short viewport.
            scroll("添加一笔交易")
            val action = compose.onNode(hasText("添加一笔交易") and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertTrue("Bottom navigation must not cover content", action.bottom <= rects.first().top)
            scroll("我的账本")
            screenshot(application, "home")
            click("交易"); compose.onNodeWithText("交易记录").assertIsDisplayed(); screenshot(application, "transactions")
            click("账户"); compose.onNodeWithText("我的账户").assertIsDisplayed(); screenshot(application, "accounts")
            scroll("自定义账户名称")
            compose.onNode(hasText("自定义账户名称") and hasSetTextAction()).performTextInput("虚构布局账户")
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("添加账户") and hasClickAction())
            compose.onNode(hasText("添加账户") and hasClickAction()).assertIsDisplayed()
            screenshot(application, "account-form")
            // Dismiss keyboard before switching tabs so each destination is checked at full viewport.
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            click("分类"); compose.onNodeWithText("分类管理").assertIsDisplayed(); screenshot(application, "categories")
            click("设置"); compose.onNodeWithText("自动分类规则").assertIsDisplayed(); screenshot(application, "settings")
            click("概览"); scroll("统计分析"); click("统计分析")
            compose.onNodeWithText("统计分析").assertIsDisplayed(); screenshot(application, "analytics")
            scroll("支出分类占比"); compose.onNodeWithContentDescription("支出分类环形图").performScrollTo().assertIsDisplayed()
            scroll("最大五笔支出")
            compose.onNodeWithContentDescription("支出分类环形图").assertIsDisplayed()
            screenshot(application, "charts")
            scroll("上个月"); click("上个月")
            scroll("每日支出趋势"); compose.onNodeWithText("该月暂无支出趋势").assertExists()
        }
    }

    private fun screenshot(application: Application, screen: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val suffix = "${application.resources.configuration.screenWidthDp}dp-${application.resources.configuration.fontScale}"
        File(application.getExternalFilesDir(null), "ui-$screen-$suffix.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
