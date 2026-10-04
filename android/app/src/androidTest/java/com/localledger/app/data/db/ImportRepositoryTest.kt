package com.localledger.app.data.db

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localledger.app.data.backup.BackupCodec
import com.localledger.app.data.backup.BackupFiles
import com.localledger.app.data.repository.ImportRepository
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.data.repository.MemoRepository
import com.localledger.app.data.repository.PlanningRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImportRepositoryTest {
    @Test fun csvPreviewReimportBackupAndAtomicFailurePreserveLedger() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        val file = File(context.cacheDir, "csv-import-test.csv")
        try {
            val ledger = LedgerRepository(db, context.getSharedPreferences("import-test", Context.MODE_PRIVATE), BackupFiles(context.contentResolver))
            ledger.initialize()
            val expense = ledger.categories.first().first { it.type == 0 }.id
            val income = ledger.categories.first().first { it.type == 1 }.id
            val account = ledger.accounts.first().first().id
            val importer = ImportRepository(db, ledger, context.contentResolver)
            file.writeText("时间,收支,金额,备注,来源ID\n2026-10-01,支出,1.25,one,A\n2026-10-02,收入,2.50,two,B\n")
            val preview = importer.preview(Uri.fromFile(file), "UTF-8", expense, income, account)
            assertTrue(ledger.snapshot().transactions.isEmpty())
            assertEquals(2, importer.confirm(preview, true))
            assertEquals(0, importer.confirm(preview, true))
            MemoRepository(db).save(null, "Review", "Keep all data", true, true)
            val planning = PlanningRepository(db, ledger)
            planning.saveBudget("2026-10", 12345)
            planning.saveRule(null, "Fee", 1500, 0, expense, account, 10, 31, "keep")
            val saved = ledger.snapshot()
            assertTrue(saved.transactions.all { it.source == 1 && it.importKey != null })
            assertEquals(saved, BackupCodec.decode(BackupCodec.encode(saved)))
            ledger.deleteTransaction(saved.transactions.first().id)
            val imported = importer.preview(Uri.fromFile(file), "UTF-8", expense, income, account)
            assertTrue(imported.items.all { it.alreadyImported })
            ledger.restore(saved)
            assertEquals(saved, ledger.snapshot())
            assertEquals(0, importer.confirm(imported, false))
            file.writeText("时间,收支,金额\n2026-10-03,支出,0.01\n2026-10-04,支出,92233720368547758.07\n")
            val overflow = importer.preview(Uri.fromFile(file), "UTF-8", expense, income, account)
            var rejected = false
            try { importer.confirm(overflow, false) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(saved, ledger.snapshot())
        } finally { db.close(); file.delete() }
    }
}
