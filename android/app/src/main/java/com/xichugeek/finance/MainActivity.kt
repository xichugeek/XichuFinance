package com.xichugeek.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xichugeek.finance.data.CategoryEntity
import com.xichugeek.finance.data.Money
import com.xichugeek.finance.data.LedgerDates
import com.xichugeek.finance.data.TransactionEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        val model = ViewModelProvider(this)[FinanceViewModel::class.java]
        setContent { FinanceApp(model) }
    }
}

@Composable
internal fun FinanceApp(model: FinanceViewModel) {
    val state by model.state.collectAsState()
    val error by model.error.collectAsState()
    FinanceTheme {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FinanceBrandMark(Modifier.size(64.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    CircularProgressIndicator()
                }
            }
            !state.hasLedger -> AuthScreen(state, error, model)
            else -> key(state.localMode, state.userId) { FinanceLedger(model) }
        }
    }
}

@Composable
private fun AuthScreen(state: FinanceUiState, error: String?, model: FinanceViewModel) {
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords stay in memory only and are not retained in saved instance state.
    var password by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Spacer(Modifier.height(24.dp))
            FinanceBrandMark(Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        item { Text("让每一笔收支清楚可见", color = MaterialTheme.colorScheme.secondary) }
        item { Text(if (register) "创建云端账户" else "登录云端账本", style = MaterialTheme.typography.titleLarge) }
        item { OutlinedTextField(email, { email = it }, label = { Text("邮箱") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(password, { password = it }, label = { Text("密码（8–128 个字符）") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth()) }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item { Button(onClick = { model.authenticate(email, password, register) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) (if (register) "正在注册并登录…" else "正在登录…") else if (register) "注册并登录" else "登录") } }
        item { TextButton(onClick = { register = !register; model.clearError() }, enabled = !state.busy) { Text(if (register) "已有账户，去登录" else "还没有账户？注册") } }
        item { OutlinedButton(onClick = { model.useLocalMode() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("打开本地账本") } }
        item { Text("云端账本会将收支保存到服务器。本地账本仅保存在此设备，首次打开含虚构示例。请勿输入银行卡号或支付密码。", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun FinanceLedger(model: FinanceViewModel) {
    val state by model.state.collectAsState()
    val error by model.error.collectAsState()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); model.clearError() }
    }

    FinanceTheme {
        Scaffold(
            modifier = Modifier.imePadding(),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                FinanceTopBar()
            },
            bottomBar = {
                if (route in listOf("home", "transactions", "accounts", "categories", "settings")) {
                    FinanceBottomNavigation(route) { destination ->
                        nav.navigate(destination) { popUpTo("home"); launchSingleTop = true }
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
                composable("home") { DashboardScreen(state, nav) }
                composable("transactions") { TransactionsScreen(state, nav) }
                composable("transaction/new") { TransactionEditor(state, model, nav, null) }
                composable("transaction/edit/{id}") { backStack ->
                    TransactionEditor(state, model, nav, backStack.arguments?.getString("id")?.toLongOrNull())
                }
                composable("transaction/{id}") { backStack ->
                    TransactionDetail(state, model, nav, backStack.arguments?.getString("id")?.toLongOrNull())
                }
                composable("accounts") { AccountsScreen(state, model) }
                composable("categories") { CategoriesScreen(state, model) }
                composable("settings") { SettingsScreen(state, model) { nav.navigate("rules") } }
                composable("import") { ImportScreen(state, model) { nav.popBackStack() } }
                composable("analytics") { AnalyticsScreen(state, { nav.navigate("transaction/$it") }) { nav.popBackStack() } }
                composable("rules") { RulesScreen(state, model) { nav.popBackStack() } }
                composable("ask") { AskScreen(state, model) { nav.popBackStack() } }
            }
        }
    }
}

@Composable
private fun TransactionDetail(state: FinanceUiState, model: FinanceViewModel, nav: NavHostController, id: Long?) {
    val item = state.transactions.firstOrNull { it.id == id }
    if (item == null) {
        Text("交易不存在", modifier = Modifier.padding(20.dp))
        return
    }
    val account = state.accounts.firstOrNull { it.id == item.accountId }?.name ?: "未知账户"
    val category = state.categories.firstOrNull { it.id == item.categoryId }?.name ?: "未分类"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeading("交易详情")
        SummaryCard(item.description, "${if (item.type == "income") "+" else "−"}${Money.formatMinor(item.amountMinor)}")
        Text("日期：${LedgerDates.decode(item.transactionDate)}")
        Text("账户：$account")
        Text("分类：$category")
        Text("来源：${when (item.source) { "demo" -> "虚构示例"; "csv" -> "CSV 导入"; else -> "手动录入" }}")
        Button(onClick = { nav.navigate("transaction/edit/${item.id}") }, modifier = Modifier.fillMaxWidth()) { Text("编辑") }
        OutlinedButton(onClick = { model.deleteTransaction(item.id) { nav.popBackStack() } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("删除交易") }
        OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("返回") }
    }
}

@Composable
private fun TransactionEditor(state: FinanceUiState, model: FinanceViewModel, nav: NavHostController, id: Long?) {
    val original = state.transactions.firstOrNull { it.id == id }
    if (id != null && original == null) {
        Text("正在加载交易…", modifier = Modifier.padding(20.dp))
        return
    }
    var type by rememberSaveable(original?.id) { mutableStateOf(original?.type ?: "expense") }
    var description by rememberSaveable(original?.id) { mutableStateOf(original?.description ?: "") }
    var amount by rememberSaveable(original?.id) { mutableStateOf(original?.let { BigDecimal.valueOf(it.amountMinor, 2).toPlainString() } ?: "") }
    var date by rememberSaveable(original?.id) {
        mutableStateOf(original?.let { LedgerDates.decode(it.transactionDate).toString() } ?: LocalDate.now().toString())
    }
    var accountId by rememberSaveable(original?.id, state.accounts.size) { mutableLongStateOf(original?.accountId ?: state.accounts.firstOrNull()?.id ?: 0L) }
    var categoryId by rememberSaveable(original?.id, type, state.categories.size) {
        mutableLongStateOf(original?.categoryId?.takeIf { key -> state.categories.any { it.id == key && it.type == type } }
            ?: state.categories.firstOrNull { it.type == type }?.id ?: 0L)
    }
    val categories = state.categories.filter { it.type == type }
    var classificationMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeading(if (id == null) "添加交易" else "编辑交易", "记录金额，留住生活的细节") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "expense", onClick = { type = "expense" }, label = { Text("支出") })
                FilterChip(selected = type == "income", onClick = { type = "income" }, label = { Text("收入") })
            }
        }
        item { OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额（元）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("描述") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item {
            OutlinedButton(onClick = {
                val input = description
                val selectedType = type
                model.classify(input, selectedType) { result ->
                    if (description == input && type == selectedType) {
                        categoryId = result.categoryId
                        classificationMessage = "建议分类：${result.category} · ${when (result.source) { "rule" -> "自定义规则"; "keyword" -> "内置关键词"; "ai" -> "AI 建议"; else -> "默认分类" }}"
                    }
                }
            }, enabled = !state.busy && description.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("自动分类") }
            classificationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        item { OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("日期 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item {
            Picker("账户", state.accounts, accountId, { it.id }, { it.name }) { accountId = it }
        }
        item {
            Picker("分类", categories, categoryId, { it.id }, { it.name }) { categoryId = it }
        }
        item {
            Button(
                onClick = {
                    try {
                        val minor = Money.parseMinor(amount)
                        val parsedDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                        model.saveTransaction(
                            TransactionEntity(
                                id = original?.id ?: 0,
                                accountId = accountId,
                                categoryId = categoryId,
                                type = type,
                                amountMinor = minor,
                                description = description.trim(),
                                transactionDate = LedgerDates.encode(parsedDate),
                                source = original?.source ?: "manual",
                                externalId = original?.externalId,
                            ),
                        ) { nav.popBackStack() }
                    } catch (e: Exception) {
                        model.showError(e.message ?: "请检查输入")
                    }
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存交易") }
        }
        item { OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("取消") } }
    }
}

@Composable
internal fun <T> Picker(label: String, items: List<T>, selected: Long, id: (T) -> Long, title: (T) -> String, onSelected: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label：${items.firstOrNull { id(it) == selected }?.let(title) ?: "请选择"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(title(item)) }, onClick = { onSelected(id(item)); expanded = false })
            }
        }
    }
}

