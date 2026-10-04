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

class PlanningReminderJob : JobService() {
    private var task: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        task = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!PlanningReminders.isEnabled(this@PlanningReminderJob)) return@launch
                val due = (application as LedgerApplication).planningRepository.dueRules()
                val notifications = getSystemService(NotificationManager::class.java)
                if (due.isEmpty()) { notifications.cancel(1102); return@launch }
                if (!notifications.areNotificationsEnabled()) return@launch
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@launch
                notifications.createNotificationChannel(NotificationChannel("planning", "固定收支到期提醒", NotificationManager.IMPORTANCE_DEFAULT))
                val intent = Intent(this@PlanningReminderJob, MainActivity::class.java).setAction(ACTION_PLANNING)
                val pending = PendingIntent.getActivity(this@PlanningReminderJob, 1102, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                notifications.notify(1102, NotificationCompat.Builder(this@PlanningReminderJob, "planning")
                    .setSmallIcon(R.drawable.ic_launcher).setContentTitle("${due.size} 项固定收支待处理")
                    .setContentText(due.take(3).joinToString("、") { it.name } + " · 点击确认记账或跳过")
                    .setContentIntent(pending).setAutoCancel(true).build())
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { Log.w("PlanningReminder", "Due reminder failed", error) }
            finally { jobFinished(params, false) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { task?.cancel(); return false }
    override fun onDestroy() { task?.cancel(); super.onDestroy() }
    companion object { const val ACTION_PLANNING = "com.localledger.app.PLANNING" }
}
