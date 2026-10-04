package com.localledger.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.UpdateRepository
import com.localledger.app.domain.AppUpdate
import com.localledger.app.ui.common.displayMessage
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateState(val visible: Boolean = false, val source: String = "", val automatic: Boolean = false,
    val busy: Boolean = false, val message: String? = null, val update: AppUpdate? = null, val downloaded: File? = null)
class UpdateViewModel(private val repository: UpdateRepository) : ViewModel() {
    private val mutable = MutableStateFlow(UpdateState(source = repository.source, automatic = repository.automatic))
    val state = mutable.asStateFlow()
    val versionName = repository.versionName
    fun open() { mutable.update { it.copy(visible = true) } }
    fun close() { if (!mutable.value.busy) mutable.update { it.copy(visible = false) } }
    fun message(value: String) { mutable.update { it.copy(message = value) } }
    fun configure(source: String, automatic: Boolean): Boolean = try {
        repository.configure(source, automatic)
        mutable.value = UpdateState(visible = true, source = repository.source, automatic = repository.automatic, message = "更新设置已保存")
        true
    } catch (error: Exception) { message(error.displayMessage()); false }
    fun checkOnOpen() { if (repository.shouldCheckOnOpen()) check(silent = true) }
    fun check(silent: Boolean = false) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = null, update = null, downloaded = null) }
        viewModelScope.launch {
            try {
                val update = repository.check()
                mutable.update { it.copy(update = update, visible = it.visible || update != null,
                    message = if (update == null) "当前已是最新版本" else null) }
            } catch (error: Exception) {
                val message = error.displayMessage()
                if (!silent) mutable.update { it.copy(message = message) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun download() {
        val update = mutable.value.update ?: return
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = "正在下载安装包，请稍候…", downloaded = null) }
        viewModelScope.launch {
            try {
                val file = repository.download(update)
                mutable.update { it.copy(downloaded = file, message = "校验通过，可以覆盖安装并保留数据") }
            } catch (error: Exception) { mutable.update { it.copy(message = error.displayMessage()) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
