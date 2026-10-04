package com.localledger.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "payment_candidates")
data class PaymentCandidateEntity(@PrimaryKey val id: String, val source: String, val text: String, val capturedAt: Long, val isDeleted: Boolean = false)

@Dao
interface PaymentCandidateDao {
    @Query("SELECT * FROM payment_candidates WHERE isDeleted = 0 ORDER BY capturedAt DESC") fun observe(): Flow<List<PaymentCandidateEntity>>
    @Query("SELECT * FROM payment_candidates WHERE id = :id") suspend fun candidate(id: String): PaymentCandidateEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(candidate: PaymentCandidateEntity)
    @Query("UPDATE payment_candidates SET isDeleted = 1 WHERE id = :id") suspend fun dismiss(id: String)
    @Query("SELECT * FROM payment_candidates ORDER BY id") suspend fun all(): List<PaymentCandidateEntity>
    @Insert suspend fun insertAll(rows: List<PaymentCandidateEntity>)
    @Query("DELETE FROM payment_candidates") suspend fun clear()
}

fun PaymentCandidateEntity.toDomain() = com.localledger.app.domain.PaymentCandidate(id, source, text, capturedAt, isDeleted)
fun com.localledger.app.domain.PaymentCandidate.toEntity() = PaymentCandidateEntity(id, source, text, capturedAt, isDeleted)
