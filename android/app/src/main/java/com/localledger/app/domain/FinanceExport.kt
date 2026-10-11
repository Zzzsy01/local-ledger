package com.localledger.app.domain

import java.time.ZoneId

data class FinanceExport(
    val range: ReportRange, val exportedAt: Long, val zone: ZoneId,
    val categories: List<Category>, val accounts: List<Account>, val transactions: List<Transaction>,
    val budgets: List<Budget>, val recurringRules: List<RecurringRule>,
) {
    val entries: List<Transaction> get() {
        val (start, end) = range.bounds(zone)
        return transactions.filter { !it.isDeleted && it.occurredAt >= start && it.occurredAt < end }
            .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenBy { it.id })
    }
}
