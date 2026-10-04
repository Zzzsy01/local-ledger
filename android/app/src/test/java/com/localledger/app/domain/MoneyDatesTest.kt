package com.localledger.app.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyDatesTest {
    @Test fun parsesExactPositiveMinorUnitsAndRejectsInvalidMoney() {
        assertEquals(1L, parseAmount("0.01"))
        assertEquals(1230L, parseAmount(" 12.3 "))
        assertEquals(100L, parseAmount("1"))
        listOf("", "0", "0.00", "-1", "+1", ".5", "1.", "1.234", "1e2", "1,000", "NaN").forEach {
            assertNull("unexpected amount: $it", parseAmount(it))
        }
    }

    @Test fun handlesLongBoundariesWithoutFloatingPointOrOverflow() {
        assertEquals(Long.MAX_VALUE, parseAmount("92233720368547758.07"))
        assertNull(parseAmount("92233720368547758.08"))
        assertNull(parseAmount("9223372036854775808"))
        assertEquals("92233720368547758.07", formatAmount(Long.MAX_VALUE))
        assertEquals("-92233720368547758.08", formatAmount(Long.MIN_VALUE))
        assertEquals("-0.01", formatAmount(-1))
        assertEquals("0.00", formatAmount(0))
    }

    @Test fun monthBoundsFollowLocalCalendarIncludingLeapDay() {
        val (start, end) = monthBounds(YearMonth.of(2024, 2), ZoneId.of("Asia/Shanghai"))
        assertEquals(Instant.parse("2024-01-31T16:00:00Z").toEpochMilli(), start)
        assertEquals(Instant.parse("2024-02-29T16:00:00Z").toEpochMilli(), end)
    }

    @Test fun monthBoundsFollowDaylightSavingInsteadOfFixedDayDurations() {
        val (start, end) = monthBounds(YearMonth.of(2026, 3), ZoneId.of("America/New_York"))
        assertEquals(Instant.parse("2026-03-01T05:00:00Z").toEpochMilli(), start)
        assertEquals(Instant.parse("2026-04-01T04:00:00Z").toEpochMilli(), end)
        assertEquals((31 * 24 - 1) * 60 * 60 * 1000L, end - start)
    }
}
