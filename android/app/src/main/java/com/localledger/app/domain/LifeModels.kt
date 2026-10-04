package com.localledger.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

enum class LifeKind(val label: String) {
    TRAVEL("旅行计划"), LEARNING("学习计划"), READING("阅读"),
    BIRTHDAY("生日"), COUNTDOWN("倒计时"), COUNTUP("正数日"), ANNIVERSARY("纪念日"),
    HABIT("习惯打卡"), MOOD("心情"), DIARY("日记"), SUBSCRIPTION("订阅记录")
}

val lifePlanKinds = setOf(LifeKind.TRAVEL, LifeKind.LEARNING, LifeKind.READING)
val lifeTimeKinds = setOf(LifeKind.BIRTHDAY, LifeKind.COUNTDOWN, LifeKind.COUNTUP, LifeKind.ANNIVERSARY)

data class LifeItem(
    val id: String,
    val kind: LifeKind,
    val title: String,
    val dateEpochDay: Long,
    val note: String? = null,
    val endDateEpochDay: Long? = null,
    val location: String? = null,
    val targetCount: Long? = null,
    val progressCount: Long = 0,
    val unit: String? = null,
    val amountMinor: Long? = null,
    val repeatYearly: Boolean = false,
    val mood: Int? = null,
    val subscriptionCycleMonths: Int? = null,
    val renewalDayOfMonth: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
    val progress: Float get() = targetCount?.let { (progressCount.toDouble() / it).coerceIn(0.0, 1.0).toFloat() } ?: 0f
    val isComplete: Boolean get() = targetCount?.let { progressCount >= it } ?: false
}

