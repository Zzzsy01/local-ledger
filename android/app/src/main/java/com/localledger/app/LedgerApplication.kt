package com.localledger.app

import android.app.Application
import com.localledger.app.data.backup.BackupFiles
import com.localledger.app.data.db.LedgerDatabase
import com.localledger.app.data.repository.UpdateRepository
import com.localledger.app.data.repository.AssetRepository
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.data.repository.MemoRepository
import com.localledger.app.data.repository.PlanningRepository
import com.localledger.app.data.repository.SettingsRepository
import com.localledger.app.data.repository.ImportRepository

class LedgerApplication : Application() {
    private val database by lazy { LedgerDatabase.create(this) }
    val updateRepository by lazy { UpdateRepository(this) }
    val assetRepository by lazy { AssetRepository(database) }
    val memoRepository by lazy { MemoRepository(database) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val planningRepository by lazy { PlanningRepository(database, repository) }
    val importRepository by lazy { ImportRepository(database, repository, contentResolver) }
    val repository by lazy {
        LedgerRepository(database, getSharedPreferences("ledger", MODE_PRIVATE), BackupFiles(contentResolver))
    }
}
