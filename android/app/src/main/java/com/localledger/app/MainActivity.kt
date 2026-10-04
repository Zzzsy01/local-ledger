package com.localledger.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.Settings
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.core.content.FileProvider
import com.localledger.app.ui.update.UpdateViewModel
import com.localledger.app.ui.update.UpdateDialog
import com.localledger.app.data.update.UpdateCheckJob
import com.localledger.app.data.reminder.PlanningReminderJob
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.localledger.app.ui.common.LedgerTheme
import com.localledger.app.ui.LedgerApp
import com.localledger.app.ui.backup.LedgerHostViewModel
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private companion object {
        const val ACTION_ENTRY = "com.localledger.app.action.ENTRY"
        const val ACTION_MEMO = "com.localledger.app.action.MEMO"
    }
    private var requestedRoute by mutableStateOf<String?>(null)
    private val updates by viewModels<UpdateViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                UpdateViewModel((application as LedgerApplication).updateRepository) as T
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (!allowed) Toast.makeText(this, "通知未开启；仍可在应用内查看提醒和更新。", Toast.LENGTH_LONG).show()
    }
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == UpdateCheckJob.ACTION_UPDATES) { updates.open(); updates.check() }
        if (intent.action == PlanningReminderJob.ACTION_PLANNING) requestedRoute = "planning"
        shortcutRoute(intent)?.let { requestedRoute = it }
    }

    private fun shortcutRoute(intent: Intent): String? {
        val route = when (intent.action) {
            ACTION_ENTRY -> "add"
            ACTION_MEMO -> "memo/add"
            else -> null
        }
        if (route != null) {
            intent.action = Intent.ACTION_MAIN
            lifecycleScope.launch(Dispatchers.IO) {
                getSystemService(ShortcutManager::class.java).reportShortcutUsed(if (route == "add") "entry" else "memo")
            }
        }
        return route
    }

    private fun recordShortcut(kind: String): ShortcutInfo {
        val memo = kind == "memo"
        return ShortcutInfo.Builder(this, kind)
            .setShortLabel(if (memo) "写备忘" else "记一笔")
            .setLongLabel(if (memo) "新建备忘录" else "记一笔收支")
            .setIcon(Icon.createWithResource(this, R.drawable.ic_launcher))
            .setIntent(Intent(this, MainActivity::class.java).setAction(if (memo) ACTION_MEMO else ACTION_ENTRY))
            .build()
    }

    private fun pinShortcut(kind: String) {
        val manager = getSystemService(ShortcutManager::class.java)
        if (!manager.isRequestPinShortcutSupported) {
            Toast.makeText(this, "当前桌面不支持添加快捷图标，可长按应用图标使用快捷入口。", Toast.LENGTH_LONG).show()
        } else if (!manager.requestPinShortcut(recordShortcut(kind), null)) {
            Toast.makeText(this, "桌面未接受请求，请检查桌面设置。", Toast.LENGTH_LONG).show()
        }
    }
    private fun installUpdate(file: File) {
        try {
            if (!packageManager.canRequestPackageInstalls()) {
                updates.message("请允许随手账安装应用，然后返回点「安装更新」。")
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } else {
                val uri = FileProvider.getUriForFile(this, "$packageName.updates", file)
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        } catch (_: ActivityNotFoundException) { updates.message("当前系统无法打开安装程序。") }
    }

    private val host by viewModels<LedgerHostViewModel> {
        val app = application as LedgerApplication
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LedgerHostViewModel(app.repository) as T
        }
    }

    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(host::export)
    }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(host::prepareRestore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LedgerApplication
        val updatesEnabled = app.updateRepository.initializeAutomaticUpdates()
        if (app.updateRepository.automatic && getSystemService(android.app.job.JobScheduler::class.java).getPendingJob(1101) == null) {
            app.updateRepository.configure(app.updateRepository.source, true)
        }
        if (intent.action == UpdateCheckJob.ACTION_UPDATES) { updates.open(); updates.check() }
        else updates.checkOnOpen()
        if (updatesEnabled && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (intent.action == PlanningReminderJob.ACTION_PLANNING) requestedRoute = "planning"
        shortcutRoute(intent)?.let { requestedRoute = it }
        lifecycleScope.launch(Dispatchers.IO) {
            getSystemService(ShortcutManager::class.java).dynamicShortcuts = listOf(recordShortcut("entry"), recordShortcut("memo"))
        }
        val repository = app.repository
        setContent {
            val state by host.state.collectAsStateWithLifecycle()
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle()
            val darkTheme = when(settings.appearance) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            LaunchedEffect(darkTheme) {
                val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LedgerTheme(darkTheme = darkTheme, accent = settings.accent) {
                Surface(Modifier.fillMaxSize()) {
                    if (state.ready) {
                        key(state.restoreGeneration) {
                            LedgerApp(
                                repository = repository,
                                assetRepository = (application as LedgerApplication).assetRepository,
                                memoRepository = app.memoRepository,
                                wishRepository = app.wishRepository,
                                planningRepository = app.planningRepository,
                                settingsRepository = app.settingsRepository,
                                importRepository = app.importRepository,
                                versionName = app.updateRepository.versionName,
                                onNotifications = ::requestNotifications,
                                onShortcut = ::pinShortcut,
                                requestedRoute = requestedRoute,
                                onRouteHandled = { requestedRoute = null },
                                onUpdate = updates::open,
                                onExport = { exportFile.launch("随手账备份-${LocalDate.now()}.json") },
                                onImport = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            )
                        }
                    } else {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("随手账", style = MaterialTheme.typography.headlineMedium)
                            if (!state.busy) Button(onClick = host::initialize) { Text("重新打开账本") }
                        }
                    }
                    UpdateDialog(updates, onNotifications = ::requestNotifications, onInstall = ::installUpdate)
                    if (state.busy) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text(if (state.ready) "正在处理账本" else "正在打开账本") },
                            text = { Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                            confirmButton = {},
                        )
                    }
                    state.pendingRestore?.let { snapshot ->
                        if (!state.busy) AlertDialog(
                            onDismissRequest = host::cancelRestore,
                            title = { Text("恢复完整备份？") },
                            text = { Text("备份包含 ${snapshot.transactions.count { !it.isDeleted }} 笔账目、${snapshot.assets.count { !it.isDeleted }} 件物品、${snapshot.memos.count { !it.isDeleted }} 条备忘录、${snapshot.wishes.count { !it.isDeleted }} 个心愿、${snapshot.budgets.size} 个月预算、${snapshot.recurringRules.count { !it.isDeleted }} 个固定账目。恢复会替换当前全部数据；旧备份未包含的心愿及其他内容也会清空，建议先导出完整备份。") },
                            confirmButton = { TextButton(onClick = host::confirmRestore) { Text("替换并恢复") } },
                            dismissButton = { TextButton(onClick = host::cancelRestore) { Text("取消") } },
                        )
                    }
                    (state.error ?: state.notice)?.let { message ->
                        AlertDialog(
                            onDismissRequest = host::dismissMessage,
                            title = { Text(if (state.error != null) "操作未完成" else "已完成") },
                            text = { Text(message) },
                            confirmButton = { TextButton(onClick = host::dismissMessage) { Text("知道了") } },
                        )
                    }
                }
            }
        }
    }
}
