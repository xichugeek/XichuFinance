package com.xichugeek.finance.data

import org.junit.Assert.*
import org.junit.Test

class LocalClassifierTest {
    @Test fun rulesOverrideKeywordsAndRespectTypePriorityAndEnabled() {
        val categories = listOf(CategoryEntity(1, "餐饮", "expense"), CategoryEntity(2, "娱乐", "expense"), CategoryEntity(3, "其他", "expense"), CategoryEntity(4, "工资", "income"))
        val keywords = listOf(KeywordRule("餐饮", "expense", listOf("咖啡")), KeywordRule("工资", "income", listOf("salary")))
        val rules = listOf(RuleEntity(1, "咖啡", 1, "expense", 200), RuleEntity(2, "咖啡", 2, "expense", 20))
        assertEquals("娱乐", LocalClassifier.classify("咖啡", "expense", categories, rules, keywords).category)
        assertEquals("keyword", LocalClassifier.classify("咖啡", "expense", categories, rules.map { it.copy(enabled = false) }, keywords).source)
        val fallback = LocalClassifier.classify("未知", "expense", categories, rules, keywords)
        assertEquals("其他", fallback.category)
        assertFalse(fallback.aiEnabled)
        assertEquals("AI Enhancement Disabled", fallback.aiStatus)
        assertEquals("工资", LocalClassifier.classify("SALARY", "income", categories, rules, keywords).category)
    }
}
