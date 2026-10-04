package com.localledger.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.localledger.app.domain.*

@Entity(tableName = "life_items")
data class LifeItemEntity(
    @PrimaryKey val id: String,
    val kind: String,
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
)

@Entity(tableName = "life_check_ins", primaryKeys = ["itemId", "dateEpochDay"],
    foreignKeys = [ForeignKey(entity = LifeItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")])
data class LifeCheckInEntity(
    val itemId: String,
    val dateEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val durationSeconds: Int,
    val elapsedMillis: Long = 0,
    val startedAt: Long? = null,
    val status: String = "PAUSED",
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Entity(tableName = "study_cards")
data class StudyCardEntity(
    @PrimaryKey val id: String,
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
)

fun LifeItemEntity.toDomain() = LifeItem(id, LifeKind.valueOf(kind), title, dateEpochDay, note, endDateEpochDay, location,
    targetCount, progressCount, unit, amountMinor, repeatYearly, mood, subscriptionCycleMonths, renewalDayOfMonth, createdAt, updatedAt, isDeleted)
fun LifeItem.toEntity() = LifeItemEntity(id, kind.name, title, dateEpochDay, note, endDateEpochDay, location,
    targetCount, progressCount, unit, amountMinor, repeatYearly, mood, subscriptionCycleMonths, renewalDayOfMonth, createdAt, updatedAt, isDeleted)
fun LifeCheckInEntity.toDomain() = LifeCheckIn(itemId, dateEpochDay, createdAt, updatedAt, isDeleted)
fun LifeCheckIn.toEntity() = LifeCheckInEntity(itemId, dateEpochDay, createdAt, updatedAt, isDeleted)
fun FocusSessionEntity.toDomain() = FocusSession(id, title, durationSeconds, elapsedMillis, startedAt, FocusStatus.valueOf(status), completedAt, createdAt, updatedAt, isDeleted)
fun FocusSession.toEntity() = FocusSessionEntity(id, title, durationSeconds, elapsedMillis, startedAt, status.name, completedAt, createdAt, updatedAt, isDeleted)
fun StudyCardEntity.toDomain() = StudyCard(id, language, word, meaning, note, dueEpochDay, correctStreak, intervalDays, lastReviewEpochDay, createdAt, updatedAt, isDeleted)
fun StudyCard.toEntity() = StudyCardEntity(id, language, word, meaning, note, dueEpochDay, correctStreak, intervalDays, lastReviewEpochDay, createdAt, updatedAt, isDeleted)
