package com.localledger.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.TypeConverters
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localledger.app.domain.TRANSFER_CATEGORY_ID

class LifeMigration : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO categories (id,name,type,icon,sortOrder,isDeleted) VALUES (?, '转账', 2, NULL, 0, 0)", arrayOf(TRANSFER_CATEGORY_ID))
    }
}

@Database(entities = [CategoryEntity::class, AccountEntity::class, TransactionEntity::class, AssetEntity::class, MemoEntity::class, BudgetEntity::class, RecurringRuleEntity::class, WishEntity::class, LifeItemEntity::class, LifeCheckInEntity::class, FocusSessionEntity::class, StudyCardEntity::class, PaymentCandidateEntity::class, AssetRecordEntity::class], version = 6, exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5, spec = LifeMigration::class), AutoMigration(from = 5, to = 6)])
@TypeConverters(AssetConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun assetDao(): AssetDao
    abstract fun memoDao(): MemoDao
    abstract fun planningDao(): PlanningDao
    abstract fun wishDao(): WishDao
    abstract fun lifeDao(): LifeDao
    abstract fun paymentCandidateDao(): PaymentCandidateDao

    suspend fun seedDefaults() = withTransaction {
        val dao = ledgerDao()
        if (dao.totalRowCount() != 0L) {
            if (dao.category(TRANSFER_CATEGORY_ID) == null) dao.insertCategories(listOf(CategoryEntity(TRANSFER_CATEGORY_ID, "转账", 2)))
            return@withTransaction
        }
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
        } + CategoryEntity(TRANSFER_CATEGORY_ID, "转账", 2))
    }

    companion object {
        fun create(context: Context): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, "ledger.db").build()
    }
}
