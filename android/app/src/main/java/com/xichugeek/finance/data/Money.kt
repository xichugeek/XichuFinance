package com.xichugeek.finance.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object Money {
    fun parseMinor(input: String): Long {
        val value = input.trim().toBigDecimalOrNull()
            ?: throw IllegalArgumentException("请输入有效金额")
        require(value.signum() > 0) { "金额必须大于 0" }
        require(value.scale() <= 2) { "金额最多保留两位小数" }
        return try {
            value.movePointRight(2).longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("金额超出范围")
        }
    }

    fun formatMinor(minor: Long): String {
        val format = NumberFormat.getCurrencyInstance(Locale.CHINA)
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        format.roundingMode = RoundingMode.UNNECESSARY
        return format.format(BigDecimal.valueOf(minor, 2))
    }
}

data class MonthSummary(val incomeMinor: Long, val expenseMinor: Long) {
    val balanceMinor: Long get() = incomeMinor - expenseMinor
}

object FinanceMath {
    fun summarize(transactions: List<TransactionEntity>): MonthSummary = MonthSummary(
        incomeMinor = transactions.filter { it.type == "income" }.sumOf { it.amountMinor },
        expenseMinor = transactions.filter { it.type == "expense" }.sumOf { it.amountMinor },
    )

    fun accountBalance(account: AccountEntity, transactions: List<TransactionEntity>): Long =
        account.openingBalanceMinor + transactions.filter { it.accountId == account.id }.sumOf {
            if (it.type == "income") it.amountMinor else -it.amountMinor
        }
}
