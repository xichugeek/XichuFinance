package com.xichugeek.finance.data

import java.time.LocalDate

/** A transaction is a calendar date, not an event timestamp. UTC day encoding
 * keeps the chosen date stable when the device changes time zone. */
object LedgerDates {
    private const val MILLIS_PER_DAY = 86_400_000L
    fun encode(date: LocalDate): Long {
        require(date.year in 1..9999) { "日期年份应为 1–9999" }
        return date.toEpochDay() * MILLIS_PER_DAY
    }
    fun decode(encoded: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(encoded, MILLIS_PER_DAY))
}
