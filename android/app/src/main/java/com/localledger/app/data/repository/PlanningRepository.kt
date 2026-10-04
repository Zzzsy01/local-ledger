package com.localledger.app.data.repository

import androidx.room.withTransaction
import com.localledger.app.data.db.LedgerDatabase
import com.localledger.app.data.db.toDomain
import com.localledger.app.data.db.toEntity
import com.localledger.app.domain.Budget
import com.localledger.app.domain.RecurringRule
import com.localledger.app.domain.nextRecurringDueAt
import com.localledger.app.domain.validateBudget
import com.localledger.app.domain.validateRecurringRule
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.map

class PlanningRepository(private val db: LedgerDatabase, private val ledger: LedgerRepository) {
    private val dao = db.planningDao()
    val rules = dao.observeRules().map { rows -> rows.map { it.toDomain() } }
    fun budget(month: String) = dao.observeBudget(month).map { it?.toDomain() }

    suspend fun saveBudget(month: String, amountMinor: Long) {
        val budget = Budget(month, amountMinor, System.currentTimeMillis())
        validateBudget(budget)
        dao.saveBudget(budget.toEntity())
    }

    suspend fun clearBudget(month: String) { dao.deleteBudget(month) }

    suspend fun saveRule(id: String?, name: String, amountMinor: Long, type: Int, categoryId: String,
        accountId: String, nextDueAt: Long, dayOfMonth: Int, note: String?) = db.withTransaction {
        val existing = id?.let { requireNotNull(dao.rule(it)) { "此固定账目已不存在。" } }
        require(existing?.isDeleted != true) { "此固定账目已停用。" }
        val category = requireNotNull(db.ledgerDao().category(categoryId)) { "请选择有效分类。" }
        val account = requireNotNull(db.ledgerDao().account(accountId)) { "请选择有效账户。" }
        require(category.type == type) { "分类与收支类型不一致。" }
        require(!category.isDeleted) { "此分类已停用，请选择其他分类。" }
        require(!account.isDeleted) { "此账户已停用，请选择其他账户。" }
        val now = System.currentTimeMillis()
        val rule = RecurringRule(existing?.id ?: UUID.randomUUID().toString(), name.trim(), amountMinor, type,
            categoryId, accountId, nextDueAt, dayOfMonth, note?.trim()?.takeIf { it.isNotEmpty() },
            existing?.createdAt ?: now, maxOf(now, existing?.updatedAt ?: now, existing?.createdAt ?: now))
        validateRecurringRule(rule)
        dao.saveRule(rule.toEntity())
    }

    suspend fun disableRule(id: String) = db.withTransaction {
        val rule = requireNotNull(dao.rule(id)) { "此固定账目已不存在。" }
        dao.saveRule(rule.copy(isDeleted = true, updatedAt = maxOf(System.currentTimeMillis(), rule.updatedAt, rule.createdAt)))
    }

    suspend fun processRule(id: String, expectedDueAt: Long, record: Boolean) = db.withTransaction {
        val rule = requireNotNull(dao.rule(id)) { "此固定账目已不存在。" }
        require(!rule.isDeleted) { "此固定账目已停用。" }
        require(rule.nextDueAt == expectedDueAt) { "本期已处理，请刷新后查看下期。" }
        val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        require(rule.nextDueAt < todayEnd) { "尚未到期。" }
        val next = nextRecurringDueAt(rule.nextDueAt, rule.dayOfMonth)
        if (record) ledger.saveTransaction(null, rule.amountMinor, rule.type, rule.categoryId,
            rule.accountId, rule.note ?: rule.name, rule.nextDueAt)
        dao.saveRule(rule.copy(nextDueAt = next, updatedAt = maxOf(System.currentTimeMillis(), rule.updatedAt, rule.createdAt)))
    }

    suspend fun dueRules(): List<RecurringRule> {
        val end = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return dao.dueRules(end).map { it.toDomain() }
    }
}
