package com.localledger.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.TypeConverters
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction

@Database(entities = [CategoryEntity::class, AccountEntity::class, TransactionEntity::class, AssetEntity::class, MemoEntity::class, BudgetEntity::class, RecurringRuleEntity::class], version = 3, exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)])
@TypeConverters(AssetConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun assetDao(): AssetDao
    abstract fun memoDao(): MemoDao
    abstract fun planningDao(): PlanningDao

    suspend fun seedDefaults() = withTransaction {
        val dao = ledgerDao()
        if (dao.totalRowCount() != 0L) return@withTransaction
        dao.insertAccounts(listOf(
            AccountEntity("00000000-0000-4000-8000-000000000001", "现金"),
            AccountEntity("00000000-0000-4000-8000-000000000002", "微信"),
            AccountEntity("00000000-0000-4000-8000-000000000003", "支付宝"),
            AccountEntity("00000000-0000-4000-8000-000000000004", "银行卡"),
        ))
        val expenseNames = listOf("餐饮", "交通", "购物", "日用", "住房", "医疗", "娱乐", "其他")
        val incomeNames = listOf("工资", "奖金", "理财", "其他")
        dao.insertCategories(expenseNames.mapIndexed { index, name ->
            CategoryEntity("10000000-0000-4000-8000-${(index + 1).toString().padStart(12, '0')}", name, 0, sortOrder = index)
        } + incomeNames.mapIndexed { index, name ->
            CategoryEntity("20000000-0000-4000-8000-${(index + 1).toString().padStart(12, '0')}", name, 1, sortOrder = index)
        })
    }

    companion object {
        fun create(context: Context): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, "ledger.db").build()
    }
}
