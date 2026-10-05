package com.xichugeek.finance.data

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CategoryTotal(val id: Long, val name: String, val amountMinor: BigInteger)
data class MonthlyAnalysis(
    val month: YearMonth, val summary: MonthSummary, val previous: MonthSummary,
    val daily: List<BigInteger>, val categories: List<CategoryTotal>,
    val largest: List<TransactionEntity>, val recent: List<TransactionEntity>,
)

object FinanceAnalytics {
    fun monthOf(timestamp: Long): YearMonth = YearMonth.from(LocalDate.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))

    fun calculate(month: YearMonth, transactions: List<TransactionEntity>, categories: List<CategoryEntity>): MonthlyAnalysis {
        val current = transactions.filter { monthOf(it.transactionDate) == month }
        val previous = transactions.filter { monthOf(it.transactionDate) == month.minusMonths(1) }
        val expenses = current.filter { it.type == "expense" }
        val daily = (1..month.lengthOfMonth()).map { day ->
            expenses.filter { LocalDate.ofInstant(Instant.ofEpochMilli(it.transactionDate), ZoneId.systemDefault()).dayOfMonth == day }
                .fold(BigInteger.ZERO) { total, row -> total + BigInteger.valueOf(row.amountMinor) }
        }
        val names = categories.associate { it.id to it.name }
        val ranked = expenses.groupBy { it.categoryId }.map { (id, rows) ->
            CategoryTotal(id, names[id] ?: "未分类", rows.fold(BigInteger.ZERO) { total, row -> total + BigInteger.valueOf(row.amountMinor) })
        }.sortedWith(compareByDescending<CategoryTotal> { it.amountMinor }.thenBy { it.id })
        val ordered = current.sortedWith(compareByDescending<TransactionEntity> { it.transactionDate }.thenByDescending { it.id })
        return MonthlyAnalysis(month, FinanceMath.summarize(current), FinanceMath.summarize(previous), daily, ranked,
            expenses.sortedWith(compareByDescending<TransactionEntity> { it.amountMinor }.thenByDescending { it.transactionDate }.thenByDescending { it.id }).take(5),
            ordered.take(5))
    }

    fun percentage(amount: BigInteger, total: BigInteger): String = if (total.signum() == 0) "0.0"
    else BigDecimal(amount).multiply(BigDecimal(100)).divide(BigDecimal(total), 1, RoundingMode.HALF_UP).toPlainString()

    // Floating-point values are used only as drawing coordinates, never as money totals.
    fun fraction(amount: BigInteger, total: BigInteger): Float = if (total.signum() == 0) 0f
    else BigDecimal(amount).divide(BigDecimal(total), 8, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
}
