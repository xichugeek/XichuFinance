package com.xichugeek.finance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xichugeek.finance.data.RuleEntity

@Composable
internal fun RulesScreen(state: FinanceUiState, model: FinanceViewModel, onBack: () -> Unit) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("expense") }
    var priority by rememberSaveable { mutableStateOf("100") }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var categoryId by rememberSaveable { mutableLongStateOf(state.categories.firstOrNull { it.type == type }?.id ?: 0L) }
    LaunchedEffect(type, state.categories) {
        if (state.categories.none { it.id == categoryId && it.type == type }) categoryId = state.categories.firstOrNull { it.type == type }?.id ?: 0L
    }
    fun reset() { keyword = ""; priority = "100"; editingId = null; enabled = true }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("自动分类规则", style = MaterialTheme.typography.headlineSmall) }
        item { Text("AI Enhancement Disabled", style = MaterialTheme.typography.bodySmall) }
        item { Text("先匹配你的规则，再匹配内置关键词。关键词按文字包含匹配；优先级越小越先匹配。分类建议可手动修改。") }
        item { Text(if (editingId == null) "添加规则" else "编辑规则", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedTextField(keyword, { keyword = it }, label = { Text("关键词") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(type == "expense", onClick = { type = "expense" }, label = { Text("支出规则") })
            FilterChip(type == "income", onClick = { type = "income" }, label = { Text("收入规则") })
        } }
        item { Picker("目标分类", state.categories.filter { it.type == type }, categoryId, { it.id }, { it.name }) { categoryId = it } }
        item { OutlinedTextField(priority, { priority = it }, label = { Text("优先级（0–1000）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
        item { Row { Checkbox(enabled, { enabled = it }); Text("启用规则", Modifier.padding(top = 12.dp)) } }
        item { Button(onClick = {
            val number = priority.toIntOrNull()
            if (number == null) model.showError("优先级应为 0–1000")
            else model.saveRule(RuleEntity(editingId ?: 0L, keyword, categoryId, type, number, enabled)) { reset() }
        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("保存规则") }
            if (editingId != null) TextButton(onClick = { reset() }) { Text("取消编辑") }
        }
        item { Text("已保存规则", style = MaterialTheme.typography.titleMedium) }
        if (state.rules.isEmpty()) item { Text("暂无自定义规则，内置关键词仍可自动分类。") }
        items(state.rules, key = { it.id }) { rule -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            val category = state.categories.firstOrNull { it.id == rule.categoryId }?.name ?: "未分类"
            Text("${rule.keyword} → $category", style = MaterialTheme.typography.titleMedium)
            Text("${if (rule.type == "expense") "支出" else "收入"} · 优先级 ${rule.priority} · ${if (rule.enabled) "已启用" else "已停用"}")
            Row {
                TextButton(onClick = { type = rule.type; categoryId = rule.categoryId; editingId = rule.id; keyword = rule.keyword; priority = rule.priority.toString(); enabled = rule.enabled }, enabled = !state.busy) { Text("编辑规则") }
                TextButton(onClick = { model.deleteRule(rule.id) }, enabled = !state.busy) { Text("删除规则") }
            }
        } } }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") } }
    }
}
