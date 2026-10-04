package com.localledger.app.data.repository

import android.content.Context
import android.net.Uri
import com.localledger.app.data.db.LedgerDatabase
import com.localledger.app.data.db.PaymentCandidateEntity
import com.localledger.app.data.db.toDomain
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.localledger.app.domain.paymentHint
import java.util.UUID
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.map

class CaptureRepository(private val context: Context, private val db: LedgerDatabase) {
    val candidates = db.paymentCandidateDao().observe().map { rows -> rows.map { it.toDomain() } }
    suspend fun candidate(id: String) = db.paymentCandidateDao().candidate(id)?.takeUnless { it.isDeleted }?.toDomain()
    suspend fun dismiss(id: String) = db.paymentCandidateDao().dismiss(id)
    suspend fun capture(source: String, key: String, text: String, timestamp: Long) {
        if (text.length > 32_000 || paymentHint(text).type == null) return
        val id = UUID.nameUUIDFromBytes("$source:$key:$timestamp".toByteArray(Charsets.UTF_8)).toString()
        db.paymentCandidateDao().insert(PaymentCandidateEntity(id, source, text, timestamp))
    }
    suspend fun recognize(uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image).addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result.text.take(32_000))
            }.addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
                .addOnCompleteListener { recognizer.close() }
        }
    }
}
