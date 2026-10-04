package com.localledger.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsScreen(
    model: SettingsViewModel,
    onCategories: () -> Unit, onPlans: () -> Unit, onExport: () -> Unit,
    onRestore: () -> Unit, onImportCsv: () -> Unit, onUpdate: () -> Unit,
    onMemos: () -> Unit, onNotifications: () -> Unit, onShortcut: (String) -> Unit,
) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var information by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("记录生活的两种方式", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("收支有数，琐事有着落", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("随手账 ${model.versionName} · 数据保存在本机", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        } }
        item { SectionTitle("主页与外观") }
        item { SettingsCard {
            Text("默认打开的主页", fontWeight = FontWeight.Medium)
            Text("下次启动时生效，也可以在主页顶部随时切换", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("home" to "记账", "memos" to "备忘录").forEach { (value, label) ->
                    FilterChip(settings.defaultHome == value, { model.defaultHome(value) }, label = { Text(label) })
                }
            }
            SettingsDivider()
            Text("主题配色", fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("blue" to "澄蓝", "green" to "青绿").forEach { (value, label) ->
                    FilterChip(settings.accent == value, { model.accent(value) }, label = { Text(label) })
                }
            }
            Text("界面外观", fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                    FilterChip(settings.appearance == value, { model.appearance(value) }, label = { Text(label) })
                }
            }
            SettingsDivider()
            SettingsToggle("隐藏金额", "首页、身家和报表金额显示为 ••••", settings.hideAmounts, model::hideAmounts)
        } }
        item { SectionTitle("记账与提醒") }
        item { SettingsCard {
            SettingsToggle("固定账目到期提醒", "每天检查待记固定账目，开启后请允许系统通知", settings.dueReminders,
                { enabled -> if (model.dueReminders(enabled) && enabled) onNotifications() })
            SettingsDivider()
            SettingsLink("记账分类管理", "新增、排序、改名和停用分类", onCategories)
            SettingsDivider()
            SettingsLink("预算与固定账目", "月预算、订阅和定期收支", onPlans)
        } }
        item { SectionTitle("数据与备份") }
        item { SettingsCard {
            SettingsLink("导出完整备份", "保存账目、物品、备忘录与计划", onExport)
            SettingsDivider()
            SettingsLink("恢复完整备份", "确认后替换当前全部数据", onRestore)
            SettingsDivider()
            SettingsLink("导入账单 CSV", "先预览，再确认导入", onImportCsv)
        } }
        item { SectionTitle("快捷入口") }
        item { SettingsCard {
            SettingsLink("添加记账至桌面", "一键打开「记一笔」", { onShortcut("entry") })
            SettingsDivider()
            SettingsLink("添加备忘录至桌面", "一键打开「新建备忘录」", { onShortcut("memo") })
            SettingsDivider()
            SettingsLink("打开备忘录主页", "浏览、搜索、置顶与完成待办", onMemos)
        } }
        item { SectionTitle("应用与帮助") }
        item { SettingsCard {
            SettingsLink("检查应用更新", "管理更新来源并安装新版", onUpdate)
            SettingsDivider()
            SettingsLink("使用帮助", "主页切换、报表、导入与备份", { information = "help" })
            SettingsDivider()
            SettingsLink("关于", "版本 ${model.versionName}", { information = "about" })
        } }
    }
    information?.let { page -> AlertDialog(onDismissRequest = { information = null },
        title = { Text(if (page == "help") "使用帮助" else "随手账 ${model.versionName}") },
        text = { Text(if (page == "help")
            "主页顶部可切换记账和备忘录；离开后点底部「主页」会回到刚才的模式。默认主页在设置中选择。\n\n报表支持周、月、年与自定义日期，可切换收入和支出；点击图表查看某日或某月金额，点击排行账目可编辑。\n\nCSV 需按应用模板整理，导入前预览并核对重复项。完整备份同时包含账目、物品、备忘录及计划，恢复会替换当前数据。\n\n固定账目提醒需要系统通知权限，到期后由你确认入账。" else
            "账目、物品和备忘录存储在本机，无需账号。\n\n请定期导出完整备份，并将备份保存在手机以外的位置。\n\n物品身家按你的参考估值统计，支付账户暂不核算余额。") },
        confirmButton = { TextButton(onClick = { information = null }) { Text("知道了") } }) }
    error?.let { AlertDialog(onDismissRequest = model::dismissError, title = { Text("设置未完成") }, text = { Text(it) },
        confirmButton = { TextButton(onClick = model::dismissError) { Text("知道了") } }) }
}

@Composable
private fun SectionTitle(title: String) = Text(title, Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun SettingsDivider() = HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun SettingsToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun SettingsLink(title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
