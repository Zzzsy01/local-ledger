package com.localledger.app.data.db

import com.localledger.app.domain.Account
import com.localledger.app.domain.Category
import com.localledger.app.domain.LedgerEntry
import com.localledger.app.domain.Transaction

internal fun CategoryEntity.toDomain() = Category(id, name, type, icon, sortOrder, isDeleted)
internal fun Category.toEntity() = CategoryEntity(id, name, type, icon, sortOrder, isDeleted)
internal fun AccountEntity.toDomain() = Account(id, name, isDeleted)
internal fun Account.toEntity() = AccountEntity(id, name, isDeleted)
internal fun TransactionEntity.toDomain() = Transaction(
    id, amountMinor, type, categoryId, accountId, note, occurredAt, createdAt, updatedAt, isDeleted, source, importKey,
)
internal fun Transaction.toEntity() = TransactionEntity(
    id, amountMinor, type, categoryId, accountId, note, occurredAt, createdAt, updatedAt, isDeleted, source, importKey,
)
internal fun LedgerEntryRow.toDomain() = LedgerEntry(transaction.toDomain(), categoryName, accountName)
