package com.xichugeek.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.xichugeek.finance.data.*
import java.time.YearMonth

@Composable
internal fun DashboardScreen(state: FinanceUiState, nav: NavHostController) {
    val month = YearMonth.now()
    val analysis = remember(month, state.transactions, state.categories) {
        FinanceAnalytics.calculate(month, state.transactions, state.categories)
    }
    val total = state.accounts.sumOf { FinanceMath.accountBalance(it, state.transactions) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { PageHeading("我的账本", "每一笔，都心中有数") }
                Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, FinancePalette.Border)) {
                    Text("${month.monthValue} 月", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { MetricCard("本月支出", Money.formatMinor(analysis.summary.expenseMinor), FinanceIcons.Expense, FinancePalette.Accent) }
                Box(Modifier.weight(1f)) { MetricCard("本月收入", Money.formatMinor(analysis.summary.incomeMinor), FinanceIcons.Income, FinancePalette.Income) }
            }
        }
        item {
            Surface(color = FinancePalette.Ink, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("账户总余额", Modifier.weight(1f), color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium)
                        Icon(FinanceIcons.Wallet, null, tint = Color.White.copy(alpha = .65f), modifier = Modifier.size(22.dp))
                    }
                    AmountText(Money.formatMinor(total), Modifier.fillMaxWidth(), color = Color.White, large = true)
                    HorizontalDivider(color = Color.White.copy(alpha = .12f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("本月结余", Modifier.weight(1f), color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall)
                        Text(Money.formatMinor(analysis.summary.balanceMinor), color = Color.White, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${month.year} 年 ${month.monthValue} 月", style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
                Text(state.syncStatus, style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
                val delta = analysis.summary.expenseMinor - analysis.previous.expenseMinor
                Text("上月支出 ${Money.formatMinor(analysis.previous.expenseMinor)} · 本月${if (delta.signum() >= 0) "增加" else "减少"} ${Money.formatMinor(delta.abs())}", style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
            }
        }
        item {
            Button(onClick = { nav.navigate("transaction/new") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(FinanceIcons.Plus, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("添加一笔交易")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("统计分析", "看清收支去向", FinanceIcons.Chart, Modifier.weight(1f)) { nav.navigate("analytics") }
                QuickAction("CSV 账单导入", "已有账单，一次整理", FinanceIcons.Import, Modifier.weight(1f)) { nav.navigate("import") }
            }
        }
        item {
            Surface(onClick = { nav.navigate("ask") }, color = FinancePalette.Blush, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(FinanceIcons.Ask, null, tint = FinancePalette.Accent); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("问问我的账单", style = MaterialTheme.typography.titleSmall)
                        Text("这个月，钱都花在哪儿？", style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
                    }
                    Icon(FinanceIcons.Arrow, null, tint = FinancePalette.Accent, modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("最近交易", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { nav.navigate("transactions") }) { Text("查看全部") }
            }
        }
        if (state.transactions.isEmpty()) item { EmptyLedger("还没有交易记录", "从第一笔收支，开始了解自己的生活") }
        items(state.transactions.take(5), key = { it.id }) { item -> TransactionRow(item, state, Modifier.clickable { nav.navigate("transaction/${item.id}") }) }
        item { DailyTrendChart(analysis) }
    }
}

@Composable
private fun QuickAction(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, FinancePalette.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = FinancePalette.Accent, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
        }
    }
}

@Composable
internal fun MetricCard(label: String, value: String, icon: ImageVector, color: Color) {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, FinancePalette.Border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
            }
            AmountText(value, Modifier.fillMaxWidth(), color = color)
        }
    }
}

@Composable
internal fun SummaryCard(label: String, value: String, emphasized: Boolean = false) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, FinancePalette.Border)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = FinancePalette.Muted)
            AmountText(value, Modifier.fillMaxWidth(), color = if (emphasized) FinancePalette.Accent else FinancePalette.Ink, large = emphasized)
        }
    }
}

@Composable
internal fun EmptyLedger(title: String, subtitle: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(FinanceIcons.Transactions)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
        }
    }
}

