package com.localledger.app.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["transferAccountId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("categoryId"), Index("accountId"), Index("transferAccountId"), Index(value = ["isDeleted", "occurredAt"]), Index(value = ["importKey"], unique = true)],
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
    val transferAccountId: String? = null,
    val merchant: String? = null,
    val location: String? = null,
    @ColumnInfo(defaultValue = "0") val isReimbursable: Boolean = false,
    @ColumnInfo(defaultValue = "0") val reimbursementStatus: Int = 0,
)
