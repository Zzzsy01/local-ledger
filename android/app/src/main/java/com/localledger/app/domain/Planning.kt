package com.localledger.app.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID

data class Budget(val month: String, val amountMinor: Long, val updatedAt: Long)

data class RecurringRule(
    val id: String,
    val name: String,
    val amountMinor: Long,
    val type: Int,
    val categoryId: String,
    val accountId: String,
    val nextDueAt: Long,
    val dayOfMonth: Int,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

fun validateBudget(budget: Budget) {
    require(Regex("[0-9]{4}-[0-9]{2}").matches(budget.month)) { "预算月份无效。" }
    try { require(YearMonth.parse(budget.month).toString() == budget.month) { "预算月份无效。" } }
    catch (error: DateTimeParseException) { throw IllegalArgumentException("预算月份无效。", error) }
    require(budget.amountMinor > 0) { "预算必须大于零。" }
    require(budget.updatedAt >= 0) { "预算更新时间无效。" }
}

fun validateRecurringRule(rule: RecurringRule) {
    require(UUID.fromString(rule.id).toString() == rule.id) { "固定账目 ID 无效。" }
    require(rule.name.isNotBlank()) { "请输入固定账目名称。" }
    require(rule.amountMinor > 0) { "金额必须大于零。" }
    require(rule.type == EXPENSE || rule.type == INCOME) { "收支类型无效。" }
    require(rule.dayOfMonth in 1..31) { "每月日期应在 1 至 31 日之间。" }
    require(rule.nextDueAt >= 0 && rule.createdAt >= 0 && rule.updatedAt >= rule.createdAt) { "固定账目日期无效。" }
}

fun nextRecurringDueAt(dueAt: Long, dayOfMonth: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
    require(dueAt >= 0 && dayOfMonth in 1..31) { "固定账目日期无效。" }
    val nextMonth = YearMonth.from(Instant.ofEpochMilli(dueAt).atZone(zone)).plusMonths(1)
    return nextMonth.atDay(minOf(dayOfMonth, nextMonth.lengthOfMonth())).atStartOfDay(zone).toInstant().toEpochMilli()
}
