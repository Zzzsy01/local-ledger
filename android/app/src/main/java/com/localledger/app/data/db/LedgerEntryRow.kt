package com.localledger.app.data.db

import androidx.room.Embedded

data class LedgerEntryRow(
    @Embedded val transaction: TransactionEntity,
    val categoryName: String,
    val accountName: String,
)
