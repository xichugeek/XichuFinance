package com.xichugeek.finance.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.math.BigDecimal
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class FinanceRepository(
    private val database: FinanceDatabase,
    private val api: FinanceApi? = null,
    private val session: UserSession? = null,
    private val keywords: List<KeywordRule> = emptyList(),
) {
    private val dao = database.dao()
    private val authorization: String get() = checkNotNull(session).authorization

    val accounts = dao.observeAccounts()
    val categories = dao.observeCategories()
    val transactions = dao.observeTransactions()
    val rules = dao.observeRules()
    suspend fun ask(question: String, month: String): AskResult {
        require(api != null) { "问问账单需要登录云端账本" }
        require(question.isNotBlank() && question.length <= 300) { "问题长度应为 1–300 个字符" }
        return api.ask(authorization, AskRequest(question.trim(), month))
    }

    suspend fun classify(description: String, type: String): ClassificationResult {
        require(description.isNotBlank()) { "请先填写交易描述" }
        return if (api == null) LocalClassifier.classify(description, type, categories.first(), rules.first(), keywords)
        else api.classify(authorization, ClassifyRequest(description, type))
    }

    suspend fun saveRule(rule: RuleEntity) {
        require(rule.keyword.isNotBlank() && rule.keyword.trim().length <= 100) { "关键词长度应为 1–100 个字符" }
        require(rule.priority in 0..1000) { "优先级应为 0–1000，越小越先匹配" }
        require(categories.first().any { it.id == rule.categoryId && it.type == rule.type }) { "请选择对应类型的分类" }
        val normalized = rule.copy(keyword = rule.keyword.trim().lowercase(java.util.Locale.ROOT))
        if (api == null) {
            if (rule.id == 0L) dao.insertRule(normalized) else dao.cacheRule(normalized)
        } else {
            val request = RuleRequest(normalized.keyword, rule.categoryId, rule.type, rule.priority, rule.enabled)
            val remote = if (rule.id == 0L) api.addRule(authorization, request) else api.updateRule(authorization, rule.id, request)
            check(remote.userId == session?.id)
            dao.cacheRule(remote.entity())
        }
    }

    suspend fun deleteRule(id: Long) {
        api?.deleteRule(authorization, id)
        check(dao.deleteRule(id) == 1) { "规则不存在" }
    }

    suspend fun previewCsv(bytes: ByteArray): CsvPreview {
        require(api != null) { "CSV 导入需要登录云端账本" }
        StandardCsvParser.inspect(bytes)
        val part = MultipartBody.Part.createFormData("file", "transactions.csv", bytes.toRequestBody("text/csv".toMediaType()))
        return api.previewCsv(authorization, part)
    }

    suspend fun commitCsv(preview: CsvPreview): CsvCommitResult {
        require(api != null) { "CSV 导入需要登录云端账本" }
        return api.commitCsv(authorization, CsvCommitRequest(preview.token))
    }

    suspend fun refresh() {
        val remote = api ?: return
        val userId = checkNotNull(session).id
        check(remote.me(authorization).id == userId) { "登录用户不匹配，请重新登录" }
        // Fetch and validate the complete response before replacing any cached records.
        val accounts = remote.accounts(authorization).onEach { check(it.userId == userId) }.map { it.entity() }
        val categories = remote.categories(authorization).onEach { check(it.userId == userId) }.map { it.entity() }
        val transactions = remote.transactions(authorization).onEach { check(it.userId == userId) }.map { it.entity() }
        val rules = remote.rules(authorization).onEach { check(it.userId == userId) }.map { it.entity() }
        database.withTransaction {
            dao.clearTransactions(); dao.clearRules(); dao.clearCategories(); dao.clearAccounts()
            dao.insertAccounts(accounts); dao.insertCategories(categories); dao.insertTransactions(transactions)
            dao.insertRules(rules)
        }
    }

    suspend fun seedIfEmpty() = database.withTransaction {
        check(api == null) { "云端用户不能插入本地示例" }
        if (dao.accountCount() != 0) return@withTransaction

        dao.insertAccount(AccountEntity(name = "现金", kind = "cash", openingBalanceMinor = 30000))
        val bank = dao.insertAccount(AccountEntity(name = "日常银行卡", kind = "bank", openingBalanceMinor = 1000000))
        val alipay = dao.insertAccount(AccountEntity(name = "支付宝", kind = "alipay", openingBalanceMinor = 50000))

        val expenseNames = listOf("餐饮", "交通", "购物", "住房", "娱乐", "医疗", "教育", "通讯", "旅行", "其他")
        val incomeNames = listOf("工资", "奖金", "投资", "其他")
        val expenseIds = expenseNames.associateWith { dao.insertCategory(CategoryEntity(name = it, type = "expense")) }
        val incomeIds = incomeNames.associateWith { dao.insertCategory(CategoryEntity(name = it, type = "income")) }
        val today = LocalDate.now()
        val samples = listOf(
            Triple("早餐", 1500L, expenseIds.getValue("餐饮")),
            Triple("地铁", 400L, expenseIds.getValue("交通")),
            Triple("超市", 8950L, expenseIds.getValue("购物")),
            Triple("咖啡", 2800L, expenseIds.getValue("餐饮")),
        )
        samples.forEachIndexed { index, sample ->
            dao.insertTransaction(
                TransactionEntity(
                    accountId = if (index == 2) bank else alipay,
                    categoryId = sample.third,
                    type = "expense",
                    amountMinor = sample.second,
                    description = sample.first,
                    transactionDate = LedgerDates.encode(today.minusDays(index.toLong())),
                    source = "demo",
                ),
            )
        }
        dao.insertTransaction(
            TransactionEntity(
                accountId = bank,
                categoryId = incomeIds.getValue("工资"),
                type = "income",
                amountMinor = 600000,
                description = "虚构工资",
                transactionDate = LedgerDates.encode(today.withDayOfMonth(1)),
                source = "demo",
            ),
        )
    }

    suspend fun addAccount(name: String, kind: String) {
        require(name.isNotBlank()) { "请输入账户名称" }
        if (api == null) dao.insertAccount(AccountEntity(name = name.trim(), kind = kind))
        else dao.cacheAccount(api.addAccount(authorization, AccountRequest(name.trim(), kind)).also {
            check(it.userId == session?.id)
        }.entity())
    }

    suspend fun updateAccount(account: AccountEntity) {
        require(account.name.isNotBlank()) { "请输入账户名称" }
        if (api == null) check(dao.updateAccount(account.id, account.name.trim(), account.kind, account.openingBalanceMinor) == 1)
        else dao.cacheAccount(api.updateAccount(authorization, account.id, AccountRequest(
            account.name.trim(), account.kind, BigDecimal.valueOf(account.openingBalanceMinor, 2).toPlainString(),
        )).also { check(it.userId == session?.id) }.entity())
    }

    suspend fun deleteAccount(id: Long) {
        api?.deleteAccount(authorization, id)
        check(dao.deleteAccount(id) == 1) { "账户不存在" }
    }

    suspend fun addCategory(name: String, type: String) {
        require(name.isNotBlank()) { "请输入分类名称" }
        require(type == "income" || type == "expense")
        if (api == null) dao.insertCategory(CategoryEntity(name = name.trim(), type = type))
        else dao.cacheCategory(api.addCategory(authorization, CategoryRequest(name.trim(), type))
            .also { check(it.userId == session?.id) }.entity())
    }

    suspend fun saveTransaction(transaction: TransactionEntity) {
        require(transaction.amountMinor > 0) { "金额必须大于 0" }
        require(transaction.description.isNotBlank()) { "请输入描述" }
        require(transaction.type == "income" || transaction.type == "expense")
        if (api != null) {
            val request = TransactionRequest(
                accountId = transaction.accountId, categoryId = transaction.categoryId, type = transaction.type,
                amount = BigDecimal.valueOf(transaction.amountMinor, 2).toPlainString(),
                description = transaction.description.trim(),
                transactionDate = LedgerDates.decode(transaction.transactionDate).toString(),
            )
            val result = if (transaction.id == 0L) api.addTransaction(authorization, request)
            else api.updateTransaction(authorization, transaction.id, request)
            check(result.userId == session?.id)
            dao.cacheTransaction(result.entity())
        } else if (transaction.id == 0L) {
            dao.insertTransaction(transaction)
        } else {
            check(dao.updateTransaction(
                transaction.id,
                transaction.accountId,
                transaction.categoryId,
                transaction.type,
                transaction.amountMinor,
                transaction.description.trim(),
                transaction.transactionDate,
                System.currentTimeMillis(),
            ) == 1) { "交易不存在" }
        }
    }

    suspend fun deleteTransaction(id: Long) {
        api?.deleteTransaction(authorization, id)
        check(dao.deleteTransaction(id) == 1) { "交易不存在" }
    }
}
