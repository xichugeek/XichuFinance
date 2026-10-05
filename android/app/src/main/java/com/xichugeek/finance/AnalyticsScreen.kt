package com.xichugeek.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xichugeek.finance.data.*
import java.math.BigInteger
import java.time.YearMonth

private val chartColors = listOf(Color(0xFF176B5B), Color(0xFF4579AD), Color(0xFFC89835), Color(0xFF9065A9), Color(0xFFCE7952), Color(0xFF82978E))

@Composable
internal fun AnalyticsScreen(state: FinanceUiState, onTransaction: (Long) -> Unit, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(selected)
    val analysis = remember(month, state.transactions, state.categories) { FinanceAnalytics.calculate(month, state.transactions, state.categories) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("统计分析", style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = month.minusMonths(1).toString() }) { Text("上个月") }
                Text("${month.year} 年 ${month.monthValue} 月", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { selected = month.plusMonths(1).toString() }) { Text("下个月") }
            }
        }
        item { Text(state.syncStatus, style = MaterialTheme.typography.bodySmall) }
        item { SummaryCard("月收入", Money.formatMinor(analysis.summary.incomeMinor)) }
        item { SummaryCard("月支出", Money.formatMinor(analysis.summary.expenseMinor)) }
        item { SummaryCard("月结余", Money.formatMinor(analysis.summary.balanceMinor)) }
        item {
            val delta = analysis.summary.expenseMinor - analysis.previous.expenseMinor
            Text("上月支出 ${Money.formatMinor(analysis.previous.expenseMinor)}，本月${if (delta.signum() >= 0) "增加" else "减少"} ${Money.formatMinor(delta.abs())}")
        }
        item { DailyTrendChart(analysis) }
        item { CategoryChart(analysis) }
        item { Text("最大五笔支出", style = MaterialTheme.typography.titleLarge) }
        if (analysis.largest.isEmpty()) item { Text("该月暂无支出") }
        items(analysis.largest, key = { "largest-${it.id}" }) { row -> TransactionRow(row, state, Modifier.clickable { onTransaction(row.id) }) }
        item { Text("该月最近交易", style = MaterialTheme.typography.titleLarge) }
        items(analysis.recent, key = { "recent-${it.id}" }) { row -> TransactionRow(row, state, Modifier.clickable { onTransaction(row.id) }) }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") } }
    }
}

@Composable
internal fun DailyTrendChart(analysis: MonthlyAnalysis) {
    val peak = analysis.daily.maxOrNull() ?: BigInteger.ZERO
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("每日支出趋势", style = MaterialTheme.typography.titleMedium)
            Text("每日峰值 ${Money.formatMinor(peak)}", style = MaterialTheme.typography.bodySmall)
            if (peak.signum() == 0) Text("该月暂无支出趋势")
            else Canvas(Modifier.fillMaxWidth().height(130.dp).semantics { contentDescription = "${analysis.month} 每日支出折线图，峰值 ${Money.formatMinor(peak)}" }) {
                val inset = 6.dp.toPx()
                val width = size.width - 2 * inset
                val height = size.height - 2 * inset
                for (level in 0..2) {
                    val y = inset + height * level / 2
                    drawLine(gridColor, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                }
                val path = Path()
                analysis.daily.forEachIndexed { index, amount ->
                    val point = Offset(inset + width * index / (analysis.daily.size - 1), inset + height * (1 - FinanceAnalytics.fraction(amount, peak)))
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(path, lineColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            Row(Modifier.fillMaxWidth()) { Text("1 日", Modifier.weight(1f)); Text("${analysis.month.lengthOfMonth()} 日") }
        }
    }
}

@Composable
internal fun CategoryChart(analysis: MonthlyAnalysis) {
    val all = analysis.categories
    val segments = if (all.size <= 6) all else all.take(5) + CategoryTotal(Long.MIN_VALUE, "其余分类", all.drop(5).fold(BigInteger.ZERO) { total, item -> total + item.amountMinor })
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("支出分类占比", style = MaterialTheme.typography.titleMedium)
            if (all.isEmpty()) Text("该月暂无分类支出")
            else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(160.dp).semantics { contentDescription = "支出分类环形图" }) {
                        val thickness = 26.dp.toPx()
                        var start = -90f
                        segments.forEachIndexed { index, item ->
                            val sweep = FinanceAnalytics.fraction(item.amountMinor, analysis.summary.expenseMinor) * 360
                            drawArc(chartColors[index], start, sweep, false,
                                topLeft = Offset(thickness / 2, thickness / 2), size = Size(size.width - thickness, size.height - thickness),
                                style = Stroke(thickness))
                            start += sweep
                        }
                    }
                }
                segments.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = chartColors[index], modifier = Modifier.padding(end = 8.dp))
                        Text(item.name, Modifier.weight(1f))
                        Text("${FinanceAnalytics.percentage(item.amountMinor, analysis.summary.expenseMinor)}% · ${Money.formatMinor(item.amountMinor)}")
                    }
                }
            }
            if (all.size > 6) {
                Text("分类支出排行", style = MaterialTheme.typography.titleSmall)
                all.forEach { item -> Text("${item.name} · ${Money.formatMinor(item.amountMinor)}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
