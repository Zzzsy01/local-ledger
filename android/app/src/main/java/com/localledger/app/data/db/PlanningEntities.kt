package com.localledger.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.localledger.app.domain.Budget
import com.localledger.app.domain.RecurringRule

@Entity(tableName = "budgets")
data class BudgetEntity(@PrimaryKey val month: String, val amountMinor: Long, val updatedAt: Long)

@Entity(tableName = "recurring_rules", foreignKeys = [
    ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"]),
    ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"]),
], indices = [Index("categoryId"), Index("accountId"), Index("nextDueAt")])
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
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

internal fun BudgetEntity.toDomain() = Budget(month, amountMinor, updatedAt)
internal fun Budget.toEntity() = BudgetEntity(month, amountMinor, updatedAt)
internal fun RecurringRuleEntity.toDomain() = RecurringRule(id, name, amountMinor, type, categoryId, accountId, nextDueAt, dayOfMonth, note, createdAt, updatedAt, isDeleted)
internal fun RecurringRule.toEntity() = RecurringRuleEntity(id, name, amountMinor, type, categoryId, accountId, nextDueAt, dayOfMonth, note, createdAt, updatedAt, isDeleted)
