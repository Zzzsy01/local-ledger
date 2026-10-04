package com.localledger.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeDao {
    @Query("SELECT * FROM life_items WHERE isDeleted = 1") fun deletedItems(): Flow<List<LifeItemEntity>>
    @Query("SELECT * FROM focus_sessions WHERE isDeleted = 1") fun deletedFocus(): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM study_cards WHERE isDeleted = 1") fun deletedCards(): Flow<List<StudyCardEntity>>
    @Query("SELECT * FROM life_items WHERE isDeleted = 0 ORDER BY updatedAt DESC, id")
    fun observeItems(): Flow<List<LifeItemEntity>>
    @Query("SELECT * FROM life_check_ins WHERE isDeleted = 0 ORDER BY dateEpochDay DESC, itemId")
    fun observeCheckIns(): Flow<List<LifeCheckInEntity>>
    @Query("SELECT * FROM focus_sessions WHERE isDeleted = 0 ORDER BY createdAt DESC, id")
    fun observeFocusSessions(): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM study_cards WHERE isDeleted = 0 ORDER BY dueEpochDay, word, id")
    fun observeStudyCards(): Flow<List<StudyCardEntity>>

    @Query("SELECT * FROM life_items WHERE id = :id") suspend fun item(id: String): LifeItemEntity?
    @Query("SELECT * FROM life_check_ins WHERE itemId = :itemId AND dateEpochDay = :day") suspend fun checkIn(itemId: String, day: Long): LifeCheckInEntity?
    @Query("SELECT * FROM focus_sessions WHERE id = :id") suspend fun focusSession(id: String): FocusSessionEntity?
    @Query("SELECT * FROM focus_sessions WHERE isDeleted = 0 AND status != 'COMPLETED' ORDER BY createdAt, id LIMIT 1") suspend fun activeFocusSession(): FocusSessionEntity?
    @Query("SELECT * FROM study_cards WHERE id = :id") suspend fun studyCard(id: String): StudyCardEntity?

    @Upsert suspend fun saveItem(item: LifeItemEntity)
    @Upsert suspend fun saveCheckIn(checkIn: LifeCheckInEntity)
    @Upsert suspend fun saveFocusSession(session: FocusSessionEntity)
    @Upsert suspend fun saveStudyCard(card: StudyCardEntity)

    @Query("SELECT * FROM life_items ORDER BY id") suspend fun allItems(): List<LifeItemEntity>
    @Query("SELECT * FROM life_check_ins ORDER BY itemId, dateEpochDay") suspend fun allCheckIns(): List<LifeCheckInEntity>
    @Query("SELECT * FROM focus_sessions ORDER BY id") suspend fun allFocusSessions(): List<FocusSessionEntity>
    @Query("SELECT * FROM study_cards ORDER BY id") suspend fun allStudyCards(): List<StudyCardEntity>
    @Insert suspend fun insertItems(items: List<LifeItemEntity>)
    @Insert suspend fun insertCheckIns(checkIns: List<LifeCheckInEntity>)
    @Insert suspend fun insertFocusSessions(sessions: List<FocusSessionEntity>)
    @Insert suspend fun insertStudyCards(cards: List<StudyCardEntity>)
    @Query("DELETE FROM life_check_ins") suspend fun clearCheckIns()
    @Query("DELETE FROM life_items") suspend fun clearItems()
    @Query("DELETE FROM focus_sessions") suspend fun clearFocusSessions()
    @Query("DELETE FROM study_cards") suspend fun clearStudyCards()
}
