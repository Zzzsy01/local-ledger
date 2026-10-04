package com.localledger.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.data.repository.RecycleRepository
import com.localledger.app.ui.common.displayMessage
import kotlinx.coroutines.launch

@Composable
fun RecycleScreen(repository: RecycleRepository, memoMode: Boolean) {
    val all by repository.items.collectAsStateWithLifecycle(emptyList())
    val rows = all.filter { if (memoMode) it.type in listOf("备忘录", "生活", "心愿", "专注", "单词") else it.type in listOf("账目", "物品") }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("回收站", style = MaterialTheme.typography.headlineSmall) }
        if (rows.isEmpty()) item { Text("没有已删除的记录。") }
        items(rows, key = { it.type + it.id }) { record ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(record.title, style = MaterialTheme.typography.titleMedium); Text(record.type, style = MaterialTheme.typography.bodySmall)
                TextButton({ busy = true; scope.launch { try { repository.restore(record) } catch (e: Exception) { error = e.displayMessage() } finally { busy = false } } }, enabled = !busy) { Text("恢复") }
            } }
        }
    }
    error?.let { AlertDialog(onDismissRequest = { error = null }, title = { Text("恢复未完成") }, text = { Text(it) }, confirmButton = { TextButton({ error = null }) { Text("知道了") } }) }
}
