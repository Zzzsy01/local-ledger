package com.localledger.app.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.domain.Category
import com.localledger.app.domain.EXPENSE
import com.localledger.app.domain.INCOME
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import kotlinx.coroutines.flow.collect

@Composable
fun CategoryScreen(model: CategoryViewModel, snackbar: SnackbarHostState) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val operationError by model.operationError.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableIntStateOf(EXPENSE) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var disabling by remember { mutableStateOf<Category?>(null) }
    LaunchedEffect(model) { model.messages.collect { snackbar.showSnackbar(it) } }
    val active = state.categories.filter { it.type == type && !it.isDeleted }
    val disabled = state.categories.filter { it.type == type && it.isDeleted }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == EXPENSE, onClick = { type = EXPENSE }, label = { Text("支出") })
                FilterChip(selected = type == INCOME, onClick = { type = INCOME }, label = { Text("收入") })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { model.clearError(); editing = null; editorOpen = true }, enabled = !busy) { Text("新增") }
            }
        }
        if (state.loading) item { Loading() }
        state.error?.let { error -> item { Message(error, isError = true) } }
        if (!state.loading && state.error == null && active.isEmpty()) item { Message("暂无分类，点击「新增」添加") }
        itemsIndexed(active, key = { _, item -> item.id }) { index, category ->
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(category.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        IconButton(onClick = { model.move(category.id, -1) }, enabled = !busy && index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, "上移${category.name}")
                        }
                        IconButton(onClick = { model.move(category.id, 1) }, enabled = !busy && index < active.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, "下移${category.name}")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(enabled = !busy, onClick = { model.clearError(); editing = category; editorOpen = true }) { Text("改名") }
                        TextButton(enabled = !busy, onClick = { disabling = category }) { Text("停用") }
                    }
                }
            }
        }
        if (disabled.isNotEmpty()) {
            item { Text("已停用 · 历史账目仍保留", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(disabled, key = { it.id }) { category ->
                Text(category.name, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (editorOpen) {
        var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
        AlertDialog(onDismissRequest = { if (!busy) editorOpen = false },
            title = { Text(if (editing == null) "新增${if (type == INCOME) "收入" else "支出"}分类" else "分类改名") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("分类名称") }, singleLine = true, enabled = !busy)
                    operationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                model.save(editing?.id, name, editing?.type ?: type) { editorOpen = false }
            }) { Text(if (busy) "保存中…" else "保存") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { editorOpen = false }) { Text("取消") } },
        )
    }
    disabling?.let { category ->
        AlertDialog(onDismissRequest = { disabling = null }, title = { Text("停用「${category.name}」？") },
            text = { Text("记新账时不再显示这个分类，历史账目和统计不会受到影响。") },
            confirmButton = { TextButton(onClick = { model.disable(category.id); disabling = null }, enabled = !busy) { Text("停用") } },
            dismissButton = { TextButton(onClick = { disabling = null }) { Text("取消") } },
        )
    }
}
