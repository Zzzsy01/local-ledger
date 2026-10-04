package com.localledger.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.Duration

class ReportModelsTest {
    @Test fun comparisonsAndAverageStayExactAtLongBoundaries() {
        assertEquals("+25.0%", percentageChange(125, 100))
        assertEquals("-100.0%", percentageChange(0, 100))
        assertNull(percentageChange(100, 0))
        assertEquals("0.0%", percentageChange(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(1L, averageMinor(1, 2))
        assertEquals(Long.MAX_VALUE, averageMinor(Long.MAX_VALUE, 1))
        assertEquals(0L, averageMinor(0, 0))
    }

    @Test fun reportPeriodsUseCalendarWeeksAndLeapMonths() {
        val anchor = LocalDate.of(2024, 2, 29)
        val custom = ReportRange(anchor, anchor)
        assertEquals(ReportRange(LocalDate.of(2024, 2, 26), LocalDate.of(2024, 3, 3)), reportRange(ReportPeriod.WEEK, anchor, custom))
        val leapMonth = reportRange(ReportPeriod.MONTH, anchor, custom)
        assertEquals(29L, leapMonth.days)
        assertEquals(ReportRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)), previousReportRange(ReportPeriod.MONTH, leapMonth))
        assertEquals(ReportRange(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4)),
            reportRange(ReportPeriod.WEEK, LocalDate.of(2026, 1, 1), custom))
        val range = ReportRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10))
        assertEquals(ReportRange(LocalDate.of(2025, 12, 22), LocalDate.of(2025, 12, 31)), previousReportRange(ReportPeriod.CUSTOM, range))
    }

    @Test fun dailyPointsIncludeBothDatesAndEmptyDaysAcrossDst() {
        val zone = ZoneId.of("America/New_York")
        val first = LocalDate.of(2026, 3, 8)
        val range = ReportRange(first, first.plusDays(2))
        val (start, end) = ReportRange(first, first).bounds(zone)
        assertEquals(23L, Duration.ofMillis(end - start).toHours())
        val points = reportPoints(listOf(
            entry(start, 1L), entry(end - 1, 2L), entry(start - 1, 90L),
            entry(range.bounds(zone).second - 1, Long.MAX_VALUE, INCOME),
            entry(range.bounds(zone).second, 90L), entry(start, 90L, deleted = true),
        ), range, zone, monthly = false)
        assertEquals(listOf(first, first.plusDays(1), first.plusDays(2)), points.map { it.date })
        assertEquals(listOf(3L, 0L, 0L), points.map { it.summary.expense })
        assertEquals(Long.MAX_VALUE, points.last().summary.income)
    }

    @Test fun monthlyPointsRespectPartialMonthRange() {
        val zone = ZoneId.of("Asia/Shanghai")
        val range = ReportRange(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 2))
        fun at(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
        val points = reportPoints(listOf(entry(at("2026-01-14"), 100L), entry(at("2026-01-15"), 1L),
            entry(at("2026-03-02"), 2L), entry(at("2026-03-03"), 100L)), range, zone, monthly = true)
        assertEquals(listOf(1L, 0L, 2L), points.map { it.summary.expense })
    }

    @Test(expected = IllegalArgumentException::class)
    fun customRangeRejectsReversedDates() {
        ReportRange(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 3))
    }

    private fun entry(at: Long, amount: Long, type: Int = EXPENSE, deleted: Boolean = false) = LedgerEntry(
        Transaction("$at-$amount-$type-$deleted", amount, type, "category", "account", occurredAt = at,
            createdAt = at, updatedAt = at, isDeleted = deleted), "分类", "账户",
    )
}
