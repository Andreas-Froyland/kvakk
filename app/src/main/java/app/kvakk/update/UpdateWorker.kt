package app.kvakk.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kvakk.media.SleepTimer
import java.util.concurrent.TimeUnit

/**
 * Daily automatic update check while the phone is idle, charging and on Wi-Fi, so it happens
 * overnight rather than while a child is using Kvakk.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Updating restarts Kvakk, which would cancel a running sleep timer.
        if (SleepTimer.endsAt.value != null) return Result.retry()
        val status = Updater.checkAndInstall(applicationContext)
        // Try again soon after temporary problems; anything else waits for tomorrow.
        val temporary = status is UpdateStatus.Failed &&
            status.reason in setOf(UpdateStatus.Reason.NETWORK, UpdateStatus.Reason.INSTALL_FAILED)
        return if (temporary) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "kvakk-auto-update"

        fun schedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresCharging(true)
                        .setRequiresDeviceIdle(true)
                        .build(),
                )
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
