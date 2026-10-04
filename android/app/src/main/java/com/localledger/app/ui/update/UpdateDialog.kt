package com.localledger.app.ui.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@Composable
fun UpdateDialog(model: UpdateViewModel, onNotifications: () -> Unit, onInstall: (File) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    if (!state.visible) return
    var source by remember(state.source) { mutableStateOf(state.source) }
    var automatic by remember(state.automatic) { mutableStateOf(state.automatic) }
    AlertDialog(onDismissRequest = model::close, title = { Text("应用更新 · ${model.versionName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("有新版时通知你，在应用内下载，确认后覆盖安装。账本仍保存在本机。")
                OutlinedTextField(source, { source = it }, label = { Text("更新地址") }, placeholder = { Text("开发者提供的 HTTPS 地址") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy, maxLines = 3)
                if (state.source.isBlank()) Text("尚未接入发布源，当前无法获取新版。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自动检查并通知", Modifier.weight(1f))
                    Switch(automatic, { automatic = it }, enabled = !state.busy)
                }
                Text("每天检查一次，系统可能延后；下载和安装由你确认。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { if (model.configure(source, automatic) && automatic) onNotifications() }, enabled = !state.busy) { Text("保存更新设置") }
                state.update?.let { update ->
                    HorizontalDivider()
                    Text("发现新版本 ${update.versionName}", style = MaterialTheme.typography.titleMedium)
                    Text(update.notes.ifBlank { "发布者未填写更新说明" })
                    if (state.downloaded == null) Button(onClick = model::download, enabled = !state.busy) { Text("下载更新") }
                    else Button(onClick = { onInstall(state.downloaded!!) }, enabled = !state.busy) { Text("安装更新") }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.message?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = { model.check() }, enabled = !state.busy && state.source.isNotBlank() && source == state.source && automatic == state.automatic) { Text("检查更新") } },
        dismissButton = { TextButton(onClick = model::close, enabled = !state.busy) { Text("关闭") } },
    )
}
