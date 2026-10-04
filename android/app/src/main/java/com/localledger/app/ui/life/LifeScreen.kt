package com.localledger.app.ui.life

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.domain.*
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import com.localledger.app.ui.common.visibleAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal val lifeOrange = Color(0xFFED9354)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LifeScreen(model: LifeViewModel, onWishes: () -> Unit, onTasks: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val query by model.search.collectAsStateWithLifecycle()
    val monthly by model.monthlySummary.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var editorKind by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var plansOpen by rememberSaveable { mutableStateOf(true) }
    var timesOpen by rememberSaveable { mutableStateOf(true) }
    var recordsOpen by rememberSaveable { mutableStateOf(true) }
    var habitsOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var focusEditor by rememberSaveable { mutableStateOf(false) }
    var focusHistory by rememberSaveable { mutableStateOf(false) }
    var deletingFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var cardEditor by rememberSaveable { mutableStateOf(false) }
    var cardId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var cardList by rememberSaveable { mutableStateOf(false) }
    var deletingCard by rememberSaveable { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(state.now).atZone(zone).toLocalDate()
    val matched = state.items.filter { query.isBlank() || it.title.contains(query, true) || it.note.orEmpty().contains(query, true) || it.kind.label.contains(query) }
    val plans = matched.filter { it.kind in lifePlanKinds }
    val times = matched.filter { it.kind in lifeTimeKinds }.sortedBy { lifeEventDate(it, today) }
    val records = matched.filter { it.kind !in lifePlanKinds && it.kind !in lifeTimeKinds }
    val habits = state.items.filter { it.kind == LifeKind.HABIT && it.dateEpochDay <= today.toEpochDay() }
    val checkedToday = state.checkIns.count { !it.isDeleted && it.dateEpochDay == today.toEpochDay() && habits.any { habit -> habit.id == it.itemId } }
    val focusedToday = state.focusSessions.filter { it.status == FocusStatus.COMPLETED && it.completedAt?.let { time -> Instant.ofEpochMilli(time).atZone(zone).toLocalDate() == today } == true }.sumOf { it.durationSeconds.toLong() } / 60
    val dueCards = state.cards.filter { it.dueEpochDay <= today.toEpochDay() }
    val activeFocus = state.focusSessions.firstOrNull { it.status != FocusStatus.COMPLETED }
    fun edit(item: LifeItem) { editorId = item.id; editorKind = item.kind.name; model.clearFeedback() }
    fun new(kind: LifeKind) { creating = false; editorId = null; editorKind = kind.name; model.clearFeedback() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 108.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("生活", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(today.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeMetric("今日习惯", "$checkedToday/${habits.size}", Modifier.weight(1f))
                    LifeMetric("今日专注", "${focusedToday}m", Modifier.weight(1f))
                    LifeMetric("待复习", dueCards.size.toString(), Modifier.weight(1f))
                }
            }
            item {
                OutlinedTextField(query, model::setSearch, Modifier.fillMaxWidth(), placeholder = { Text("搜索生活记录") },
                    singleLine = true, shape = RoundedCornerShape(18.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onWishes) { Text("购物 · 存钱计划") }
                    TextButton(onClick = onTasks) { Text("待办事项") }
                }
            }
            if (state.loading) item { Loading() }
            state.error?.let { item { Message(it, true) } }
            message?.let { item { Message(it) } }
            if (editorKind == null && !focusEditor && !cardEditor) error?.let { item { Message(it, true) } }
            item {
                LifeGroup("计划", "${plans.size} 个", plansOpen, { plansOpen = !plansOpen }) {
                    if (plans.isEmpty()) LifeEmpty("安排一次旅行、一个课程或一本书", onClick = { creating = true })
                    plans.forEachIndexed { index, item ->
                        LifeItemHeader(item, onEdit = { edit(item) }, onDelete = { deletingId = item.id }, busy = busy) {
                            Text(if (item.isComplete) "已完成" else "进行中", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        if (item.kind == LifeKind.TRAVEL) LifeSmallText(listOfNotNull(item.location, item.amountMinor?.let { "预算 ¥${visibleAmount(it)}" }, item.date.toString()).joinToString(" · "))
                        Text("${item.progressCount}/${item.targetCount} ${item.unit}", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                        Row {
                            TextButton(enabled = !busy && item.progressCount > 0, onClick = { model.setProgress(item, item.progressCount - 1) }) { Text("−1 ${item.unit}") }
                            TextButton(enabled = !busy && !item.isComplete, onClick = { model.setProgress(item, item.progressCount + 1) }) { Text("+1 ${item.unit}") }
                            TextButton(enabled = !busy && !item.isComplete, onClick = { model.setProgress(item, requireNotNull(item.targetCount)) }) { Text("完成目标") }
                        }
                        if (index != plans.lastIndex) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            item {
                LifeGroup("时间", "${times.size} 个事件", timesOpen, { timesOpen = !timesOpen }) {
                    if (times.isEmpty()) LifeEmpty("生日、纪念日与重要日期", onClick = { creating = true })
                    times.forEachIndexed { index, item ->
                        LifeItemHeader(item, { edit(item) }, { deletingId = item.id }, busy) {
                            val days = lifeEventDays(item, today)
                            Text(when {
                                days == 0L -> "今天"
                                item.kind == LifeKind.COUNTUP && days >= 0 -> "${days}天"
                                days > 0 -> "还有${days}天"
                                item.kind == LifeKind.COUNTUP -> "还有${-days}天"
                                else -> "已过${-days}天"
                            }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        LifeSmallText(item.date.toString() + if (item.repeatYearly) " · 每年重复" else "")
                        item.note?.let { LifeSmallText(it) }
                        if (index != times.lastIndex) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            item {
                LifeGroup("记录", "${records.size} 个", recordsOpen, { recordsOpen = !recordsOpen }) {
                    if (records.isEmpty()) LifeEmpty("打卡、心情、日记与订阅", onClick = { creating = true })
                    records.forEachIndexed { index, item ->
                        LifeItemHeader(item, { edit(item) }, { deletingId = item.id }, busy) {
                            when (item.kind) {
                                LifeKind.MOOD -> Text(moodLabel(requireNotNull(item.mood)), color = MaterialTheme.colorScheme.primary)
                                LifeKind.SUBSCRIPTION -> Text("¥${visibleAmount(requireNotNull(item.amountMinor))}", fontWeight = FontWeight.SemiBold)
                                else -> Unit
                            }
                        }
                        when (item.kind) {
                            LifeKind.HABIT -> {
                                val checks = state.checkIns.filter { it.itemId == item.id && !it.isDeleted }.map { it.dateEpochDay }
                                LifeSmallText("连续 ${habitStreak(checks, today)} 天 · 累计 ${checks.size} 天")
                                Row {
                                    TextButton(enabled = !busy && today.toEpochDay() >= item.dateEpochDay, onClick = { model.toggleCheckIn(item, today) }) {
                                        Text(if (today.toEpochDay() in checks) "今日已打卡 ✓" else "今日打卡")
                                    }
                                    TextButton(onClick = { habitsOpen = if (habitsOpen == item.id) null else item.id }) { Text(if (habitsOpen == item.id) "收起记录" else "打卡日历") }
                                }
                                if (habitsOpen == item.id) HabitHeatmap(item, checks.toSet(), today, busy) { day -> model.toggleCheckIn(item, day) }
                            }
                            LifeKind.SUBSCRIPTION -> {
                                LifeSmallText("续期 ${item.date} · 每 ${item.subscriptionCycleMonths} 个月")
                                if (item.date <= today) Text("已到期", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                TextButton(enabled = !busy, onClick = { model.renew(item) }) { Text("已续期，推进日期") }
                            }
                            else -> {
                                LifeSmallText(item.date.toString())
                                item.note?.let { Text(it, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                        if (index != records.lastIndex) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            item {
                LifeGroup("专注", "番茄钟") {
                    if (activeFocus == null) {
                        LifeSmallText("选定一件事，专注 1 至 180 分钟")
                        Button(onClick = { model.clearFeedback(); focusEditor = true }, enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = lifeOrange, contentColor = Color(0xFF442B19))) { Text("开始专注") }
                    } else {
                        Text(activeFocus.title, style = MaterialTheme.typography.titleMedium)
                        val remaining = activeFocus.remainingSeconds(state.now)
                        Text("${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.headlineLarge)
                        Text(if (activeFocus.status == FocusStatus.PAUSED) "已暂停" else "专注中", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { activeFocus.elapsedAt(state.now).toFloat() / activeFocus.durationMillis }, modifier = Modifier.fillMaxWidth())
                        Row {
                            TextButton(enabled = !busy, onClick = { if (activeFocus.status == FocusStatus.RUNNING) model.pauseFocus(activeFocus) else model.resumeFocus(activeFocus) }) {
                                Text(if (activeFocus.status == FocusStatus.RUNNING) "暂停" else "继续")
                            }
                            if (remaining == 0L) TextButton(enabled = !busy, onClick = model::completeFocus) { Text("完成") }
                            TextButton(enabled = !busy, onClick = { deletingFocus = activeFocus.id }) { Text("放弃") }
                        }
                    }
                    val completed = state.focusSessions.filter { it.status == FocusStatus.COMPLETED }
                    TextButton(onClick = { focusHistory = !focusHistory }) { Text("已完成 ${completed.size} 次 · ${if (focusHistory) "收起" else "查看记录"}") }
                    if (focusHistory) completed.take(50).forEach { session ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(session.title, style = MaterialTheme.typography.bodyMedium)
                                LifeSmallText("${Instant.ofEpochMilli(requireNotNull(session.completedAt)).atZone(zone).toLocalDate()} · ${session.durationSeconds / 60} 分钟")
                            }
                            TextButton(enabled = !busy, onClick = { deletingFocus = session.id }) { Text("删除") }
                        }
                    }
                    if (focusHistory && completed.size > 50) LifeSmallText("显示最近 50 次，周/月总结包含全部已完成记录")
                }
            }
            item {
                LifeGroup("单词卡", "英语 · 日语") {
                    val mastered = state.cards.count { it.isMastered }
                    Text("已掌握 $mastered/${state.cards.size} · 待复习 ${dueCards.size}")
                    LinearProgressIndicator(progress = { if (state.cards.isEmpty()) 0f else mastered.toFloat() / state.cards.size }, modifier = Modifier.fillMaxWidth())
                    FlowRow {
                        TextButton(onClick = { cardId = null; cardEditor = true; model.clearFeedback() }, enabled = !busy) { Text("自建单词卡") }
                        TextButton(onClick = { reviewing = true }, enabled = !busy && dueCards.isNotEmpty()) { Text("复习到期卡片") }
                        TextButton(onClick = { cardList = !cardList }) { Text(if (cardList) "收起单词" else "查看单词") }
                    }
                    LifeSmallText("按 1、3、7、14、30、60 天复习；忘记后次日重学。达到 30 天间隔计为已掌握。")
                    if (cardList) state.cards.filter { query.isBlank() || it.word.contains(query, true) || it.meaning.contains(query, true) }.forEach { card ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { cardId = card.id; cardEditor = true; model.clearFeedback() }.padding(vertical = 8.dp)) {
                                Text(card.word, fontWeight = FontWeight.SemiBold)
                                LifeSmallText("${if (card.language == "en") "英语" else "日语"} · ${card.meaning}")
                                LifeSmallText("复习 ${LocalDate.ofEpochDay(card.dueEpochDay)}${if (card.isMastered) " · 已掌握" else ""}")
                            }
                            TextButton(enabled = !busy, onClick = { deletingCard = card.id }) { Text("删除") }
                        }
                    }
                }
            }
            item {
                val summary = lifeSummary(state.items, state.checkIns, state.focusSessions, state.cards, today, monthly)
                LifeGroup(if (monthly) "月报" else "周报", "本地记录") {
                    Row {
                        FilterChip(!monthly, { model.setMonthlySummary(false) }, label = { Text("本周") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(monthly, { model.setMonthlySummary(true) }, label = { Text("本月") })
                    }
                    LifeSmallText("${summary.start} — ${minOf(today, summary.endExclusive.minusDays(1))}")
                    Text("习惯打卡 ${summary.checkIns} 次，覆盖 ${summary.habitDays} 天")
                    Text("完成专注 ${summary.focusSessions} 次，共 ${summary.focusMinutes} 分钟")
                    Text("日记 ${summary.diaryEntries} 篇，心情 ${summary.moodEntries} 条")
                    summary.averageMood?.let { Text("平均心情 ${"%.1f".format(it)}/5") }
                    Text("本期复习过的单词 ${summary.studyReviews} 张")
                    Text("本期更新且已完成的计划 ${summary.completedPlans} 个")
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { model.clearFeedback(); creating = true },
            Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = lifeOrange, contentColor = Color(0xFF442B19)) { Text("＋ 新建") }
    }
    if (creating) LifeCreateDialog(onDismiss = { creating = false }, onSelect = ::new,
        onWishes = { creating = false; onWishes() }, onTasks = { creating = false; onTasks() },
        onFocus = { creating = false; focusEditor = true }, onStudy = { creating = false; cardId = null; cardEditor = true })
    editorKind?.let { kind ->
        val existing = state.items.find { it.id == editorId }
        if (editorId == null || existing != null) LifeItemDialog(LifeKind.valueOf(kind), existing, busy, error,
            onDismiss = { editorKind = null; editorId = null; model.clearFeedback() },
            onSave = { draft -> model.saveDraft(draft) { editorKind = null; editorId = null } })
    }
    state.items.find { it.id == deletingId }?.let { item ->
        LifeDeleteDialog(item.title, busy, error, onDismiss = { deletingId = null }, onConfirm = { model.delete(item) { deletingId = null } })
    }
    if (focusEditor) FocusCreateDialog(busy, error, { focusEditor = false; model.clearFeedback() }) { title, minutes -> model.startFocus(title, minutes) { focusEditor = false } }
    state.focusSessions.find { it.id == deletingFocus }?.let { session ->
        LifeDeleteDialog(session.title, busy, error, onDismiss = { deletingFocus = null }, onConfirm = { model.deleteFocus(session) { deletingFocus = null } },
            description = if (session.status == FocusStatus.COMPLETED) "删除后将不再计入专注总结。" else "本次计时将停止，不计入已完成专注。")
    }
    if (cardEditor) StudyCardDialog(state.cards.find { it.id == cardId }, busy, error, { cardEditor = false; cardId = null; model.clearFeedback() }) { language, word, meaning, note ->
        model.saveCard(cardId, language, word, meaning, note) { cardEditor = false; cardId = null }
    }
    if (reviewing) StudyReviewDialog(dueCards.firstOrNull(), busy, error, { reviewing = false; model.clearFeedback() }) { card, remembered -> model.review(card, remembered) }
    state.cards.find { it.id == deletingCard }?.let { card ->
        LifeDeleteDialog(card.word, busy, error, onDismiss = { deletingCard = null }, onConfirm = { model.deleteCard(card) { deletingCard = null } })
    }
}

@Composable
private fun LifeMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LifeGroup(title: String, subtitle: String, expanded: Boolean = true, toggle: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().then(if (toggle != null) Modifier.clickable(onClick = toggle) else Modifier), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(subtitle + if (toggle != null) if (expanded) "  ⌃" else "  ⌄" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun LifeItemHeader(item: LifeItem, onEdit: () -> Unit, onDelete: () -> Unit, busy: Boolean, suffix: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(3.dp).height(40.dp).background(lifeOrange, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 8.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            LifeSmallText(item.kind.label)
        }
        suffix()
    }
    Row {
        TextButton(enabled = !busy, onClick = onEdit) { Text("编辑") }
        TextButton(enabled = !busy, onClick = onDelete) { Text("删除") }
    }
}

@Composable
internal fun LifeSmallText(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun LifeEmpty(text: String, onClick: () -> Unit) {
    LifeSmallText(text)
    TextButton(onClick = onClick) { Text("添加第一条记录") }
}

internal fun moodLabel(mood: Int): String = when (mood) { 1 -> "难过"; 2 -> "低落"; 3 -> "平静"; 4 -> "开心"; else -> "很棒" }

@Composable
private fun HabitHeatmap(item: LifeItem, checked: Set<Long>, today: LocalDate, busy: Boolean, onToggle: (LocalDate) -> Unit) {
    val first = today.minusDays(83)
    LifeSmallText("最近 12 周 · ${first} 至 $today · 点击日期补记或取消")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(12) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { offset ->
                    val day = first.plusDays((week * 7 + offset).toLong())
                    val enabled = !busy && day.toEpochDay() >= item.dateEpochDay
                    val completed = day.toEpochDay() in checked
                    Box(Modifier.weight(1f).aspectRatio(1f).background(
                        if (completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(5.dp))
                        .clickable(enabled = enabled) { onToggle(day) }
                        .semantics { contentDescription = "$day ${if (completed) "已打卡" else "未打卡"}" }, contentAlignment = Alignment.Center) {
                        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall,
                            color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f) else if (completed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
