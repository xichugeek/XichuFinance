package com.xichugeek.finance.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.ZoneId

class FinanceRepository(private val database: FinanceDatabase) {
    private val dao = database.dao()

    val accounts = dao.observeAccounts()
    val categories = dao.observeCategories()
    val transactions = dao.observeTransactions()

    suspend fun seedIfEmpty() = database.withTransaction {
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
                    transactionDate = today.minusDays(index.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
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
                transactionDate = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                source = "demo",
            ),
        )
    }

    suspend fun addAccount(name: String, kind: String) {
        require(name.isNotBlank()) { "请输入账户名称" }
        dao.insertAccount(AccountEntity(name = name.trim(), kind = kind))
    }

    suspend fun addCategory(name: String, type: String) {
        require(name.isNotBlank()) { "请输入分类名称" }
        require(type == "income" || type == "expense")
        dao.insertCategory(CategoryEntity(name = name.trim(), type = type))
    }

    suspend fun saveTransaction(transaction: TransactionEntity) {
        require(transaction.amountMinor > 0) { "金额必须大于 0" }
        require(transaction.description.isNotBlank()) { "请输入描述" }
        require(transaction.type == "income" || transaction.type == "expense")
        if (transaction.id == 0L) {
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
        check(dao.deleteTransaction(id) == 1) { "交易不存在" }
    }
}
