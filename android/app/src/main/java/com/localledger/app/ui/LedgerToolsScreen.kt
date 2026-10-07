package com.localledger.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.localledger.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import com.localledger.app.ui.common.PageHeading

@Composable
fun LedgerToolsScreen(open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            PageHeading("财务工具箱", "账户、预算与账单，各有安排", R.drawable.art_wallet, eyebrow = "井井有条 · 从容管理")
        }
        item { Text("资金管理", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        item { ToolLink("账户管理", "查看余额，新增或调整资金账户", "accounts", open) }
        item { ToolLink("预算与固定账目", "月预算、订阅及定期收支", "planning", open) }
        item { ToolLink("报销管理", "跟进待报销和已报销账目", "reimbursement", open) }
        item { Text("账单整理", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        item { ToolLink("截图与自动采集", "离线识别截图，核对支付通知", "capture", open) }
        item { ToolLink("导入账单 CSV", "预览微信、支付宝等账单后导入", "import", open) }
        item { ToolLink("记账分类", "新增、排序、改名和停用分类", "category", open) }
        item { Text("完整备份、隐私、提醒及应用更新在右上角设置。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ToolLink(title: String, description: String, route: String, open: (String) -> Unit) {
    Card(onClick = { open(route) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(when(route) {
                    "accounts" -> R.drawable.ic_ui_wallet
                    "planning" -> R.drawable.ic_ui_calendar
                    "reimbursement" -> R.drawable.ic_ui_arrow_up_right
                    "capture" -> R.drawable.ic_ui_copy
                    "import" -> R.drawable.ic_ui_arrow_down_right
                    else -> R.drawable.ic_ui_notebook
                }), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_ui_chevron_right), null, Modifier.size(18.dp))
        }
    }
}