@Composable
private fun CategoriesScreen(state: FinanceUiState, model: FinanceViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("expense") }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editName by rememberSaveable { mutableStateOf("") }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    state.categories.firstOrNull { it.id == editingId }?.let { category ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) editingId = null },
            title = { Text("编辑分类") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (category.type == "expense") "支出分类" else "收入分类")
                    OutlinedTextField(editName, { editName = it }, label = { Text("分类名称") },
                        singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("edit-category-name"))
                    Text("修改名称会同步更新原有交易和规则中的分类名称。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { model.updateCategory(category.id, editName) { editingId = null } },
                enabled = !state.busy && editName.isNotBlank()) { Text(if (state.busy) "正在保存…" else "保存修改") } },
            dismissButton = { TextButton(onClick = { editingId = null }, enabled = !state.busy) { Text("取消") } },
        )
    }
    state.categories.firstOrNull { it.id == deletingId }?.let { category ->
        val transactions = state.transactions.count { it.categoryId == category.id }
        val rules = state.rules.count { it.categoryId == category.id }
        val used = transactions > 0 || rules > 0
        AlertDialog(
            onDismissRequest = { if (!state.busy) deletingId = null },
            title = { Text(if (used) "暂时无法删除" else "删除分类？") },
            text = { Text(if (used) "“${category.name}”关联了 $transactions 笔交易、$rules 条规则。请先修改交易分类或删除对应规则，再删除此分类。"
                else "确认删除“${category.name}”？删除后可重新添加。") },
            confirmButton = {
                if (used) TextButton(onClick = { deletingId = null }) { Text("知道了") }
                else TextButton(onClick = { model.deleteCategory(category.id) { deletingId = null } }, enabled = !state.busy) {
                    Text(if (state.busy) "正在删除…" else "确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { if (!used) TextButton(onClick = { deletingId = null }, enabled = !state.busy) { Text("取消") } },
        )
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeading("分类管理", "让每一笔收支各有所属") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "expense", onClick = { type = "expense" }, label = { Text("支出分类") })
                FilterChip(selected = type == "income", onClick = { type = "income" }, label = { Text("收入分类") })
            }
        }
        items(state.categories.filter { it.type == type }, key = { it.id }) { category ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(categoryIcon(category.name))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val count = state.transactions.count { it.categoryId == category.id }
                            val rules = state.rules.count { it.categoryId == category.id }
                            Text("$count 笔交易 · $rules 条规则", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editingId = category.id; editName = category.name }, enabled = !state.busy,
                            modifier = Modifier.testTag("category-edit-${category.id}")) { Text("编辑") }
                        TextButton(onClick = { deletingId = category.id }, enabled = !state.busy,
                            modifier = Modifier.testTag("category-delete-${category.id}")) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)); Text("添加分类", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("分类名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { model.addCategory(name, type) { name = "" } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("添加分类") } }
    }
}