@Composable
internal fun TransactionsScreen(state: FinanceUiState, nav: NavHostController) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { PageHeading("交易记录", "共 ${state.transactions.size} 笔收支") }
                Button(onClick = { nav.navigate("transaction/new") }, shape = RoundedCornerShape(14.dp)) {
                    Icon(FinanceIcons.Plus, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("添加")
                }
            }
        }
        if (state.transactions.isEmpty()) item { EmptyLedger("还没有交易记录", "暂无交易。点击“添加”记录第一笔。") }
        items(state.transactions, key = { it.id }) { item -> TransactionRow(item, state, Modifier.clickable { nav.navigate("transaction/${item.id}") }) }
    }
}

internal fun categoryIcon(name: String): ImageVector = when {
    name.contains("餐") || name.contains("食") -> FinanceIcons.Food
    name.contains("交通") -> FinanceIcons.Transport
    else -> FinanceIcons.Categories
}

@Composable
internal fun TransactionRow(item: TransactionEntity, state: FinanceUiState, modifier: Modifier = Modifier) {
    val category = state.categories.firstOrNull { it.id == item.categoryId }?.name ?: "未分类"
    val income = item.type == "income"
    val color = if (income) FinancePalette.Income else FinancePalette.Accent
    Surface(Modifier.fillMaxWidth().then(modifier), color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FinancePalette.Border)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(if (income) FinanceIcons.Income else categoryIcon(category), color, if (income) FinancePalette.Mint else FinancePalette.Blush)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.description, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$category · ${LedgerDates.decode(item.transactionDate)}", style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AmountText("${if (income) "+" else "−"}${Money.formatMinor(item.amountMinor)}", Modifier.widthIn(max = 130.dp).weight(.8f), color, textAlign = TextAlign.End)
        }
    }
}

@Composable
internal fun AccountsScreen(state: FinanceUiState, model: FinanceViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("cash") }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val kinds = listOf("cash" to "现金", "bank" to "银行卡", "credit" to "信用卡", "alipay" to "支付宝", "wechat" to "微信", "other" to "其他")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("我的账户", "${state.accounts.size} 个账户，余额一目了然") }
        item { SummaryCard("账户总余额", Money.formatMinor(state.accounts.sumOf { FinanceMath.accountBalance(it, state.transactions) }), emphasized = true) }
        items(state.accounts, key = { it.id }) { account ->
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, FinancePalette.Border)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(FinanceIcons.Wallet, FinancePalette.Ink, FinancePalette.Canvas)
                        Column(Modifier.weight(1f)) {
                            Text(account.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(kinds.firstOrNull { it.first == account.kind }?.second ?: "其他", style = MaterialTheme.typography.bodySmall, color = FinancePalette.Muted)
                        }
                    }
                    AmountText(Money.formatMinor(FinanceMath.accountBalance(account, state.transactions)), Modifier.fillMaxWidth())
                    HorizontalDivider(color = FinancePalette.Border)
                    Row {
                        TextButton(onClick = { editingId = account.id; name = account.name; kind = account.kind }, enabled = !state.busy) { Text("编辑账户") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { model.deleteAccount(account.id) }, enabled = !state.busy && state.transactions.none { it.accountId == account.id }) { Text("删除空账户") }
                    }
                }
            }
        }
        item { Text(if (editingId == null) "添加账户" else "编辑账户", style = MaterialTheme.typography.titleLarge) }
        item { OutlinedTextField(name, { name = it }, label = { Text("自定义账户名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { Picker("类型", kinds, kinds.indexOfFirst { it.first == kind }.toLong(), { kinds.indexOf(it).toLong() }, { it.second }) { kind = kinds[it.toInt()].first } }
        item {
            Button(onClick = {
                val current = state.accounts.firstOrNull { it.id == editingId }
                if (current == null) model.addAccount(name, kind) { name = ""; editingId = null }
                else model.updateAccount(current.copy(name = name, kind = kind)) { name = ""; editingId = null }
            }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(if (editingId == null) "添加账户" else "保存账户") }
            if (editingId != null) TextButton(onClick = { editingId = null; name = "" }) { Text("取消编辑") }
        }
    }
}
