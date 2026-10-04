package com.localledger.app.domain

data class Category(
    val id: String,
    val name: String,
    val type: Int,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isDeleted: Boolean = false,
)

data class Account(
    val id: String,
    val name: String,
    val isDeleted: Boolean = false,
)

data class Transaction(
    val id: String,
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

data class LedgerEntry(
    val transaction: Transaction,
    val categoryName: String,
    val accountName: String,
)

data class MonthlySummary(val income: Long = 0, val expense: Long = 0)

data class CategoryTotal(val categoryId: String, val name: String, val type: Int, val total: Long)

data class LedgerSnapshot(
    val categories: List<Category>,
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val assets: List<Asset> = emptyList(),
    val memos: List<Memo> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val recurringRules: List<RecurringRule> = emptyList(),
    val wishes: List<Wish> = emptyList(),
)
