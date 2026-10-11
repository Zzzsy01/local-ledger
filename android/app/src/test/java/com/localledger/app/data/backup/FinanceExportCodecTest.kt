package com.localledger.app.data.backup

import com.localledger.app.domain.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FinanceExportCodecTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val account = Account(UUID.randomUUID().toString(), "现金", initialBalanceMinor = 1000)
    private val target = Account(UUID.randomUUID().toString(), "旧账户", isDeleted = true)
    private val expense = Category(UUID.randomUUID().toString(), "餐饮", EXPENSE)
    private val income = Category(UUID.randomUUID().toString(), "收入", INCOME)
    private fun at(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun row(date: String, amount: Long, type: Int = EXPENSE) = Transaction(UUID.randomUUID().toString(), amount, type,
        if (type == INCOME) income.id else if (type == TRANSFER) TRANSFER_CATEGORY_ID else expense.id, account.id,
        occurredAt = at(date), createdAt = at(date), updatedAt = at(date), transferAccountId = target.id.takeIf { type == TRANSFER })
    private fun data() = FinanceExport(ReportRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 9)), at("2026-02-10"), zone,
        listOf(expense, income, Category(TRANSFER_CATEGORY_ID, "转账", TRANSFER)), listOf(account, target),
        listOf(row("2026-01-10", 2000, INCOME), row("2026-02-01", 300, TRANSFER), row("2026-02-09", 100),
            row("2026-02-09", 200).copy(source = 2, note = "支付通知原文"), row("2026-02-05", 999).copy(isDeleted = true), row("2026-03-01", 500, INCOME)),
        listOf(Budget("2026-02", 10_000, 0), Budget("2026-03", 10_000, 0)), emptyList())

    @Test fun inclusiveRangeAndBalancesUseDifferentScopesAndTransfersDoNotInflateSpending() {
        val json = JSONObject(FinanceExportCodec.encode(data()))
        val summary = json.getJSONObject("summary")
        assertEquals(3, summary.getInt("transactionCount"))
        assertEquals(1, summary.getInt("transferCount"))
        assertEquals(300L, summary.getLong("expenseMinor"))
        assertEquals(0L, summary.getLong("incomeMinor"))
        assertEquals(2, summary.getInt("daysWithRecords"))
        assertEquals(2900L, json.getJSONArray("accounts").getJSONObject(0).getLong("ledgerBalanceMinor"))
        assertEquals(300L, json.getJSONArray("accounts").getJSONObject(1).getLong("ledgerBalanceMinor"))
        assertEquals(1, json.getJSONArray("budgets").length())
        assertFalse(json.toString().contains("支付通知原文"))
        assertFalse(json.has("memos"))
        assertFalse(json.has("assetPhotos"))
        assertEquals("Asia/Shanghai", json.getString("timeZone"))
    }

    @Test fun csvEscapesMultilineTextAndNeutralizesSpreadsheetFormulas() {
        val source = data().copy(transactions = listOf(row("2026-02-09", 125).copy(merchant = "=SUM(1,2)",
            location = " \t@cmd", note = "文字,\"引号\"\n第二行")))
        val csv = FinanceExportCodec.csv(source)
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"'=SUM(1,2)\""))
        assertTrue(csv.contains("\"' \t@cmd\""))
        assertTrue(csv.contains("\"文字,\"\"引号\"\"\n第二行\""))
        assertTrue(csv.contains("\"1.25\",\"125\""))
    }

    @Test fun largeIntegerBalancesStayExactAndAnalysisCannotRestore() {
        val source = data().copy(accounts = listOf(account.copy(initialBalanceMinor = 9_007_199_254_740_993L)), transactions = emptyList())
        val text = FinanceExportCodec.encode(source)
        assertEquals(9_007_199_254_740_993L, JSONObject(text).getJSONArray("accounts").getJSONObject(0).getLong("ledgerBalanceMinor"))
        val error = assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(text) }
        assertTrue(error.message.orEmpty().contains("财务分析文件"))
    }
}
