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

class Phase7ClassificationUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun savedRuleOverridesSharedKeywordsAndSuggestionDoesNotSaveTransaction() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val api = ApiClient.create()
        lateinit var session: UserSession
        runBlocking {
            val credentials = Credentials("rules-ui-${UUID.randomUUID()}@example.com", UUID.randomUUID().toString())
            val user = api.register(credentials)
            session = UserSession(user.id, user.email, api.login(credentials).token)
            api.addAccount(session.authorization, AccountRequest("虚构规则钱包", "cash"))
            assertEquals("餐饮", api.classify(session.authorization, ClassifyRequest("咖啡", "expense")).category)
            SessionStore(application).save(session)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: FinanceViewModel
            scenario.onActivity { model = ViewModelProvider(it)[FinanceViewModel::class.java] }
            compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.categories.size == 14 }
            compose.onNodeWithText("设置").performClick()
            compose.onNodeWithText("AI Enhancement Disabled").assertExists()
            compose.onNode(hasText("自动分类规则") and hasClickAction()).performClick()
            compose.onNodeWithText("关键词").performTextInput("咖啡")
            compose.onNodeWithText("目标分类：餐饮").performClick()
            compose.onNodeWithText("娱乐").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("保存规则") and hasClickAction())
            compose.onNodeWithText("保存规则").performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.rules.size == 1 }
            val cached = model.state.value
            val keywords = application.assets.open("classification_keywords.json").bufferedReader().use { ApiClient.json.decodeFromString<List<KeywordRule>>(it.readText()) }
            assertEquals("娱乐", LocalClassifier.classify("咖啡", "expense", cached.categories, cached.rules, keywords).category)
            runBlocking {
                assertEquals("娱乐", api.classify(session.authorization, ClassifyRequest("咖啡", "expense")).category)
                assertEquals(1, api.rules(session.authorization).size)
            }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("返回") and hasClickAction())
            compose.onNodeWithText("返回").performClick()
            compose.onNode(hasText("交易") and hasClickAction()).performClick()
            compose.onNodeWithText("添加").performClick()
            compose.onNodeWithText("描述").performTextInput("虚构咖啡")
            compose.onNodeWithText("自动分类").performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("建议分类：娱乐 · 自定义规则").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("分类：娱乐") and hasClickAction())
            compose.onNodeWithText("分类：娱乐").assertIsDisplayed()
            runBlocking { assertTrue(api.transactions(session.authorization).isEmpty()) }
        }
    }
}
