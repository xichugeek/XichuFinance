package com.xichugeek.finance.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.Locale

@Serializable
data class KeywordRule(val category: String, val type: String, val keywords: List<String>)

@Serializable
data class ClassifyRequest(val description: String, val type: String)

@Serializable
data class ClassificationResult(
    @SerialName("category_id") val categoryId: Long, val category: String,
    val type: String, val source: String,
    @SerialName("ai_enabled") val aiEnabled: Boolean = false,
    @SerialName("ai_status") val aiStatus: String = "AI Enhancement Disabled",
)

@Serializable
data class RuleRequest(
    val keyword: String, @SerialName("category_id") val categoryId: Long, val type: String,
    val priority: Int = 100, val enabled: Boolean = true,
)

@Serializable
data class RemoteRule(
    val id: Long, @SerialName("user_id") val userId: Long,
    val keyword: String, @SerialName("category_id") val categoryId: Long,
    val type: String, val priority: Int, val enabled: Boolean,
) { fun entity() = RuleEntity(id, keyword, categoryId, type, priority, enabled) }

object LocalClassifier {
    fun classify(description: String, type: String, categories: List<CategoryEntity>, rules: List<RuleEntity>, keywords: List<KeywordRule>): ClassificationResult {
        val candidates = categories.filter { it.type == type }
        val text = description.lowercase(Locale.ROOT)
        val custom = rules.sortedWith(compareBy<RuleEntity> { it.priority }.thenBy { it.id }).firstOrNull {
            it.enabled && it.type == type && candidates.any { category -> category.id == it.categoryId } && it.keyword.lowercase(Locale.ROOT) in text
        }
        val builtIn = keywords.firstOrNull { entry -> entry.type == type && candidates.any { it.name == entry.category } && entry.keywords.any { it.lowercase(Locale.ROOT) in text } }
        val category = custom?.let { rule -> candidates.first { it.id == rule.categoryId } }
            ?: builtIn?.let { keyword -> candidates.first { it.name == keyword.category } }
            ?: candidates.firstOrNull { it.name == "其他" } ?: candidates.firstOrNull()
        requireNotNull(category) { "请先创建对应类型的分类" }
        return ClassificationResult(category.id, category.name, type, if (custom != null) "rule" else if (builtIn != null) "keyword" else "fallback")
    }
}
