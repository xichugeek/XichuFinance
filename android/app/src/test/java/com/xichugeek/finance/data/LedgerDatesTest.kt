package com.xichugeek.finance.data

import java.time.LocalDate
import java.time.YearMonth
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class LedgerDatesTest {
    @Test fun dateAndMonthStayStableAcrossTimeZones() {
        val old = TimeZone.getDefault()
        try {
            val day = LocalDate.of(2026, 10, 1)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val encoded = LedgerDates.encode(day)
            for (zone in listOf("America/Los_Angeles", "Pacific/Kiritimati", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals(day, LedgerDates.decode(encoded))
                assertEquals(YearMonth.of(2026, 10), FinanceAnalytics.monthOf(encoded))
            }
            assertEquals(LocalDate.of(1960, 1, 1), LedgerDates.decode(LedgerDates.encode(LocalDate.of(1960, 1, 1))))
        } finally { TimeZone.setDefault(old) }
    }
}
