package com.localledger.app.data.update

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.localledger.app.LedgerApplication
import com.localledger.app.MainActivity
import com.localledger.app.R
import kotlinx.coroutines.*

class UpdateCheckJob : JobService() {
    private var task: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        task = CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = (application as LedgerApplication).updateRepository
                if (!repository.automatic || repository.source.isBlank()) return@launch
                val update = repository.check() ?: return@launch
                val notifications = getSystemService(NotificationManager::class.java)
                if (!notifications.areNotificationsEnabled()) return@launch
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@launch
                notifications.createNotificationChannel(NotificationChannel("updates", "应用更新", NotificationManager.IMPORTANCE_DEFAULT))
                val intent = Intent(this@UpdateCheckJob, MainActivity::class.java).setAction(ACTION_UPDATES)
                val pending = PendingIntent.getActivity(this@UpdateCheckJob, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                notifications.notify(1101, NotificationCompat.Builder(this@UpdateCheckJob, "updates")
                    .setSmallIcon(R.drawable.ic_launcher).setContentTitle("随手账 ${update.versionName} 可更新")
                    .setContentText("点击查看更新内容，下载后覆盖安装").setContentIntent(pending).setAutoCancel(true).build())
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { Log.w("AppUpdate", "Update check failed", error) }
            finally { jobFinished(params, false) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { task?.cancel(); return false }
    override fun onDestroy() { task?.cancel(); super.onDestroy() }
    companion object { const val ACTION_UPDATES = "com.localledger.app.UPDATES" }
}
