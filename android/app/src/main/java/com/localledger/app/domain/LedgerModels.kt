package com.localledger.app.domain

const val TRANSFER = 2
const val TRANSFER_CATEGORY_ID = "00000000-0000-0000-0000-000000000002"
const val REIMBURSEMENT_PENDING = 0
const val REIMBURSEMENT_DONE = 1

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
    val initialBalanceMinor: Long = 0,
)

data class AccountBalance(val account: Account, val balanceMinor: Long)

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
    val transferAccountId: String? = null,
    val merchant: String? = null,
    val location: String? = null,
    val isReimbursable: Boolean = false,
    val reimbursementStatus: Int = REIMBURSEMENT_PENDING,
)

data class LedgerEntry(
    val transaction: Transaction,
    val categoryName: String,
    val accountName: String,
    val transferAccountName: String? = null,
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
    val lifeItems: List<LifeItem> = emptyList(),
    val lifeCheckIns: List<LifeCheckIn> = emptyList(),
    val focusSessions: List<FocusSession> = emptyList(),
    val studyCards: List<StudyCard> = emptyList(),
    val assetPhotos: Map<String, String> = emptyMap(),
    val paymentCandidates: List<PaymentCandidate> = emptyList(),
)
