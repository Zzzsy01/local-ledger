package com.localledger.app.ui.importing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.data.repository.CaptureRepository
import com.localledger.app.ui.common.displayMessage
import com.localledger.app.ui.common.visibleAmount
import com.localledger.app.domain.paymentHint
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen(repository: CaptureRepository, onReview: (String) -> Unit) {
    val rows by repository.candidates.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { busy = true; scope.launch {
            try { text = repository.recognize(uri); if (text.isBlank()) error = "图片中没有识别到文字。" }
            catch (e: Exception) { error = e.displayMessage() } finally { busy = false }
        } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("截图与自动采集", style = MaterialTheme.typography.headlineSmall) }
        item { Text("图片文字在本机识别。支付通知先存为候选，核对金额、分类和账户后再保存账目。", style = MaterialTheme.typography.bodyMedium) }
        item { Button({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Text("选择账单截图") } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (text.isNotEmpty()) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it.take(32_000) }, Modifier.fillMaxWidth(), label = { Text("识别文字，可修正") }, maxLines = 8)
            Text("金额不明确时请在下一页手动填写。", style = MaterialTheme.typography.bodySmall)
            TextButton({ onReview(text) }) { Text("核对并记账") }
            TextButton({ text = "" }) { Text("清除识别结果") }
        } } }
        item { Text("待核对通知 ${rows.size} 条", style = MaterialTheme.typography.titleMedium) }
        items(rows, key = { it.id }) { row -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text(row.source, style = MaterialTheme.typography.titleMedium)
            val hint = paymentHint(row.text)
            Text(hint.amountMinor?.let { "¥${visibleAmount(it)}" } ?: "待确认金额")
            Text(row.text, maxLines = 5)
            Row { TextButton({ onReview("candidate:${row.id}") }, enabled = !busy) { Text("核对入账") }
                TextButton({ busy = true; scope.launch { try { repository.dismiss(row.id) } catch(e: Exception) { error = e.displayMessage() } finally { busy = false } } }, enabled = !busy) { Text("忽略") } }
        } } }
    }
}
