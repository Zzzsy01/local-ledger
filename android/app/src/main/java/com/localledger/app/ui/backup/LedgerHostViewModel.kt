package com.localledger.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.domain.LedgerSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HostState(
    val ready: Boolean = false,
    val busy: Boolean = true,
    val error: String? = null,
    val notice: String? = null,
    val pendingRestore: LedgerSnapshot? = null,
    val restoreGeneration: Int = 0,
)

class LedgerHostViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HostState())
    val state = mutableState.asStateFlow()

    init { initialize() }

    fun initialize() = runOperation {
        repository.initialize()
        mutableState.value = mutableState.value.copy(ready = true)
    }

    fun export(uri: Uri) = runOperation {
        repository.exportBackup(uri)
        mutableState.value = mutableState.value.copy(notice = "完整备份已保存。")
    }

    fun prepareRestore(uri: Uri) = runOperation {
        val snapshot = repository.readBackup(uri)
        mutableState.value = mutableState.value.copy(pendingRestore = snapshot)
    }

    fun cancelRestore() {
        mutableState.value = mutableState.value.copy(pendingRestore = null)
    }

    fun confirmRestore() {
        val snapshot = mutableState.value.pendingRestore ?: return
        runOperation {
            repository.restore(snapshot)
            mutableState.value = mutableState.value.copy(
                pendingRestore = null,
                restoreGeneration = mutableState.value.restoreGeneration + 1,
                notice = "账本已完整恢复。",
            )
        }
    }

    fun dismissMessage() {
        mutableState.value = mutableState.value.copy(error = null, notice = null)
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (mutableState.value.busy && mutableState.value.ready) return
        mutableState.value = mutableState.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(error = error.message ?: "操作失败，请重试。")
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }
}
