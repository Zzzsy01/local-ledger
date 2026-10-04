package com.localledger.app.data.reminder

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit

object PlanningReminders {
    private const val JOB_ID = 1102
    fun isEnabled(context: Context): Boolean = context.getSystemService(JobScheduler::class.java).getPendingJob(JOB_ID) != null
    fun configure(context: Context, enabled: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!enabled) { scheduler.cancel(JOB_ID); context.getSystemService(NotificationManager::class.java).cancel(JOB_ID); return }
        if (isEnabled(context)) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, PlanningReminderJob::class.java))
            .setPeriodic(TimeUnit.DAYS.toMillis(1)).setPersisted(true).build()
        require(scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS) { "无法开启到期提醒，请稍后重试。" }
    }
}
