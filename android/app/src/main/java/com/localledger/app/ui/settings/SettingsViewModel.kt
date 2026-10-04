package com.localledger.app.ui.settings

import androidx.lifecycle.ViewModel
import com.localledger.app.data.repository.SettingsRepository
import com.localledger.app.ui.common.displayMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val repository: SettingsRepository, val versionName: String) : ViewModel() {
    val settings = repository.settings
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    fun dismissError() { mutableError.value = null }
    fun hideAmounts(value: Boolean) { repository.hideAmounts(value) }
    fun appearance(value: String) { repository.appearance(value) }
    fun defaultHome(value: String) { repository.defaultHome(value) }
    fun accent(value: String) { repository.accent(value) }
    fun capturePayments(value: Boolean) { repository.capturePayments(value) }
    fun appLock(value: Boolean) { try { repository.appLock(value) } catch (error: Exception) { mutableError.value = error.displayMessage() } }
    fun dueReminders(value: Boolean): Boolean = try {
        repository.dueReminders(value)
        true
    } catch (error: Exception) { mutableError.value = error.displayMessage(); false }
}
