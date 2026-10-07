package com.localledger.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.R
import com.localledger.app.ui.common.PageHeading

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsScreen(
    model: SettingsViewModel,
    memoMode: Boolean,
    onExport: () -> Unit, onRestore: () -> Unit, onUpdate: () -> Unit,
    onNotifications: () -> Unit, onShortcut: (String) -> Unit,
    onRecycle: () -> Unit = {}, onCaptureAccess: () -> Unit = {},
) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var information by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeading("按你的习惯", "数据保存在本机 · 无需账号", if (memoMode) R.drawable.art_journal else R.drawable.art_wallet,
            Modifier.padding(horizontal = 20.dp), "随手账 ${model.versionName}") }
        item { SectionTitle("主页与外观") }
        item { SettingsCard {
            Text("默认打开的主页", fontWeight = FontWeight.Medium)
            Text("下次启动时生效，也可以在主页顶部随时切换", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("memos" to "备忘录", "home" to "记账").forEach { (value, label) ->
                    FilterChip(settings.defaultHome == value, { model.defaultHome(value) }, label = { Text(label) })
                }
            }
            SettingsDivider()
            Text("主题配色", fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("paper" to "奶白", "blue" to "澄蓝", "green" to "青绿").forEach { (value, label) ->
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
            SettingsToggle("隐藏金额", "隐藏账单、藏品估值、心愿金额与进度", settings.hideAmounts, model::hideAmounts)
        } }
        if (!memoMode) {
        item { SectionTitle("支付通知采集") }
        item { SettingsCard {
            SettingsToggle("采集支付通知", "需授予通知使用权；仅微信/支付宝，候选核对后入账", settings.capturePayments, { enabled ->
                model.capturePayments(enabled); if (enabled) onCaptureAccess()
            })
            SettingsLink("通知使用权", "开启采集后，前往系统授权或关闭权限", onCaptureAccess)

        } }
        }
        item { SectionTitle("提醒与隐私") }
        item { SettingsCard { SettingsToggle("到期提醒", "每天检查待办、重要日期、订阅、物品保修及固定账目；允许系统通知后生效", settings.dueReminders,
            { enabled -> if (model.dueReminders(enabled) && enabled) onNotifications() }) } }
        item { SettingsCard { SettingsToggle("应用锁", "使用手机锁屏密码；离开应用后锁定，并隐藏桌面待办内容", settings.appLock, model::appLock) } }
        item { SectionTitle("数据与备份") }
        item { SettingsCard {
            SettingsLink("导出完整备份", if (memoMode) "保存备忘录、心愿及全部本机数据" else "保存账目、物品及全部本机数据", onExport)
            SettingsDivider()
            SettingsLink("恢复完整备份", "确认后替换当前全部数据", onRestore)
            SettingsDivider()
            SettingsLink("回收站", "恢复已删除的记录", onRecycle)

        } }
        item { SectionTitle("快捷入口") }
        item { SettingsCard {
            if (memoMode) {
                SettingsLink("添加待办小组件", "桌面查看前三项待办，点击勾选完成，可调整尺寸", { onShortcut("widget") })
                SettingsDivider()
            }
            if (memoMode) SettingsLink("添加备忘录至桌面", "一键打开「新建备忘录」", { onShortcut("memo") })
            else SettingsLink("添加记账至桌面", "一键打开「记一笔」", { onShortcut("entry") })
        } }
        item { SectionTitle("应用与帮助") }
        item { SettingsCard {
            SettingsLink("应用自动更新", "自动检查新版，可查看更新设置", onUpdate)
            SettingsDivider()
            SettingsLink("使用帮助", if (memoMode) "备忘录、心愿进度与备份" else "账单、报表、导入与备份", { information = "help" })
            SettingsDivider()
            SettingsLink("关于", "版本 ${model.versionName}", { information = "about" })
        } }
    }
    information?.let { page -> AlertDialog(onDismissRequest = { information = null },
        title = { Text(if (page == "help") "使用帮助" else "随手账 ${model.versionName}") },
        text = { Text(if (page == "help") {
            if (memoMode) "顶部可一键切换备忘录和记账，两种模式各自保留页面。\n\n生活按日常、计划、日期、学习和总结分组；博物馆保存物品位置与历史，顶部放大镜可跨模式找东西。\n\n备忘录支持搜索、置顶和完成待办；心愿清单填写预计价格和已攒金额，自动计算还差多少钱。攒钱是为目标预留的金额，买到后可标记达成。\n\n自动更新已默认开启，允许通知后可收到新版提醒。完整备份包含全部本机数据，恢复旧备份会清空其中未包含的新功能数据。"
            else "顶部可一键切换记账和备忘录，两种模式各自保留页面。\n\n账户、预算、报销和账单采集统一在底栏「工具」。博物馆保存物品档案，顶部放大镜可找东西。\n\n报表支持周、月、年和自定义日期；点击图表查看金额，点击排行账目可编辑。支持模板及微信/支付宝常见 CSV；退款、中性及关闭交易需手动核对。\n\n固定账目提醒需要系统通知权限，到期后由你确认入账。完整备份包含全部本机数据，恢复会替换当前数据。"
        } else "数据保存在本机，无需账号。\n\n请定期导出完整备份，并将备份另存到手机以外的位置。\n\n应用可以自动检查新版，下载和安装由你确认。") },
        confirmButton = { TextButton(onClick = { information = null }) { Text("知道了") } }) }
    error?.let { AlertDialog(onDismissRequest = model::dismissError, title = { Text("设置未完成") }, text = { Text(it) },
        confirmButton = { TextButton(onClick = model::dismissError) { Text("知道了") } }) }
}

@Composable
private fun SectionTitle(title: String) = Text(title, Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun SettingsDivider() = HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun SettingsToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun SettingsLink(title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_ui_chevron_right), null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
