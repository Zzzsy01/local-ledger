package com.localledger.app.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlanningTest {
    @Test fun budgetPaceIncludesTodayRoundsDownAndHandlesOtherMonths() {
        val month = YearMonth.of(2026, 10)
        val pace = budgetPace(month, 100_000, 30_001, LocalDate.of(2026, 10, 12))
        assertEquals(20, pace.remainingDays)
        assertEquals(12, pace.elapsedDays)
        assertEquals(3499L, pace.dailyMinor)
        assertEquals(69999L, pace.remainingMinor)
        assertEquals(69999L, budgetPace(month, 100_000, 30_001, month.atEndOfMonth()).dailyMinor)
        assertEquals(null, budgetPace(month, 100, 101, month.atDay(1)).dailyMinor)
        assertEquals(null, budgetPace(month, 100, 0, LocalDate.of(2026, 11, 1)).dailyMinor)
        assertEquals(0, budgetPace(month, 100, 0, LocalDate.of(2026, 9, 30)).elapsedDays)
        assertEquals(1, budgetPace(YearMonth.of(2024, 2), 100, 0, LocalDate.of(2024, 2, 29)).remainingDays)
        assertThrows(IllegalArgumentException::class.java) { budgetPace(month, 0, 0, month.atDay(1)) }
    }
    @Test fun monthlyDueDatesKeepTargetDayAcrossFebruaryAndYearEnd() {
        val zone = ZoneId.of("Asia/Shanghai")
        fun at(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(at("2024-02-29"), nextRecurringDueAt(at("2024-01-31"), 31, zone))
        assertEquals(at("2024-03-31"), nextRecurringDueAt(at("2024-02-29"), 31, zone))
        assertEquals(at("2025-02-28"), nextRecurringDueAt(at("2025-01-30"), 30, zone))
        assertEquals(at("2026-01-31"), nextRecurringDueAt(at("2025-12-31"), 31, zone))
        assertThrows(IllegalArgumentException::class.java) { nextRecurringDueAt(at("2025-01-01"), 0, zone) }
        assertThrows(IllegalArgumentException::class.java) { validateBudget(Budget("2025-13", 100, 0)) }
        assertThrows(IllegalArgumentException::class.java) { validateBudget(Budget("2025-01", 0, 0)) }
    }
}
