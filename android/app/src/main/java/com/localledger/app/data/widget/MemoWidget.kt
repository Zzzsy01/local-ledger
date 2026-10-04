package com.localledger.app.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.localledger.app.LedgerApplication
import com.localledger.app.MainActivity
import com.localledger.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class MemoWidget : AppWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, widgetId: Int, options: android.os.Bundle) { refresh(context) }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in listOf(AppWidgetManager.ACTION_APPWIDGET_UPDATE, ACTION_REFRESH, ACTION_DONE)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as LedgerApplication
                if (intent.action == ACTION_DONE && !app.settingsRepository.settings.value.appLock) {
                    intent.getStringExtra("id")?.let { id -> app.memoRepository.memo(id)?.let { app.memoRepository.setDone(id, !it.isDone) } }
                }
                val locked = app.settingsRepository.settings.value.appLock
                val rows = if (locked) emptyList() else app.memoRepository.memos.first().filter { it.kind == "todo" && !it.isDone }.take(3)
                val manager = AppWidgetManager.getInstance(context)
                manager.getAppWidgetIds(ComponentName(context, MemoWidget::class.java)).forEach { widgetId ->
                    val view = RemoteViews(context.packageName, R.layout.memo_widget)
                    fun open(action: String, code: Int) = PendingIntent.getActivity(context, code,
                        Intent(context, MainActivity::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    view.setOnClickPendingIntent(R.id.widget_title, open(ACTION_TASKS, 2100))
                    view.setOnClickPendingIntent(R.id.widget_add, open(ACTION_TASK, 2101))
                    val ids = intArrayOf(R.id.widget_row1, R.id.widget_row2, R.id.widget_row3)
                    val height = manager.getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)
                    val visibleRows = if (height >= 224) 3 else if (height >= 176) 2 else 1
                    ids.forEachIndexed { index, viewId ->
                        view.setViewVisibility(viewId, if (index < visibleRows) android.view.View.VISIBLE else android.view.View.GONE)
                        val item = rows.getOrNull(index)
                        view.setTextViewText(viewId, item?.let { "□  ${it.title}" } ?: if (index == 0) {
                            if (locked) "打开应用解锁查看待办" else "今天没有未完成待办"
                        } else "")
                        val click = if (item == null) open(ACTION_TASKS, 2100) else PendingIntent.getBroadcast(context,
                            item.id.hashCode(), Intent(context, MemoWidget::class.java).setAction(ACTION_DONE).putExtra("id", item.id),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        view.setOnClickPendingIntent(viewId, click)
                    }
                    manager.updateAppWidget(widgetId, view)
                }
            } finally { pending.finish() }
        }
    }
    companion object {
        const val ACTION_REFRESH = "com.localledger.app.widget.REFRESH"
        const val ACTION_DONE = "com.localledger.app.widget.DONE"
        const val ACTION_TASK = "com.localledger.app.action.TASK"
        const val ACTION_TASKS = "com.localledger.app.action.TASKS"
        fun refresh(context: Context) { context.sendBroadcast(Intent(context, MemoWidget::class.java).setAction(ACTION_REFRESH)) }
    }
}
