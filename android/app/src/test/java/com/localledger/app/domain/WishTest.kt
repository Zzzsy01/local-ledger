package com.localledger.app.domain

import org.junit.Assert.*
import org.junit.Test

class WishTest {
    private fun wish(id: Int, target: Long, saved: Long = 0, purchased: Boolean = false, deleted: Boolean = false) =
        Wish("00000000-0000-4000-8000-${id.toString().padStart(12, '0')}", "Goal $id", target, saved,
            isPurchased = purchased, createdAt = 10, updatedAt = 20, isDeleted = deleted)

    @Test fun budgetAndProgressCountOnlyPendingGoalsAndKeepEachGoalIndependent() {
        val first = wish(1, 60_000, 20_000)
        val overfunded = wish(2, 40_000, 45_000)
        val totals = wishTotals(listOf(first, overfunded, wish(3, 90_000, purchased = true), wish(4, 10_000, deleted = true)))
        assertEquals(WishTotals(100_000, 40_000, 2, 1), totals)
        assertEquals(60_000L, totals.fundedMinor)
        assertEquals(.6f, totals.progress, .0001f)
        assertEquals(0L, overfunded.remainingMinor)
        assertEquals(1f, overfunded.progress, 0f)
        assertEquals(0f, wishTotals(emptyList()).progress, 0f)
    }

    @Test fun overflowingBudgetIsRejectedWhilePurchasedAndDeletedGoalsDoNotConsumeIt() {
        val maximum = wish(1, Long.MAX_VALUE, Long.MAX_VALUE)
        assertEquals(0L, maximum.remainingMinor)
        assertEquals(1f, maximum.progress, 0f)
        assertThrows(IllegalArgumentException::class.java) { wishTotals(listOf(maximum, wish(2, 1))) }
        assertEquals(1L, wishTotals(listOf(maximum.copy(isPurchased = true), wish(2, 1))).targetMinor)
        assertEquals(1L, wishTotals(listOf(maximum.copy(isDeleted = true), wish(2, 1))).remainingMinor)
    }

    @Test fun invalidAmountsNamesAndTimestampsAreRejected() {
        val valid = wish(1, 10_000)
        validateWish(valid)
        listOf(valid.copy(targetMinor = 0), valid.copy(savedMinor = -1), valid.copy(name = " "),
            valid.copy(updatedAt = 9), valid.copy(id = "invalid")).forEach {
            assertThrows(IllegalArgumentException::class.java) { validateWish(it) }
        }
    }
}
