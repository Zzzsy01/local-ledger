package com.localledger.app.domain

import java.util.UUID

data class Wish(
    val id: String,
    val name: String,
    val targetMinor: Long,
    val savedMinor: Long = 0,
    val note: String? = null,
    val isPurchased: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
) {
    val remainingMinor: Long get() = maxOf(targetMinor - savedMinor, 0)
    val progress: Float get() = (savedMinor.toDouble() / targetMinor).coerceIn(0.0, 1.0).toFloat()
}

data class WishTotals(val targetMinor: Long = 0, val remainingMinor: Long = 0, val active: Int = 0, val purchased: Int = 0) {
    val fundedMinor: Long get() = targetMinor - remainingMinor
    val progress: Float get() = if (targetMinor == 0L) 0f else (fundedMinor.toDouble() / targetMinor).toFloat()
}

fun validateWish(wish: Wish) {
    require(wish.id.length == 36 && UUID.fromString(wish.id).toString() == wish.id) { "心愿标识无效。" }
    require(wish.name.isNotBlank()) { "请写下想买的东西。" }
    require(wish.targetMinor > 0 && wish.savedMinor >= 0) { "预计价格必须大于零，已攒金额不能为负数。" }
    require(wish.createdAt >= 0 && wish.updatedAt >= wish.createdAt) { "心愿时间无效。" }
}

fun wishTotals(wishes: List<Wish>): WishTotals {
    val active = wishes.filter { !it.isDeleted && !it.isPurchased }
    var target = 0L
    var remaining = 0L
    try {
        active.forEach { target = Math.addExact(target, it.targetMinor); remaining = Math.addExact(remaining, it.remainingMinor) }
    } catch (error: ArithmeticException) {
        throw IllegalArgumentException("想买清单的总预算超出可保存范围。", error)
    }
    return WishTotals(target, remaining, active.size, wishes.count { !it.isDeleted && it.isPurchased })
}
