package com.localledger.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import com.localledger.app.ui.common.visibleAmount

@Composable
fun AccountsScreen(model: AccountsViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("我的账户", style = MaterialTheme.typography.headlineSmall)
                Text("余额 = 初始余额 + 收入 − 支出 + 转入 − 转出。停用账户会保留历史账目。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { model.edit(null) }, enabled = !state.saving) { Text("新增账户") }
            }
        }
        if (state.loading) item { Loading() }
        if (state.draft == null) state.error?.let { error -> item { Message(error, isError = true) } }
        items(state.balances.sortedBy { it.account.isDeleted }, key = { it.account.id }) { balance ->
            val account = balance.account
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(account.name + if (account.isDeleted) " · 已停用" else "", style = MaterialTheme.typography.titleMedium)
                    Text("¥${visibleAmount(balance.balanceMinor)}", style = MaterialTheme.typography.headlineSmall)
                    Text("初始余额 ¥${visibleAmount(account.initialBalanceMinor)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { model.edit(account) }, enabled = !state.saving) { Text("编辑") }
                        TextButton(onClick = { model.setDisabled(account, !account.isDeleted) }, enabled = !state.saving) {
                            Text(if (account.isDeleted) "恢复使用" else "停用")
                        }
                    }
                }
            }
        }
    }
    state.draft?.let { draft ->
        AlertDialog(onDismissRequest = model::closeEditor,
            title = { Text(if (draft.id == null) "新增账户" else "编辑账户") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(draft.name, model::setName, label = { Text("账户名称") }, singleLine = true, enabled = !state.saving)
                    OutlinedTextField(draft.initialBalance, model::setInitialBalance, label = { Text("初始余额") }, prefix = { Text("¥") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), enabled = !state.saving)
                    Text("可填负数。修改初始余额会调整当前余额。", style = MaterialTheme.typography.bodySmall)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = model::save, enabled = !state.saving) { Text(if (state.saving) "保存中…" else "保存") } },
            dismissButton = { TextButton(onClick = model::closeEditor, enabled = !state.saving) { Text("取消") } })
    }
}
