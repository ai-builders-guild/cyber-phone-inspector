package com.cyberrick.phoneinspector.feeds

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * The optional weekly refresh. Off by default; the person turns it on from the Lists screen. Unmetered network only.
 * Not persisted across reboots (that would need the boot permission, which is not on the allowlist); the app
 * re schedules it on launch while the switch is on.
 */
object WeeklyRefresh {
    private const val JOB_ID = 1101
    const val PREF = "weekly_refresh"

    fun isOn(context: Context): Boolean = context.getSharedPreferences("cpi", Context.MODE_PRIVATE).getBoolean(PREF, false)

    /** Called on launch: a scheduled job does not survive a reboot, so it is scheduled again while the switch is on. */
    fun ensure(context: Context) {
        if (isOn(context)) set(context, true)
    }

    fun set(
        context: Context,
        on: Boolean,
    ) {
        context.getSharedPreferences("cpi", Context.MODE_PRIVATE).edit { putBoolean(PREF, on) }
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!on) {
            scheduler.cancel(JOB_ID)
            return
        }
        val job =
            JobInfo
                .Builder(JOB_ID, ComponentName(context, FeedRefreshJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPeriodic(TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(1))
                .build()
        scheduler.schedule(job)
    }
}

class FeedRefreshJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running =
            scope.launch {
                try {
                    FeedFiles(applicationContext).refresher().refresh()
                } finally {
                    jobFinished(params, false)
                }
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }
}
