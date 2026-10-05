package com.xichugeek.finance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.YearMonth

@Composable
internal fun AskScreen(state: FinanceUiState, model: FinanceViewModel, onBack: () -> Unit) {
    var question by remember { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(selected)
    val suggestions = listOf("这个月花了多少钱？", "这个月钱主要花在哪？", "餐饮花了多少？", "本月最大的五笔支出是什么？", "这个月收入多少？", "这个月结余多少？", "交通费比上个月高了吗？")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("问问我的账单", style = MaterialTheme.typography.headlineSmall) }
        item { Text("AI Enhancement Disabled", style = MaterialTheme.typography.bodySmall) }
        if (state.localMode) item { Text("问问账单需要登录云端账本。统计分析可查看本地收支。") }
        else {
            item { Text("金额由数据库计算，再用模板回答。查询会将问题发送给你的 Backend；当前没有外部 AI 请求。") }
            item { Row {
                TextButton(onClick = { selected = month.minusMonths(1).toString() }) { Text("上个月") }
                Text("${month.year} 年 ${month.monthValue} 月", Modifier.weight(1f).padding(top = 12.dp))
                TextButton(onClick = { selected = month.plusMonths(1).toString() }) { Text("下个月") }
            } }
            item { OutlinedTextField(question, { question = it }, label = { Text("输入账单问题") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
            item { Button(onClick = { model.ask(question, month.atDay(1).toString()) }, enabled = !state.busy && question.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "正在查询…" else "查询账单") } }
            state.askResult?.let { result -> item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(result.question, style = MaterialTheme.typography.titleMedium)
                Text(result.answer)
                Text("查询范围：${result.month.take(7)} · 数据库结果", style = MaterialTheme.typography.bodySmall)
            } } } }
            item { Text("可以这样问", style = MaterialTheme.typography.titleMedium) }
            suggestions.forEach { example -> item { OutlinedButton(onClick = { question = example }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(example) } } }
        }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") } }
    }
}
