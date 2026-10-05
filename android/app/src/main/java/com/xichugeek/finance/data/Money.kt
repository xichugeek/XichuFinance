package com.xichugeek.finance.data

import java.math.BigDecimal
import java.math.BigInteger
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
            value.movePointRight(2).longValueExact().also {
                require(it <= 999_999_999_999_999_999L) { "金额超出范围" }
            }
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("金额超出范围")
        }
    }

    fun formatMinor(minor: Long): String = formatMinor(BigInteger.valueOf(minor))

    fun formatMinor(minor: BigInteger): String {
        val format = NumberFormat.getCurrencyInstance(Locale.CHINA)
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        format.roundingMode = RoundingMode.UNNECESSARY
        return format.format(BigDecimal(minor, 2))
    }
}

data class MonthSummary(val incomeMinor: BigInteger, val expenseMinor: BigInteger) {
    val balanceMinor: BigInteger get() = incomeMinor - expenseMinor
}

object FinanceMath {
    fun summarize(transactions: List<TransactionEntity>): MonthSummary = MonthSummary(
        incomeMinor = transactions.filter { it.type == "income" }.fold(BigInteger.ZERO) { total, item -> total + BigInteger.valueOf(item.amountMinor) },
        expenseMinor = transactions.filter { it.type == "expense" }.fold(BigInteger.ZERO) { total, item -> total + BigInteger.valueOf(item.amountMinor) },
    )

    fun accountBalance(account: AccountEntity, transactions: List<TransactionEntity>): BigInteger =
        transactions.filter { it.accountId == account.id }.fold(BigInteger.valueOf(account.openingBalanceMinor)) { total, item ->
            total + BigInteger.valueOf(if (item.type == "income") item.amountMinor else -item.amountMinor)
        }
}