data class LifeCheckIn(
    val itemId: String,
    val dateEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

enum class FocusStatus { RUNNING, PAUSED, COMPLETED }

data class FocusSession(
    val id: String,
    val title: String,
    val durationSeconds: Int,
    val elapsedMillis: Long = 0,
    val startedAt: Long? = null,
    val status: FocusStatus = FocusStatus.PAUSED,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
) {
    val durationMillis: Long get() = durationSeconds * 1_000L
    fun elapsedAt(now: Long): Long = (elapsedMillis + (startedAt?.let { (now - it).coerceAtLeast(0).coerceAtMost(durationMillis) } ?: 0))
        .coerceAtMost(durationMillis)
    fun remainingSeconds(now: Long): Long = ((durationMillis - elapsedAt(now) + 999) / 1_000).coerceAtLeast(0)
}

data class StudyCard(
    val id: String,
    val language: String,
    val word: String,
    val meaning: String,
    val note: String? = null,
    val dueEpochDay: Long,
    val correctStreak: Int = 0,
    val intervalDays: Int = 0,
    val lastReviewEpochDay: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
) {
    val isMastered: Boolean get() = intervalDays >= 30
}

internal fun validateLifeId(id: String) {
    require(id.length == 36 && UUID.fromString(id).toString() == id) { "记录标识无效。" }
}

internal fun validateLifeDay(day: Long) {
    require(day in LocalDate.of(1, 1, 1).toEpochDay()..LocalDate.of(9999, 12, 31).toEpochDay()) { "日期必须在 1 至 9999 年之间。" }
}

private fun validateLifeTimes(createdAt: Long, updatedAt: Long) {
    require(createdAt >= 0 && updatedAt >= createdAt) { "记录时间无效。" }
}

fun validateLifeItem(item: LifeItem) {
    validateLifeId(item.id)
    require(item.title.isNotBlank()) { "请填写名称。" }
    validateLifeDay(item.dateEpochDay)
    item.endDateEpochDay?.let { validateLifeDay(it); require(it >= item.dateEpochDay) { "结束日期不能早于开始日期。" } }
    validateLifeTimes(item.createdAt, item.updatedAt)
    require(item.progressCount >= 0) { "进度不能为负数。" }
    if (item.kind in lifePlanKinds) {
        require(item.targetCount != null && item.targetCount > 0 && item.progressCount <= item.targetCount) { "目标必须大于零，进度不能超过目标。" }
        require(!item.unit.isNullOrBlank()) { "请填写进度单位，例如页、课或项。" }
    } else require(item.targetCount == null && item.progressCount == 0L && item.unit == null) { "此类记录不支持目标进度。" }
    require(item.amountMinor == null || (item.kind in setOf(LifeKind.TRAVEL, LifeKind.SUBSCRIPTION) && item.amountMinor > 0)) { "金额必须大于零，且仅用于旅行或订阅。" }
    require(!item.repeatYearly || item.kind in setOf(LifeKind.BIRTHDAY, LifeKind.ANNIVERSARY)) { "只有生日和纪念日可按年重复。" }
    require(item.endDateEpochDay == null || item.kind == LifeKind.TRAVEL) { "只有旅行计划可填写结束日期。" }
    require(item.location == null || item.kind == LifeKind.TRAVEL) { "只有旅行计划可填写目的地。" }
    if (item.kind == LifeKind.MOOD) require(item.mood in 1..5) { "请选择 1 至 5 的心情。" }
    else require(item.mood == null) { "此类记录不支持心情评分。" }
    if (item.kind == LifeKind.DIARY) require(!item.note.isNullOrBlank()) { "请写下日记内容。" }
    if (item.kind == LifeKind.SUBSCRIPTION) {
        require(item.amountMinor != null && item.amountMinor > 0) { "请填写订阅金额。" }
        require(item.subscriptionCycleMonths in 1..120 && item.renewalDayOfMonth in 1..31) { "续期间隔应为 1 至 120 个月。" }
    } else require(item.subscriptionCycleMonths == null && item.renewalDayOfMonth == null) { "此类记录不支持订阅周期。" }
}

fun validateLifeCheckIn(checkIn: LifeCheckIn) {
    validateLifeId(checkIn.itemId); validateLifeDay(checkIn.dateEpochDay)
    validateLifeTimes(checkIn.createdAt, checkIn.updatedAt)
}

fun validateFocusSession(session: FocusSession) {
    validateLifeId(session.id); validateLifeTimes(session.createdAt, session.updatedAt)
    require(session.title.isNotBlank() && session.durationSeconds in 60..10_800) { "专注名称不能为空，时长应为 1 至 180 分钟。" }
    require(session.elapsedMillis in 0..session.durationMillis) { "专注计时无效。" }
    require(session.startedAt == null || session.startedAt >= session.createdAt) { "专注开始时间无效。" }
    require(session.completedAt == null || session.completedAt >= session.createdAt) { "专注完成时间无效。" }
    when (session.status) {
        FocusStatus.RUNNING -> require(session.startedAt != null && session.completedAt == null && session.elapsedMillis < session.durationMillis) { "正在计时的状态无效。" }
        FocusStatus.PAUSED -> require(session.startedAt == null && session.completedAt == null && session.elapsedMillis < session.durationMillis) { "已暂停的状态无效。" }
        FocusStatus.COMPLETED -> require(session.startedAt == null && session.completedAt != null && session.elapsedMillis == session.durationMillis) { "已完成的专注状态无效。" }
    }
}

fun completeFocusIfDue(session: FocusSession, now: Long): FocusSession {
    if (session.isDeleted || session.status != FocusStatus.RUNNING || session.elapsedAt(now) < session.durationMillis) return session
    val completedAt = requireNotNull(session.startedAt) + (session.durationMillis - session.elapsedMillis)
    return session.copy(elapsedMillis = session.durationMillis, startedAt = null, status = FocusStatus.COMPLETED,
        completedAt = completedAt, updatedAt = maxOf(now, completedAt, session.updatedAt))
}

fun validateStudyCard(card: StudyCard) {
    validateLifeId(card.id); validateLifeTimes(card.createdAt, card.updatedAt); validateLifeDay(card.dueEpochDay)
    require(card.language in setOf("en", "ja") && card.word.isNotBlank() && card.meaning.isNotBlank()) { "请选择英语或日语，并填写单词和释义。" }
    require(card.correctStreak in 0..6 && card.intervalDays in setOf(0, 1, 3, 7, 14, 30, 60)) { "单词复习进度无效。" }
    val intervals = listOf(0, 1, 3, 7, 14, 30, 60)
    require(card.intervalDays == intervals[card.correctStreak] || (card.correctStreak == 0 && card.intervalDays == 1 && card.lastReviewEpochDay != null)) { "单词复习间隔与掌握进度不一致。" }
    card.lastReviewEpochDay?.let { validateLifeDay(it); require(card.intervalDays > 0 && card.dueEpochDay - it == card.intervalDays.toLong()) { "下次复习日期与复习间隔不一致。" } }
    require(card.lastReviewEpochDay != null || (card.correctStreak == 0 && card.intervalDays == 0)) { "单词缺少复习日期。" }
}

/** February 29 is observed on February 28 in non-leap years; calendar days avoid DST drift. */
fun lifeEventDate(item: LifeItem, today: LocalDate): LocalDate {
    if (!item.repeatYearly) return item.date
    val thisYear = item.date.withYear(today.year)
    return if (thisYear >= today) thisYear else item.date.withYear(today.year + 1)
}

fun lifeEventDays(item: LifeItem, today: LocalDate): Long = if (item.kind == LifeKind.COUNTUP)
    today.toEpochDay() - item.dateEpochDay else lifeEventDate(item, today).toEpochDay() - today.toEpochDay()

fun habitStreak(days: Collection<Long>, today: LocalDate): Int {
    val completed = days.toSet()
    var day = today.toEpochDay()
    if (day !in completed) day--
    var count = 0
    while (day in completed) { count++; day-- }
    return count
}

fun nextSubscriptionDate(item: LifeItem): LocalDate {
    require(item.kind == LifeKind.SUBSCRIPTION) { "此记录不是订阅。" }
    val month = YearMonth.from(item.date).plusMonths(requireNotNull(item.subscriptionCycleMonths).toLong())
    val result = month.atDay(minOf(requireNotNull(item.renewalDayOfMonth), month.lengthOfMonth()))
    validateLifeDay(result.toEpochDay())
    return result
}

fun reviewStudyCard(card: StudyCard, remembered: Boolean, today: LocalDate, now: Long): StudyCard {
    require(!card.isDeleted && card.dueEpochDay <= today.toEpochDay()) { "这张卡片还未到复习日期。" }
    val streak = if (remembered) minOf(card.correctStreak + 1, 6) else 0
    // A missed word returns tomorrow; new/correct words advance through 1/3/7/14/30/60 days.
    val interval = if (remembered) listOf(0, 1, 3, 7, 14, 30, 60)[streak] else 1
    return card.copy(correctStreak = streak, intervalDays = interval,
        dueEpochDay = today.plusDays(interval.toLong()).toEpochDay(), lastReviewEpochDay = today.toEpochDay(),
        updatedAt = maxOf(now, card.updatedAt)).also(::validateStudyCard)
}

data class LifeSummary(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val checkIns: Int,
    val habitDays: Int,
    val focusSessions: Int,
    val focusMinutes: Long,
    val diaryEntries: Int,
    val moodEntries: Int,
    val averageMood: Double?,
    val studyReviews: Int,
    val completedPlans: Int,
)

fun lifeSummary(items: List<LifeItem>, checks: List<LifeCheckIn>, focus: List<FocusSession>, cards: List<StudyCard>,
    today: LocalDate, monthly: Boolean, zone: ZoneId = ZoneId.systemDefault()): LifeSummary {
    val start = if (monthly) today.withDayOfMonth(1) else today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = if (monthly) start.plusMonths(1) else start.plusWeeks(1)
    fun inPeriod(day: Long) = day >= start.toEpochDay() && day < end.toEpochDay()
    val active = items.filterNot { it.isDeleted }
    val habits = active.filter { it.kind == LifeKind.HABIT }.map { it.id }.toSet()
    val periodChecks = checks.filter { !it.isDeleted && it.itemId in habits && inPeriod(it.dateEpochDay) }
    val completed = focus.filter { !it.isDeleted && it.status == FocusStatus.COMPLETED && it.completedAt != null &&
        inPeriod(Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate().toEpochDay()) }
    val moods = active.filter { it.kind == LifeKind.MOOD && inPeriod(it.dateEpochDay) }
    return LifeSummary(start, end, periodChecks.size, periodChecks.map { it.dateEpochDay }.distinct().size,
        completed.size, completed.sumOf { it.durationSeconds.toLong() } / 60,
        active.count { it.kind == LifeKind.DIARY && inPeriod(it.dateEpochDay) }, moods.size,
        moods.mapNotNull { it.mood }.takeIf { it.isNotEmpty() }?.average(),
        cards.count { !it.isDeleted && it.lastReviewEpochDay?.let(::inPeriod) == true },
        active.count { it.kind in lifePlanKinds && it.isComplete && inPeriod(Instant.ofEpochMilli(it.updatedAt).atZone(zone).toLocalDate().toEpochDay()) })
}
