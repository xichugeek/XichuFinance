package com.xichugeek.finance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test
    fun parsesExactMinorUnits() {
        assertEquals(1L, Money.parseMinor("0.01"))
        assertEquals(10L, Money.parseMinor("0.1"))
        assertEquals(20L, Money.parseMinor("0.2"))
        assertEquals(123456789012L, Money.parseMinor("1234567890.12"))
    }

    @Test
    fun rejectsInvalidMoney() {
        listOf("", "abc", "0", "-1", "1.001").forEach {
            assertThrows(IllegalArgumentException::class.java) { Money.parseMinor(it) }
        }
    }

    @Test
    fun totalsDoNotUseFloatingPoint() {
        val values = listOf(
            TransactionEntity(accountId = 1, categoryId = 1, type = "income", amountMinor = 100, description = "income", transactionDate = 0),
            TransactionEntity(accountId = 1, categoryId = 2, type = "expense", amountMinor = Money.parseMinor("0.1"), description = "a", transactionDate = 0),
            TransactionEntity(accountId = 1, categoryId = 2, type = "expense", amountMinor = Money.parseMinor("0.2"), description = "b", transactionDate = 0),
        )
        val summary = FinanceMath.summarize(values)
        assertEquals(100L, summary.incomeMinor)
        assertEquals(30L, summary.expenseMinor)
        assertEquals(70L, summary.balanceMinor)
        assertEquals(70L, FinanceMath.accountBalance(AccountEntity(id = 1, name = "test", kind = "cash"), values))
    }
}