@Composable
private fun SettingsScreen(state: FinanceUiState, model: FinanceViewModel, onRules: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("设置", "管理账本与同步") }
        item { Text(if (state.localMode) "本地账本" else state.email, style = MaterialTheme.typography.titleMedium) }
        item { Text(state.syncStatus) }
        item { Text("AI Enhancement Disabled", style = MaterialTheme.typography.bodySmall) }
        item { OutlinedButton(onClick = onRules, modifier = Modifier.fillMaxWidth()) { Text("自动分类规则") } }
        if (!state.localMode) {
            item { Button(onClick = { model.refresh() }, enabled = !state.busy && !state.loginExpired, modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "正在同步…" else "刷新云端账本") } }
            item { Text("云端修改需要联网。离线时可查看已同步数据；恢复联网后点击刷新。", style = MaterialTheme.typography.bodyMedium) }
        }
        item { OutlinedButton(onClick = { model.logout() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.localMode) "返回登录页" else if (state.loginExpired) "重新登录" else "退出登录") } }
        item { Text("退出后，此设备的缓存会保留，但只有重新登录同一用户才可打开。设备锁屏保护本地数据；Room 缓存目前未加密。", style = MaterialTheme.typography.bodySmall) }
        item { Text(if (BuildConfig.DEBUG) "开发环境：${BuildConfig.API_BASE_URL}" else "服务：${BuildConfig.API_BASE_URL}", style = MaterialTheme.typography.bodySmall) }
    }
}
