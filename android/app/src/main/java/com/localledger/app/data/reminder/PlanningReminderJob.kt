package com.localledger.app.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.localledger.app.LedgerApplication
import com.localledger.app.MainActivity
import com.localledger.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import com.localledger.app.domain.*

class PlanningReminderJob : JobService() {
    private var task: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        task = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!PlanningReminders.isEnabled(this@PlanningReminderJob)) return@launch
                val app = application as LedgerApplication
                val due = app.planningRepository.dueRules()
                val today = LocalDate.now()
                val life = app.lifeRepository.items.first()
                val reminders = buildList {
                    addAll(app.memoRepository.memos.first().filter { it.kind == "todo" && !it.isDone && it.dueEpochDay != null && it.dueEpochDay <= today.toEpochDay() }.map { "待办：${it.title}" })
                    addAll(life.filter { it.kind in lifeTimeKinds && it.kind != LifeKind.COUNTUP && lifeEventDays(it, today) in 0..3 }.map { "${it.kind.label}：${it.title}" })
                    addAll(life.filter { it.kind == LifeKind.SUBSCRIPTION && it.dateEpochDay <= today.plusDays(3).toEpochDay() }.map { "订阅：${it.title}" })
                    addAll(app.assetRepository.assets.first().filter { it.isHeld && it.warrantyUntil?.let(LocalDate::parse)?.let { date -> date >= today && date <= today.plusDays(7) } == true }.map { "保修到期：${it.name}" })
                }
                val notifications = getSystemService(NotificationManager::class.java)
                if (due.isEmpty() && reminders.isEmpty()) { notifications.cancel(1102); notifications.cancel(1103); return@launch }
                if (!notifications.areNotificationsEnabled()) return@launch
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@launch
                notifications.createNotificationChannel(NotificationChannel("planning", "生活与固定收支提醒", NotificationManager.IMPORTANCE_DEFAULT))
                if (reminders.isNotEmpty()) {
                    val open = PendingIntent.getActivity(this@PlanningReminderJob, 1103, Intent(this@PlanningReminderJob, MainActivity::class.java).setAction(ACTION_LIFE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    notifications.notify(1103, NotificationCompat.Builder(this@PlanningReminderJob, "planning").setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("${reminders.size} 项生活提醒").setContentText(if (app.settingsRepository.settings.value.appLock) "解锁应用查看待办与重要日期" else reminders.take(3).joinToString("、"))
                        .setContentIntent(open).setAutoCancel(true).build())
                } else notifications.cancel(1103)
                if (due.isEmpty()) { notifications.cancel(1102); return@launch }
                val intent = Intent(this@PlanningReminderJob, MainActivity::class.java).setAction(ACTION_PLANNING)
                val pending = PendingIntent.getActivity(this@PlanningReminderJob, 1102, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                notifications.notify(1102, NotificationCompat.Builder(this@PlanningReminderJob, "planning")
                    .setSmallIcon(R.drawable.ic_launcher).setContentTitle("${due.size} 项固定收支待处理")
                    .setContentText(if (app.settingsRepository.settings.value.appLock) "解锁应用查看到期账目" else due.take(3).joinToString("、") { it.name } + " · 点击确认记账或跳过")
                    .setContentIntent(pending).setAutoCancel(true).build())
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { Log.w("PlanningReminder", "Due reminder failed", error) }
            finally { jobFinished(params, false) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { task?.cancel(); return false }
    override fun onDestroy() { task?.cancel(); super.onDestroy() }
    companion object { const val ACTION_PLANNING = "com.localledger.app.PLANNING"; const val ACTION_LIFE = "com.localledger.app.LIFE" }
}
