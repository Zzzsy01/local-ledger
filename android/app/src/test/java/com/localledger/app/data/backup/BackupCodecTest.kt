package com.localledger.app.data.backup

import com.localledger.app.domain.Asset
import com.localledger.app.domain.Account
import com.localledger.app.domain.Category
import com.localledger.app.domain.LedgerSnapshot
import com.localledger.app.domain.Transaction
import com.localledger.app.domain.Memo
import com.localledger.app.domain.Budget
import com.localledger.app.domain.RecurringRule
import com.localledger.app.domain.validateSnapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val expense = "00000000-0000-4000-8000-000000000001"
    private val income = "00000000-0000-4000-8000-000000000002"
    private val account = "00000000-0000-4000-8000-000000000003"
    private val retiredAccount = "00000000-0000-4000-8000-000000000004"
    private val transactionId = "00000000-0000-4000-8000-000000000005"

    private fun snapshot() = LedgerSnapshot(
        categories = listOf(
            Category(expense, "餐饮", 0, icon = "food", sortOrder = 2, isDeleted = true),
            Category(income, "收入", 1),
        ),
        accounts = listOf(Account(account, "现金"), Account(retiredAccount, "旧账户", isDeleted = true)),
        transactions = listOf(Transaction(
            id = transactionId, amountMinor = 9_007_199_254_740_993L, type = 0,
            categoryId = expense, accountId = retiredAccount, note = "历史账目\n保留原备注",
            occurredAt = 1234L, createdAt = 1000L, updatedAt = 2000L, isDeleted = true, source = 8,
        )),
    )

    @Test
    fun versionThreePreservesAllNewDataAndRejectsInvalidReferences() {
        val original = snapshot().copy(
            transactions = snapshot().transactions.map { it.copy(importKey = "csv-test:one") },
            memos = listOf(Memo("10000000-0000-4000-8000-000000000001", "复盘", "内容", true, true, 10, 20, true)),
            budgets = listOf(Budget("2026-12", 12345, 20)),
            recurringRules = listOf(RecurringRule("20000000-0000-4000-8000-000000000001", "房租", 2000, 0, expense, account, 30, 31, "keep", 10, 20, true)),
        )
        assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
        val old = JSONObject(BackupCodec.encode(snapshot())).put("version", 2).apply { remove("memos"); remove("budgets"); remove("recurringRules") }
        assertEquals(snapshot(), BackupCodec.decode(old.toString()))
        rejects { validateSnapshot(original.copy(recurringRules = original.recurringRules.map { it.copy(type = 1) })) }
        rejects { validateSnapshot(original.copy(budgets = original.budgets + original.budgets)) }
        rejects { validateSnapshot(original.copy(transactions = original.transactions + original.transactions.single().copy(id = "30000000-0000-4000-8000-000000000001"))) }
    }

    @Test
    fun assetsRoundTripAndVersionOneBackupsRemainReadable() {
        val original = snapshot().copy(assets = listOf(Asset(
            "10000000-0000-4000-8000-000000000001", "Camera", "摄影", 30000, 15000, 1234,
            listOf(10000, 20000), "used", 1000, 2000, true,
        )))
        assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
        val legacy = JSONObject(BackupCodec.encode(snapshot())).put("version", 1).apply { remove("assets") }
        assertEquals(snapshot(), BackupCodec.decode(legacy.toString()))
        val damaged = JSONObject(BackupCodec.encode(original))
        damaged.getJSONArray("assets").getJSONObject(0).put("valueMinor", 1)
        rejects { BackupCodec.decode(damaged.toString()) }
    }

    @Test
    fun roundTripPreservesLongPrecisionAndDeletedHistory() {
        val original = snapshot()
        assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
    }

    @Test
    fun activeTotalsCannotOverflowAndIncomeAndExpenseAreIndependent() {
        val original = snapshot()
        val maximum = original.transactions.single().copy(amountMinor = Long.MAX_VALUE, isDeleted = false)
        val next = maximum.copy(id = "00000000-0000-4000-8000-000000000006", amountMinor = 1)
        rejects { validateSnapshot(original.copy(transactions = listOf(maximum, next))) }
        validateSnapshot(original.copy(transactions = listOf(maximum, next.copy(isDeleted = true))))
        val separateIncome = next.copy(type = 1, categoryId = income, amountMinor = Long.MAX_VALUE)
        validateSnapshot(original.copy(transactions = listOf(maximum, separateIncome)))
    }

    @Test
    fun emptyLedgerAndAllRetiredCategoriesRemainRestorable() {
        val original = snapshot().copy(categories = emptyList(), transactions = emptyList())
        assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
        val retired = snapshot().copy(categories = snapshot().categories.map { it.copy(isDeleted = true) })
        assertEquals(retired, BackupCodec.decode(BackupCodec.encode(retired)))
        rejects { validateSnapshot(original.copy(accounts = original.accounts.map { it.copy(isDeleted = true) })) }
    }

    @Test
    fun damagedOrUnsupportedBackupIsRejected() {
        rejects { BackupCodec.decode("{") }
        rejects { BackupCodec.decode(BackupCodec.encode(snapshot()) + " trailing") }
        for (version in listOf<Any>(4, "1", true, 1.5)) {
            val json = JSONObject(BackupCodec.encode(snapshot())).put("version", version)
            rejects { BackupCodec.decode(json.toString()) }
        }
        val incomplete = JSONObject(BackupCodec.encode(snapshot())).apply { remove("accounts") }
        rejects { BackupCodec.decode(incomplete.toString()) }
    }

    @Test
    fun jsonTypesCannotBeCoerced() {
        for ((field, value) in listOf<Pair<String, Any>>(
            "amountMinor" to "12", "amountMinor" to 1.5, "amountMinor" to true,
            "type" to "0", "source" to 2_147_483_648L, "occurredAt" to "1234",
            "isDeleted" to "true", "note" to 42,
        )) {
            val json = JSONObject(BackupCodec.encode(snapshot()))
            json.getJSONArray("transactions").getJSONObject(0).put(field, value)
            rejects { BackupCodec.decode(json.toString()) }
        }
        val fractional = BackupCodec.encode(snapshot()).replace("9007199254740993", "9007199254740993.0")
        rejects { BackupCodec.decode(fractional) }
        val outOfRange = BackupCodec.encode(snapshot()).replace("9007199254740993", "9223372036854775808")
        rejects { BackupCodec.decode(outOfRange) }
    }

    @Test
    fun invalidRelationshipsIdsAndMoneyAreRejected() {
        val original = snapshot()
        val record = original.transactions.single()
        val invalidRecords = listOf(
            record.copy(accountId = transactionId), record.copy(categoryId = transactionId),
            record.copy(type = 1), record.copy(type = 2), record.copy(amountMinor = 0),
            record.copy(amountMinor = -1), record.copy(source = -1), record.copy(occurredAt = -1),
            record.copy(createdAt = -1), record.copy(updatedAt = record.createdAt - 1),
            record.copy(id = "1-1-1-1-1"), record.copy(id = "00000000-0000-4000-8000-00000000000A"),
        )
        invalidRecords.forEach { invalid ->
            rejects { validateSnapshot(original.copy(transactions = listOf(invalid))) }
        }
        rejects { validateSnapshot(original.copy(transactions = listOf(record, record))) }
        rejects { validateSnapshot(original.copy(categories = original.categories + original.categories.first())) }
        rejects { validateSnapshot(original.copy(accounts = original.accounts + original.accounts.first())) }
        rejects { validateSnapshot(original.copy(accounts = listOf(Account(account, "  ")))) }
        rejects { validateSnapshot(original.copy(categories = listOf(Category(expense, " ", 0)))) }
    }

    private fun rejects(action: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { action() }
    }
}
