package com.yichincho.skcheck.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yichincho.skcheck.data.Market
import com.yichincho.skcheck.data.Store
import java.util.concurrent.TimeUnit

/** Checks quotes during Taiwan market hours and sends any alerts that are due. */
class QuoteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Store.init(applicationContext)
        try {
            if (Market.isOpen() && Store.stocks.value.isNotEmpty()) Store.refreshAll(notify = true)
        } finally {
            // Intervals under 15 minutes run as a chain of one-off jobs, each booking the next.
            if (Store.settings.value.intervalMin < 15) Scheduler.next(applicationContext)
        }
        return Result.success()
    }
}

object Scheduler {
    private const val PERIODIC = "quotes-periodic"
    private const val SHORT = "quotes-short"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Sets up background checks for the interval in the settings. [replace] is for when the interval changed;
     * at app start it stays false, so a check that is already booked (or about to run) is left alone.
     */
    fun apply(context: Context, replace: Boolean = false) {
        val wm = WorkManager.getInstance(context)
        val minutes = Store.settings.value.intervalMin
        if (minutes >= 15) {
            wm.cancelUniqueWork(SHORT)
            val req = PeriodicWorkRequestBuilder<QuoteWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(PERIODIC)
            val req = OneTimeWorkRequestBuilder<QuoteWorker>().setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(network).build()
            wm.enqueueUniqueWork(SHORT, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
        }
    }

    /** Books the next short-interval check after the one now running (appending never cancels the running one). */
    fun next(context: Context) {
        val minutes = Store.settings.value.intervalMin.toLong().coerceAtLeast(5)
        val req = OneTimeWorkRequestBuilder<QuoteWorker>().setInitialDelay(minutes, TimeUnit.MINUTES)
            .setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(SHORT, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }
}
