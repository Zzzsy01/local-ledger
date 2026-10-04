package com.localledger.app.data.repository

import android.content.Context
import com.localledger.app.data.reminder.PlanningReminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val hideAmounts: Boolean = false, val appearance: String = "system", val dueReminders: Boolean = false,
    val defaultHome: String = "memos", val accent: String = "paper",
    val appLock: Boolean = false,
    val capturePayments: Boolean = false,
)

class SettingsRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences("app-settings", Context.MODE_PRIVATE)
    init {
        if (!preferences.getBoolean("memo-first-home", false)) {
            preferences.edit().putString("default-home", "memos").putBoolean("memo-first-home", true).apply()
        }
    }
    private val mutableSettings = MutableStateFlow(AppSettings(
        hideAmounts = preferences.getBoolean("hide-amounts", false),
        appearance = preferences.getString("appearance", "system") ?: "system",
        dueReminders = PlanningReminders.isEnabled(applicationContext),
        defaultHome = preferences.getString("default-home", "memos") ?: "memos",
        accent = preferences.getString("accent", "paper") ?: "paper",
        appLock = preferences.getBoolean("app-lock", false),
        capturePayments = preferences.getBoolean("capture-payments", false),
    ))
    val settings = mutableSettings.asStateFlow()
    fun capturePayments(enabled: Boolean) {
        preferences.edit().putBoolean("capture-payments", enabled).apply()
        mutableSettings.value = mutableSettings.value.copy(capturePayments = enabled)
    }
    fun appLock(enabled: Boolean) {
        require(!enabled || applicationContext.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure) { "请先为手机设置锁屏密码。" }
        preferences.edit().putBoolean("app-lock", enabled).apply()
        mutableSettings.value = mutableSettings.value.copy(appLock = enabled)
        com.localledger.app.data.widget.MemoWidget.refresh(applicationContext)
    }

    fun hideAmounts(hidden: Boolean) {
        preferences.edit().putBoolean("hide-amounts", hidden).apply()
        mutableSettings.value = mutableSettings.value.copy(hideAmounts = hidden)
    }

    fun appearance(value: String) {
        require(value in listOf("system", "light", "dark"))
        preferences.edit().putString("appearance", value).apply()
        mutableSettings.value = mutableSettings.value.copy(appearance = value)
    }

    fun dueReminders(enabled: Boolean) {
        PlanningReminders.configure(applicationContext, enabled)
        mutableSettings.value = mutableSettings.value.copy(dueReminders = enabled)
    }

    fun defaultHome(value: String) {
        require(value in listOf("home", "memos"))
        preferences.edit().putString("default-home", value).apply()
        mutableSettings.value = mutableSettings.value.copy(defaultHome = value)
    }

    fun accent(value: String) {
        require(value in listOf("blue", "green", "paper"))
        preferences.edit().putString("accent", value).apply()
        mutableSettings.value = mutableSettings.value.copy(accent = value)
    }
}
