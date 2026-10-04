package com.localledger.app.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlanningTest {
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
