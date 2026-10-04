package com.localledger.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class LedgerFilter(
    val start: Long = 0,
    val end: Long = Long.MAX_VALUE,
    val keyword: String = "",
    val type: Int? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val minimum: Long? = null,
    val maximum: Long? = null,
    val reimbursement: Int? = null,
)

data class AccountTotal(val accountId: String, val name: String, val total: Long)
data class ReportMetrics(val transactionCount: Long = 0, val expenseCount: Long = 0, val largestExpense: Long = 0)

fun percentageChange(current: Long, previous: Long): String? = if (previous <= 0) null else {
    val change = BigDecimal.valueOf(current).subtract(BigDecimal.valueOf(previous))
        .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP)
    (if (change.signum() > 0) "+" else "") + change.toPlainString() + "%"
}

fun averageMinor(total: Long, count: Long): Long = if (count <= 0) 0 else
    BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).toLong()

enum class ReportPeriod(val label: String) { WEEK("周报"), MONTH("月报"), YEAR("年报"), CUSTOM("自定义") }

data class ReportRange(val from: LocalDate, val through: LocalDate) {
    init { require(!through.isBefore(from)) { "结束日期不能早于开始日期。" } }
    val days: Long get() = ChronoUnit.DAYS.between(from, through) + 1
    fun bounds(zone: ZoneId): Pair<Long, Long> =
        from.atStartOfDay(zone).toInstant().toEpochMilli() to through.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

fun reportRange(period: ReportPeriod, anchor: LocalDate, custom: ReportRange): ReportRange = when (period) {
    ReportPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { ReportRange(it, it.plusDays(6)) }
    ReportPeriod.MONTH -> YearMonth.from(anchor).let { ReportRange(it.atDay(1), it.atEndOfMonth()) }
    ReportPeriod.YEAR -> ReportRange(LocalDate.of(anchor.year, 1, 1), LocalDate.of(anchor.year, 12, 31))
    ReportPeriod.CUSTOM -> custom
}

fun previousReportRange(period: ReportPeriod, range: ReportRange): ReportRange = when (period) {
    ReportPeriod.MONTH -> YearMonth.from(range.from).minusMonths(1).let { ReportRange(it.atDay(1), it.atEndOfMonth()) }
    ReportPeriod.YEAR -> ReportRange(range.from.minusYears(1), range.through.minusYears(1))
    else -> ReportRange(range.from.minusDays(range.days), range.from.minusDays(1))
}

data class ReportPoint(val date: LocalDate, val summary: MonthlySummary)

// Group by local calendar dates, never by a fixed 24-hour duration (DST can shorten or lengthen a day).
fun reportPoints(entries: List<LedgerEntry>, range: ReportRange, zone: ZoneId, monthly: Boolean): List<ReportPoint> {
    val matching = entries.filter {
        val date = Instant.ofEpochMilli(it.transaction.occurredAt).atZone(zone).toLocalDate()
        !it.transaction.isDeleted && !date.isBefore(range.from) && !date.isAfter(range.through)
    }.groupBy {
        val date = Instant.ofEpochMilli(it.transaction.occurredAt).atZone(zone).toLocalDate()
        if (monthly) date.withDayOfMonth(1) else date
    }
    val first = if (monthly) range.from.withDayOfMonth(1) else range.from
    val last = if (monthly) range.through.withDayOfMonth(1) else range.through
    return generateSequence(first) { if (monthly) it.plusMonths(1) else it.plusDays(1) }
        .takeWhile { !it.isAfter(last) }.map { date ->
            val rows = matching[date].orEmpty()
            ReportPoint(date, MonthlySummary(
                rows.filter { it.transaction.type == INCOME }.sumOf { it.transaction.amountMinor },
                rows.filter { it.transaction.type == EXPENSE }.sumOf { it.transaction.amountMinor },
            ))
        }.toList()
}
