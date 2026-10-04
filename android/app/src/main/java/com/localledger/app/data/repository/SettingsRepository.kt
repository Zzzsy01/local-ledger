package com.localledger.app.data.repository

import android.content.Context
import com.localledger.app.data.reminder.PlanningReminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val hideAmounts: Boolean = false, val appearance: String = "system", val dueReminders: Boolean = false,
    val defaultHome: String = "home", val accent: String = "blue",
)

class SettingsRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences("app-settings", Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(AppSettings(
        hideAmounts = preferences.getBoolean("hide-amounts", false),
        appearance = preferences.getString("appearance", "system") ?: "system",
        dueReminders = PlanningReminders.isEnabled(applicationContext),
        defaultHome = preferences.getString("default-home", "home") ?: "home",
        accent = preferences.getString("accent", "blue") ?: "blue",
    ))
    val settings = mutableSettings.asStateFlow()

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
        require(value in listOf("blue", "green"))
        preferences.edit().putString("accent", value).apply()
        mutableSettings.value = mutableSettings.value.copy(accent = value)
    }
}
