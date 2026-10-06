package com.xichugeek.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import com.xichugeek.finance.data.*
import java.math.BigInteger
import java.time.YearMonth

private val chartColors = listOf(FinancePalette.Accent, Color(0xFFFFBB6A), Color(0xFF67B5AE), Color(0xFF7D9BE0), Color(0xFFB7A0D6), Color(0xFFB0B8C6))

@Composable
internal fun AnalyticsScreen(state: FinanceUiState, onTransaction: (Long) -> Unit, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(selected)
    val analysis = remember(month, state.transactions, state.categories) { FinanceAnalytics.calculate(month, state.transactions, state.categories) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("统计分析", "看见数字背后的生活") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = month.minusMonths(1).toString() }) { Text("上个月") }
                Text("${month.year} 年 ${month.monthValue} 月", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { selected = month.plusMonths(1).toString() }) { Text("下个月") }
            }
        }
        item { Text(state.syncStatus, style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { MetricCard("月支出", Money.formatMinor(analysis.summary.expenseMinor), FinanceIcons.Expense, FinancePalette.Accent) }
                Box(Modifier.weight(1f)) { MetricCard("月收入", Money.formatMinor(analysis.summary.incomeMinor), FinanceIcons.Income, FinancePalette.Income) }
            }
        }
        item { SummaryCard("月结余", Money.formatMinor(analysis.summary.balanceMinor)) }
        item {
            val delta = analysis.summary.expenseMinor - analysis.previous.expenseMinor
            Text("上月支出 ${Money.formatMinor(analysis.previous.expenseMinor)}，本月${if (delta.signum() >= 0) "增加" else "减少"} ${Money.formatMinor(delta.abs())}", style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
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
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, FinancePalette.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("每日支出趋势", style = MaterialTheme.typography.titleMedium)
            Text("每日峰值 ${Money.formatMinor(peak)}", style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
            if (peak.signum() == 0) {
                Box(Modifier.fillMaxWidth().height(110.dp).background(FinancePalette.Canvas, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text("该月暂无支出趋势", style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
                }
            }
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
                val fill = Path().apply {
                    addPath(path)
                    lineTo(size.width - inset, size.height - inset)
                    lineTo(inset, size.height - inset)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = .2f), lineColor.copy(alpha = .01f))))
                drawPath(path, lineColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            Row(Modifier.fillMaxWidth()) {
                Text("1 日", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
                Text("${analysis.month.lengthOfMonth()} 日", style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
            }
        }
    }
}

@Composable
internal fun CategoryChart(analysis: MonthlyAnalysis) {
    val all = analysis.categories
    val segments = if (all.size <= 6) all else all.take(5) + CategoryTotal(Long.MIN_VALUE, "其余分类", all.drop(5).fold(BigInteger.ZERO) { total, item -> total + item.amountMinor })
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, FinancePalette.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("支出分类占比", style = MaterialTheme.typography.titleMedium)
            if (all.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconBadge(FinanceIcons.Chart, FinancePalette.Muted, FinancePalette.Canvas)
                        Text("该月暂无分类支出", style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
                    }
                }
            }
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("支出分类", style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
                        Text("${all.size} 项", style = MaterialTheme.typography.titleLarge)
                    }
                }
                segments.forEachIndexed { index, item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(chartColors[index], RoundedCornerShape(3.dp)))
                            Spacer(Modifier.width(8.dp))
                            Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Money.formatMinor(item.amountMinor), style = MaterialTheme.typography.titleSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LinearProgressIndicator(progress = { FinanceAnalytics.fraction(item.amountMinor, analysis.summary.expenseMinor) }, modifier = Modifier.weight(1f).height(5.dp), color = chartColors[index], trackColor = FinancePalette.Canvas, gapSize = 0.dp, drawStopIndicator = {})
                            Text("${FinanceAnalytics.percentage(item.amountMinor, analysis.summary.expenseMinor)}%", style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
                        }
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
