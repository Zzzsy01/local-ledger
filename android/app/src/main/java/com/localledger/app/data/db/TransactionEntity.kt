package com.localledger.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("categoryId"), Index("accountId"), Index(value = ["isDeleted", "occurredAt"]), Index(value = ["importKey"], unique = true)],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amountMinor: Long,
    val type: Int,
    val categoryId: String,
    val accountId: String,
    val note: String? = null,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val source: Int = 0,
    val importKey: String? = null,
)
