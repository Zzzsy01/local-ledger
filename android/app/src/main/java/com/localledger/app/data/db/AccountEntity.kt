package com.localledger.app.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val initialBalanceMinor: Long = 0,
)
