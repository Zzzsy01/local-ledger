package com.localledger.app.domain

import java.time.YearMonth
import java.time.ZoneId

const val EXPENSE = 0
const val INCOME = 1

private val amountPattern = Regex("^[0-9]+(?:\\.[0-9]{1,2})?$")

fun parseAmount(text: String, allowZero: Boolean = false): Long? {
    val value = text.trim()
    if (!amountPattern.matches(value)) return null
    val parts = value.split('.')
    val whole = parts[0].toLongOrNull() ?: return null
    val fraction = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0L
    if (whole > (Long.MAX_VALUE - fraction) / 100) return null
    return (whole * 100 + fraction).takeIf { it > 0 || (allowZero && it == 0L) }
}

fun formatAmount(minor: Long): String {
    val whole = minor / 100
    val fraction = kotlin.math.abs(minor % 100).toString().padStart(2, '0')
    val sign = if (minor < 0 && whole == 0L) "-" else ""
    return "$sign$whole.$fraction"
}

fun monthBounds(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
    month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
