package com.localledger.app.data.db

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localledger.app.data.backup.BackupFiles
import com.localledger.app.data.backup.BackupCodec
import com.localledger.app.data.repository.*
import com.localledger.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID
import java.io.File

@RunWith(AndroidJUnit4::class)
class LifeIntegrationTest {
    @Test fun photoBackupsRestoreBytesAndInvalidPhotosLeaveDatabaseUnchanged() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build()
        val source = File(context.cacheDir,"asset-photo-test.png")
        val created = mutableListOf<File>()
        try {
            val ledger = LedgerRepository(db,context.getSharedPreferences("life-test",Context.MODE_PRIVATE),BackupFiles(context.contentResolver,context.filesDir))
            ledger.initialize();val assets = AssetRepository(db,context)
            val bitmap = Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.GREEN);source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            val path = assets.importPhoto(Uri.fromFile(source));created.add(assets.photoFile(path)!!)
            assets.save(null,"测试相机","摄影",30000,15000,1,emptyList(),null,photoPaths = listOf(path))
            val snapshot = BackupCodec.decode(BackupCodec.encode(ledger.snapshot()))
            ledger.restore(snapshot)
            val restored = assets.assets.first().single()
            val newPhoto = assets.photoFile(restored.photoPaths.single())!!;created.add(newPhoto)
            assertNotEquals(path,restored.photoPaths.single());assertArrayEquals(source.readBytes(),newPhoto.readBytes())
            val before = ledger.snapshot()
            try { ledger.restore(before.copy(assetPhotos = before.assetPhotos.mapValues { "AQID" }));fail("Invalid image must be rejected") }
            catch(expected: IllegalArgumentException) { }
            assertEquals(before,ledger.snapshot())
        } finally { created.forEach { it.delete() };source.delete();db.close() }
    }
    @Test fun transferDeleteAndCandidateConfirmationAreAtomic() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build()
        try {
            val ledger = LedgerRepository(db,context.getSharedPreferences("life-test",Context.MODE_PRIVATE),BackupFiles(context.contentResolver))
            ledger.initialize()
            val accounts = ledger.accounts.first().take(2)
            ledger.saveAccount(accounts[0].id,accounts[0].name,10_000)
            val id = ledger.saveTransaction(null,2500,TRANSFER,TRANSFER_CATEGORY_ID,accounts[0].id,null,100,transferAccountId = accounts[1].id)
            val balances = ledger.balances.first().associate { it.account.id to it.balanceMinor }
            assertEquals(7500L,balances[accounts[0].id]);assertEquals(2500L,balances[accounts[1].id])
            assertEquals(MonthlySummary(),ledger.summary(0,1000).first());assertEquals(0L,ledger.reportMetrics(0,1000).first().transactionCount)
            ledger.deleteTransaction(id);assertEquals(10_000L,ledger.balances.first().first { it.account.id==accounts[0].id }.balanceMinor)
            ledger.undoDeleteTransaction(id)
            val candidateId = UUID.randomUUID().toString()
            db.paymentCandidateDao().insert(PaymentCandidateEntity(candidateId,"微信","支付成功 ¥12.00",100))
            val category = ledger.categories.first().first { it.type==EXPENSE }
            ledger.saveTransaction(null,1200,EXPENSE,category.id,accounts[0].id,null,100,source = 2,importKey = "notification:$candidateId",captureCandidateId = candidateId)
            assertTrue(db.paymentCandidateDao().candidate(candidateId)!!.isDeleted)
            val count = ledger.entries(0,1000).first().size
            try { ledger.saveTransaction(null,1200,EXPENSE,category.id,accounts[0].id,null,100,source = 2,importKey = "notification:$candidateId",captureCandidateId = candidateId);fail("Candidate must only be confirmed once") }
            catch (expected: IllegalArgumentException) { }
            assertEquals(count,ledger.entries(0,1000).first().size)
        } finally { db.close() }
    }
    @Test fun habitsAndFocusSurviveBackupRestoreAndRejectInvalidHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build()
        try {
            val ledger = LedgerRepository(db,context.getSharedPreferences("life-test",Context.MODE_PRIVATE),BackupFiles(context.contentResolver))
            ledger.initialize();val life = LifeRepository(db)
            val today = LocalDate.now();val item = LifeItem(UUID.randomUUID().toString(),LifeKind.HABIT,"阅读",today.toEpochDay(),createdAt = 0,updatedAt = 0)
            life.saveItem(item);life.toggleCheckIn(item.id);life.toggleCheckIn(item.id)
            try { life.saveItem(item.copy(dateEpochDay = today.plusDays(1).toEpochDay()));fail("Deleted history is retained") } catch(expected: IllegalArgumentException) { }
            life.toggleCheckIn(item.id);life.startFocus("阅读",1)
            val session = life.focusSessions.first().single()
            life.completeDueFocus(session.startedAt!! + 60_000);life.completeDueFocus(session.startedAt + 70_000)
            assertEquals(1,life.focusSessions.first().count { it.status==FocusStatus.COMPLETED })
            val snapshot = BackupCodec.decode(BackupCodec.encode(ledger.snapshot()))
            ledger.restore(snapshot)
            assertEquals(snapshot,ledger.snapshot())
            assertEquals(1,life.checkIns.first().size)
        } finally { db.close() }
    }
    @Test fun bundledRecognitionReadsAnImageWithoutDownloadingModels() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build()
        val file = File(context.cacheDir,"ocr-test.png")
        try {
            val bitmap = Bitmap.createBitmap(1000,400,Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap);canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK;textSize=64f }
            canvas.drawText("支付成功",50f,100f,paint);canvas.drawText("¥88.00",50f,220f,paint)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            val text = CaptureRepository(context,db).recognize(Uri.fromFile(file))
            assertTrue(text,text.contains("88.00"));assertTrue(text,text.contains("支付成功"))
            assertEquals(8800L,paymentHint(text).amountMinor)
        } finally { file.delete();db.close() }
    }
}
