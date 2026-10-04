package com.localledger.app.domain

import java.util.UUID
import java.math.BigInteger

fun parseBalanceAmount(text: String): Long? {
    val value = text.trim()
    if (!Regex("^-?[0-9]+(?:\\.[0-9]{1,2})?$").matches(value)) return null
    val negative = value.startsWith('-')
    val parts = value.removePrefix("-").split('.')
    val minor = BigInteger(parts[0]).multiply(BigInteger.valueOf(100))
        .add(BigInteger(parts.getOrNull(1)?.padEnd(2, '0') ?: "0"))
        .let { if (negative) it.negate() else it }
    return if (minor < BigInteger.valueOf(Long.MIN_VALUE) || minor > BigInteger.valueOf(Long.MAX_VALUE)) null else minor.toLong()
}

fun validateTransactionDetails(transaction: Transaction) {
    require(transaction.amountMinor > 0 && transaction.type in EXPENSE..TRANSFER && transaction.source >= 0) { "账目金额、类型或来源无效。" }
    require(transaction.occurredAt >= 0 && transaction.createdAt >= 0 && transaction.updatedAt >= transaction.createdAt) { "账目时间无效。" }
    if (transaction.type == TRANSFER) {
        require(transaction.categoryId == TRANSFER_CATEGORY_ID) { "转账必须使用转账分类。" }
        require(transaction.transferAccountId != null && transaction.transferAccountId != transaction.accountId) { "转出和转入账户必须不同。" }
    } else require(transaction.transferAccountId == null) { "收支账目不能包含转入账户。" }
    require(transaction.reimbursementStatus in REIMBURSEMENT_PENDING..REIMBURSEMENT_DONE) { "报销状态无效。" }
    require(!transaction.isReimbursable || transaction.type == EXPENSE) { "只有支出可以标记报销。" }
    require(transaction.isReimbursable || transaction.reimbursementStatus == REIMBURSEMENT_PENDING) { "非报销账目不能标记已报销。" }
}

// Accumulate in unbounded integers, then check the final balance. Intermediate debit/credit order cannot overflow.
fun accountBalances(accounts: List<Account>, transactions: List<Transaction>): List<AccountBalance> {
    val balances = accounts.associate { it.id to BigInteger.valueOf(it.initialBalanceMinor) }.toMutableMap()
    val totals = arrayOf(BigInteger.ZERO, BigInteger.ZERO)
    transactions.filterNot { it.isDeleted }.forEach { transaction ->
        validateTransactionDetails(transaction)
        val amount = BigInteger.valueOf(transaction.amountMinor)
        val source = requireNotNull(balances[transaction.accountId]) { "账目引用了不存在的账户。" }
        if (transaction.type == INCOME) {
            balances[transaction.accountId] = source.add(amount)
            totals[INCOME] = totals[INCOME].add(amount)
        } else {
            balances[transaction.accountId] = source.subtract(amount)
            if (transaction.type == EXPENSE) totals[EXPENSE] = totals[EXPENSE].add(amount)
            else {
                val target = requireNotNull(balances[transaction.transferAccountId]) { "转入账户不存在。" }
                balances[transaction.transferAccountId!!] = target.add(amount)
            }
        }
    }
    val maximum = BigInteger.valueOf(Long.MAX_VALUE)
    require(totals.all { it <= maximum }) { "收入或支出总额超出可保存范围。" }
    val minimum = BigInteger.valueOf(Long.MIN_VALUE)
    return accounts.map { account ->
        val balance = balances.getValue(account.id)
        require(balance >= minimum && balance <= maximum) { "账户「${account.name}」余额超出可保存范围。" }
        AccountBalance(account, balance.toLong())
    }
}

