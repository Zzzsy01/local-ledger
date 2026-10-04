package com.localledger.app.domain

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class LifeTest {
    private val id = "00000000-0000-4000-8000-000000000099"
    @Test fun anniversaryAndSubscriptionUseCalendarDates() {
        val birthday = LifeItem(id, LifeKind.BIRTHDAY, "生日", LocalDate.of(2000,2,29).toEpochDay(), repeatYearly = true, createdAt = 0, updatedAt = 0)
        assertEquals(LocalDate.of(2027,2,28), lifeEventDate(birthday, LocalDate.of(2027,1,1)))
        assertEquals(0L, lifeEventDays(birthday, LocalDate.of(2027,2,28)))
        assertEquals(LocalDate.of(2028,2,29), lifeEventDate(birthday, LocalDate.of(2027,3,1)))
        val subscription = birthday.copy(kind = LifeKind.SUBSCRIPTION, repeatYearly = false, dateEpochDay = LocalDate.of(2027,1,31).toEpochDay(), amountMinor = 100, subscriptionCycleMonths = 1, renewalDayOfMonth = 31)
        validateLifeItem(subscription)
        val feb = nextSubscriptionDate(subscription)
        assertEquals(LocalDate.of(2027,2,28), feb)
        assertEquals(LocalDate.of(2027,3,31), nextSubscriptionDate(subscription.copy(dateEpochDay = feb.toEpochDay())))
    }
    @Test fun focusCompletesOnceAfterProcessRestartAndPausedTimeDoesNotGrow() {
        val running = FocusSession(id, "阅读", 60, elapsedMillis = 10_000, startedAt = 100_000, status = FocusStatus.RUNNING, createdAt = 90_000, updatedAt = 100_000)
        validateFocusSession(running)
        assertEquals(40L, running.remainingSeconds(110_000))
        val completed = completeFocusIfDue(running, 200_000)
        assertEquals(FocusStatus.COMPLETED, completed.status)
        assertEquals(150_000L, completed.completedAt)
        validateFocusSession(completed)
        assertEquals(completed, completeFocusIfDue(completed, 300_000))
        assertEquals(10_000L, running.copy(startedAt = null, status = FocusStatus.PAUSED).elapsedAt(999_999))
    }
    @Test fun habitStreakAndWordReviewAreBasedOnActualDays() {
        val day = LocalDate.of(2026,10,4)
        assertEquals(2, habitStreak(listOf(day.minusDays(1).toEpochDay(), day.minusDays(2).toEpochDay()), day))
        assertEquals(0, habitStreak(listOf(day.minusDays(2).toEpochDay()), day))
        val new = StudyCard(id,"en","hello","你好",dueEpochDay = day.toEpochDay(),createdAt = 0,updatedAt = 0)
        val next = reviewStudyCard(new, true, day, 100)
        assertEquals(day.plusDays(1).toEpochDay(), next.dueEpochDay)
        assertThrows(IllegalArgumentException::class.java) { reviewStudyCard(next, true, day, 200) }
        val forgotten = reviewStudyCard(next, false, day.plusDays(1), 200)
        assertEquals(0, forgotten.correctStreak)
        validateStudyCard(forgotten)
    }
    @Test fun transfersUpdateBothBalancesAndRejectOverflowAndInvalidReferences() {
        val accounts = listOf(Account(id,"现金",initialBalanceMinor = 10_000),Account("00000000-0000-4000-8000-000000000098","微信"))
        val transfer = Transaction("00000000-0000-4000-8000-000000000097",2500,TRANSFER,TRANSFER_CATEGORY_ID,id,occurredAt = 1,createdAt = 1,updatedAt = 1, transferAccountId = accounts[1].id)
        assertEquals(listOf(7500L,2500L),accountBalances(accounts,listOf(transfer)).map { it.balanceMinor })
        assertEquals(listOf(10_000L,0L),accountBalances(accounts,listOf(transfer.copy(isDeleted = true))).map { it.balanceMinor })
        assertThrows(IllegalArgumentException::class.java) { accountBalances(accounts,listOf(transfer.copy(transferAccountId = id))) }
        assertThrows(IllegalArgumentException::class.java) { accountBalances(accounts.map { if(it.id==id) it.copy(initialBalanceMinor = Long.MIN_VALUE) else it },listOf(transfer)) }
        assertEquals(Long.MIN_VALUE,parseBalanceAmount("-92233720368547758.08"))
        assertNull(parseBalanceAmount("92233720368547758.08"))
    }
    @Test fun paymentHintsDoNotGuessRefundsOrAmbiguousPrices() {
        assertEquals(8800L,paymentHint("支付成功\n¥88.00").amountMinor)
        assertEquals(8800L,paymentHint("实付 ¥88.00\n原价 ¥100.00").amountMinor)
        assertNull(paymentHint("支付成功\n¥88.00\n¥100.00").amountMinor)
        assertNull(paymentHint("退款成功 ¥88.00").type)
        assertNull(paymentHint("待支付 ¥88.00").amountMinor)
        assertEquals(INCOME,paymentHint("收款成功\n¥20.00").type)
    }
}
