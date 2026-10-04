package com.localledger.app.domain

import java.util.UUID

fun validateSnapshot(snapshot: LedgerSnapshot) {
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
        if (!it.isDeleted) {
            try { assetTotal = Math.addExact(assetTotal, it.valueMinor) }
            catch (error: ArithmeticException) { throw IllegalArgumentException("物品估值总额超出范围。", error) }
        }
    }
    require(snapshot.accounts.any { !it.isDeleted }) { "备份必须保留至少一个可用账户。" }
    snapshot.categories.forEach {
        require(it.name.isNotBlank() && it.type in 0..1) { "分类名称或类型无效。" }
    }
    snapshot.accounts.forEach { require(it.name.isNotBlank()) { "账户名称不能为空。" } }
    val categories = snapshot.categories.associateBy { it.id }
    val accounts = snapshot.accounts.map { it.id }.toSet()
    snapshot.recurringRules.forEach {
        validateRecurringRule(it)
        require(it.accountId in accounts && categories[it.categoryId]?.type == it.type) { "固定账目引用的分类或账户无效。" }
    }
    val totals = LongArray(2)
    snapshot.transactions.forEach {
        require(it.amountMinor > 0 && it.type in 0..1 && it.source >= 0) { "账目金额、类型或来源无效。" }
        require(it.occurredAt >= 0 && it.createdAt >= 0 && it.updatedAt >= it.createdAt) { "账目时间无效。" }
        require(it.accountId in accounts) { "账目引用了不存在的账户。" }
        require(categories[it.categoryId]?.type == it.type) { "账目分类不存在或类型不一致。" }
        if (!it.isDeleted) {
            try {
                totals[it.type] = Math.addExact(totals[it.type], it.amountMinor)
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("收入或支出总额超出可保存范围。", error)
            }
        }
    }
}

private fun validateIds(ids: List<String>) {
    require(ids.toSet().size == ids.size) { "备份包含重复的记录 ID。" }
    ids.forEach { require(it.length == 36 && UUID.fromString(it).toString() == it) { "记录 ID 必须是标准小写 UUID。" } }
}
