package com.localledger.app.data.backup

import com.localledger.app.domain.*
import java.time.Instant
import java.time.YearMonth
import org.json.JSONArray
import org.json.JSONObject

object FinanceExportCodec {
    const val FORMAT = "local-ledger-finance-analysis"

    fun encode(data: FinanceExport): String {
        val entries = data.entries
        val balances = accountBalances(data.accounts, data.transactions).associateBy { it.account.id }
        val income = entries.filter { it.type == INCOME }.sumOf { it.amountMinor }
        val expense = entries.filter { it.type == EXPENSE }.sumOf { it.amountMinor }
        val summary = JSONObject().put("incomeMinor", income).put("expenseMinor", expense).put("netMinor", income - expense)
            .put("transactionCount", entries.size).put("incomeCount", entries.count { it.type == INCOME })
            .put("expenseCount", entries.count { it.type == EXPENSE }).put("transferCount", entries.count { it.type == TRANSFER })
            .put("daysWithRecords", entries.map { Instant.ofEpochMilli(it.occurredAt).atZone(data.zone).toLocalDate() }.distinct().size)
        val root = JSONObject().put("format", FORMAT).put("schemaVersion", 1)
            .put("exportedAt", data.exportedAt).put("timeZone", data.zone.id).put("currency", "CNY").put("amountUnit", "integer minor units; 100 = 1 yuan")
            .put("fromDate", data.range.from.toString()).put("throughDateInclusive", data.range.through.toString())
            .put("summary", summary)
            .put("definitions", JSONObject()
                .put("transactionTypes", "0=expense, 1=income, 2=transfer; transfers excluded from income, expense and budgets")
                .put("timestamps", "Unix milliseconds; occurredAt is transaction date, createdAt is actual entry creation time")
                .put("recordCompleteness", "daysWithRecords is not a completeness claim; unrecorded transactions are unknown")
                .put("accountBalances", "initialBalanceMinor + all active ledger income - expense + transfers; includes future-dated records; not verified bank balances")
                .put("initialBalanceDate", "not recorded; do not assume month-start or export-start")
                .put("reimbursement", "status is only a marker; no automatic deduction or income matching")
                .put("privacy", "No life records, photos, deleted transactions or payment candidates. Notes from capture-source transactions are omitted to avoid notification/OCR text.")
                .put("restore", "Analysis only; cannot restore the application from this file"))
        root.put("categories", JSONArray(data.categories.map { JSONObject().put("id", it.id).put("name", it.name).put("type", it.type).put("isDeleted", it.isDeleted) }))
        root.put("accounts", JSONArray(data.accounts.map { JSONObject().put("id", it.id).put("name", it.name).put("isDeleted", it.isDeleted)
            .put("initialBalanceMinor", it.initialBalanceMinor).put("ledgerBalanceMinor", balances.getValue(it.id).balanceMinor) }))
        root.put("transactions", JSONArray(entries.map { row -> JSONObject().put("id", row.id).put("amountMinor", row.amountMinor)
            .put("type", row.type).put("categoryId", row.categoryId).put("accountId", row.accountId)
            .put("transferAccountId", row.transferAccountId ?: JSONObject.NULL).put("merchant", row.merchant ?: JSONObject.NULL)
            .put("location", row.location ?: JSONObject.NULL).put("note", safeNote(row) ?: JSONObject.NULL)
            .put("isReimbursable", row.isReimbursable).put("reimbursementStatus", row.reimbursementStatus)
            .put("occurredAt", row.occurredAt).put("createdAt", row.createdAt).put("updatedAt", row.updatedAt).put("source", row.source) }))
        root.put("budgets", JSONArray(data.budgets.filter { YearMonth.parse(it.month) in YearMonth.from(data.range.from)..YearMonth.from(data.range.through) }
            .map { JSONObject().put("month", it.month).put("amountMinor", it.amountMinor) }))
        root.put("recurringRules", JSONArray(data.recurringRules.filterNot { it.isDeleted }.map { JSONObject().put("id", it.id).put("name", it.name)
            .put("amountMinor", it.amountMinor).put("type", it.type).put("categoryId", it.categoryId).put("accountId", it.accountId)
            .put("nextDueAt", it.nextDueAt).put("dayOfMonth", it.dayOfMonth).put("note", it.note ?: JSONObject.NULL) }))
        return root.toString(2)
    }

    fun csv(data: FinanceExport): String {
        val categories = data.categories.associateBy { it.id }
        val accounts = data.accounts.associateBy { it.id }
        val lines = sequenceOf(listOf("记录ID", "类型", "发生时间", "金额（元）", "金额（分）", "分类", "账户", "转入账户", "商家或用途", "地点", "备注", "报销状态", "录入时间", "更新时间")) +
            data.entries.asSequence().map { row -> listOf(row.id, when (row.type) { INCOME -> "收入"; TRANSFER -> "转账"; else -> "支出" },
                Instant.ofEpochMilli(row.occurredAt).atZone(data.zone).toOffsetDateTime().toString(), formatAmount(row.amountMinor), row.amountMinor.toString(),
                categories.getValue(row.categoryId).name, accounts.getValue(row.accountId).name, row.transferAccountId?.let { accounts.getValue(it).name }.orEmpty(),
                row.merchant.orEmpty(), row.location.orEmpty(), safeNote(row).orEmpty(),
                if (!row.isReimbursable) "" else if (row.reimbursementStatus == 1) "已报销" else "待报销",
                Instant.ofEpochMilli(row.createdAt).atZone(data.zone).toOffsetDateTime().toString(),
                Instant.ofEpochMilli(row.updatedAt).atZone(data.zone).toOffsetDateTime().toString()) }
        return "\uFEFF" + lines.joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",", transform = ::csvCell) }
    }

    private fun safeNote(row: Transaction): String? = row.note.takeUnless { row.source == 2 }

    private fun csvCell(value: String): String {
        val first = value.trimStart { it.isWhitespace() || it == '\uFEFF' }.firstOrNull()
        val safe = if (first in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
