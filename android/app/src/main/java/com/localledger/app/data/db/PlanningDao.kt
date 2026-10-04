package com.localledger.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanningDao {
    @Query("SELECT * FROM budgets WHERE month = :month")
    fun observeBudget(month: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM recurring_rules WHERE isDeleted = 0 ORDER BY nextDueAt, name, id")
    fun observeRules(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun rule(id: String): RecurringRuleEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM recurring_rules WHERE categoryId = :id)")
    suspend fun categoryHasRules(id: String): Boolean

    @Query("SELECT * FROM recurring_rules WHERE isDeleted = 0 AND nextDueAt < :end ORDER BY nextDueAt, id")
    suspend fun dueRules(end: Long): List<RecurringRuleEntity>

    @Query("SELECT * FROM budgets ORDER BY month")
    suspend fun allBudgets(): List<BudgetEntity>

    @Query("SELECT * FROM recurring_rules ORDER BY id")
    suspend fun allRules(): List<RecurringRuleEntity>

    @Upsert
    suspend fun saveBudget(budget: BudgetEntity)

    @Upsert
    suspend fun saveRule(rule: RecurringRuleEntity)

    @Query("DELETE FROM budgets WHERE month = :month")
    suspend fun deleteBudget(month: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBudgets(budgets: List<BudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRules(rules: List<RecurringRuleEntity>)

    @Query("DELETE FROM budgets")
    suspend fun clearBudgets()

    @Query("DELETE FROM recurring_rules")
    suspend fun clearRules()
}