fun validateSnapshot(snapshot: LedgerSnapshot) {
    validateIds(snapshot.paymentCandidates.map { it.id })
    snapshot.paymentCandidates.forEach {
        require(it.source in listOf("微信", "支付宝") && it.text.isNotBlank() && it.text.length <= 32_000 && it.capturedAt >= 0) { "支付候选记录无效。" }
    }
    validateIds(snapshot.lifeItems.map { it.id })
    validateIds(snapshot.focusSessions.map { it.id })
    validateIds(snapshot.studyCards.map { it.id })
    snapshot.lifeItems.forEach(::validateLifeItem)
    snapshot.lifeCheckIns.forEach(::validateLifeCheckIn)
    snapshot.focusSessions.forEach(::validateFocusSession)
    snapshot.studyCards.forEach(::validateStudyCard)
    val life = snapshot.lifeItems.associateBy { it.id }
    require(snapshot.lifeCheckIns.map { it.itemId to it.dateEpochDay }.distinct().size == snapshot.lifeCheckIns.size) { "备份包含重复打卡。" }
    snapshot.lifeCheckIns.forEach { check ->
        val item = life[check.itemId]
        require(item?.kind == LifeKind.HABIT && check.dateEpochDay >= item.dateEpochDay) { "打卡对应的习惯不存在或日期无效。" }
    }
    require(snapshot.focusSessions.count { !it.isDeleted && it.status != FocusStatus.COMPLETED } <= 1) { "备份包含多个未结束的专注。" }
    val photoPaths = snapshot.assets.flatMap { it.photoPaths }.toSet()
    require(snapshot.assetPhotos.keys == photoPaths) { "物品照片附件不完整。" }
    var photoBytes = 0L
    snapshot.assetPhotos.forEach { (path, base64) ->
        require(isAssetPhotoPath(path) && base64.length <= 28 * 1024 * 1024) { "物品照片附件无效。" }
        val bytes = java.util.Base64.getDecoder().decode(base64)
        require(bytes.isNotEmpty() && bytes.size <= 20 * 1024 * 1024 && java.util.Base64.getEncoder().encodeToString(bytes) == base64) { "照片附件无效或过大。" }
        photoBytes += bytes.size
        require(photoBytes <= 48L * 1024 * 1024) { "照片附件总量超过 48 MB。" }
    }
    validateIds(snapshot.categories.map { it.id })
    validateIds(snapshot.accounts.map { it.id })
    validateIds(snapshot.transactions.map { it.id })
    validateIds(snapshot.assets.map { it.id })
    validateIds(snapshot.memos.map { it.id })
    validateIds(snapshot.recurringRules.map { it.id })
    validateIds(snapshot.wishes.map { it.id })
    snapshot.wishes.forEach(::validateWish)
    wishTotals(snapshot.wishes)
    snapshot.memos.forEach(::validateMemo)
    require(snapshot.budgets.map { it.month }.toSet().size == snapshot.budgets.size) { "备份包含重复月份预算。" }
    snapshot.budgets.forEach(::validateBudget)
    val importKeys = snapshot.transactions.mapNotNull { it.importKey }
    require(importKeys.toSet().size == importKeys.size && importKeys.all { it.isNotBlank() && it.length <= 128 }) { "导入来源标识重复或无效。" }
    var assetTotal = 0L
    snapshot.assets.forEach {
        validateAsset(it)
        if (!it.isDeleted && it.isHeld) {
            try { assetTotal = Math.addExact(assetTotal, it.valueMinor) }
            catch (error: ArithmeticException) { throw IllegalArgumentException("物品估值总额超出范围。", error) }
        }
    }
    require(snapshot.accounts.any { !it.isDeleted }) { "备份必须保留至少一个可用账户。" }
    snapshot.categories.forEach {
        require(it.name.isNotBlank() && it.type in EXPENSE..TRANSFER) { "分类名称或类型无效。" }
        require(it.type != TRANSFER || (it.id == TRANSFER_CATEGORY_ID && !it.isDeleted)) { "转账分类无效。" }
    }
    snapshot.accounts.forEach { require(it.name.isNotBlank()) { "账户名称不能为空。" } }
    val categories = snapshot.categories.associateBy { it.id }
    val accounts = snapshot.accounts.map { it.id }.toSet()
    snapshot.recurringRules.forEach {
        validateRecurringRule(it)
        require(it.accountId in accounts && categories[it.categoryId]?.type == it.type) { "固定账目引用的分类或账户无效。" }
    }
    snapshot.transactions.forEach {
        validateTransactionDetails(it)
        require(it.accountId in accounts) { "账目引用了不存在的账户。" }
        require(it.transferAccountId == null || it.transferAccountId in accounts) { "账目引用了不存在的转入账户。" }
        require(categories[it.categoryId]?.type == it.type) { "账目分类不存在或类型不一致。" }
    }
    accountBalances(snapshot.accounts, snapshot.transactions)
}

private fun validateIds(ids: List<String>) {
    require(ids.toSet().size == ids.size) { "备份包含重复的记录 ID。" }
    ids.forEach { require(it.length == 36 && UUID.fromString(it).toString() == it) { "记录 ID 必须是标准小写 UUID。" } }
}
