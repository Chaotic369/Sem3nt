package com.sem3.planner

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

class RefreshJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                Refresher.run(applicationContext)
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}

object Scheduler {
    private const val JOB_ID = 1

    fun schedule(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(JOB_ID) != null) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(ctx, RefreshJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(15 * 60 * 1000L)
            .setPersisted(true)
            .build()
        js.schedule(info)
    }
}
