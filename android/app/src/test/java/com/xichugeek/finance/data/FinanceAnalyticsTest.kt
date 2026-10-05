package com.xichugeek.finance.data

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

class FinanceAnalyticsTest {
    @Test fun monthBoundariesRanksDailyTotalsAndPreviousMonth() {
        fun row(id: Long, date: String, amount: Long, category: Long = 1, type: String = "expense") = TransactionEntity(
            id = id, accountId = 1, categoryId = category, type = type, amountMinor = amount, description = "fictional",
            transactionDate = LedgerDates.encode(LocalDate.parse(date)))
        val items = listOf(row(1, "2026-09-30", 2500), row(2, "2026-10-01", 1), row(3, "2026-10-01", 10),
            row(4, "2026-10-02", 20, 2), row(5, "2026-10-02", 10000000000, 3, "income"), row(6, "2026-11-01", 5000))
        val analysis = FinanceAnalytics.calculate(YearMonth.of(2026, 10), items, listOf(CategoryEntity(1, "餐饮", "expense"), CategoryEntity(2, "交通", "expense")))
        assertEquals(BigInteger.valueOf(31), analysis.summary.expenseMinor)
        assertEquals(BigInteger.valueOf(10000000000), analysis.summary.incomeMinor)
        assertEquals(BigInteger.valueOf(2500), analysis.previous.expenseMinor)
        assertEquals(listOf(BigInteger.valueOf(11), BigInteger.valueOf(20)), analysis.daily.take(2))
        assertEquals("交通", analysis.categories.first().name)
        assertEquals(listOf(4L, 3L, 2L), analysis.largest.map { it.id })
        assertEquals("64.5", FinanceAnalytics.percentage(BigInteger.valueOf(20), BigInteger.valueOf(31)))
    }

    @Test fun emptyMonthHasZeroTotalsAndCompleteCalendar() {
        val analysis = FinanceAnalytics.calculate(YearMonth.of(2024, 2), emptyList(), emptyList())
        assertEquals(29, analysis.daily.size)
        assertEquals(BigInteger.ZERO, analysis.summary.balanceMinor)
        assertTrue(analysis.categories.isEmpty())
        assertEquals("0.0", FinanceAnalytics.percentage(BigInteger.ZERO, BigInteger.ZERO))
    }
}
