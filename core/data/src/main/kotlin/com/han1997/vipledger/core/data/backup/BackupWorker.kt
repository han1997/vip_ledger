package com.han1997.vipledger.core.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * 定时备份 worker：执行一次远端备份并写入有界日志。
 *
 * 失败时最多重试 [MAX_ATTEMPTS] 次，超过则记为 `failure`，避免 `Result.retry()` 无限重试占满队列。
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = EntryPointAccessors.fromApplication(applicationContext, BackupWorkerEntryPoint::class.java)
            .backupIoManager()
        val log = BackupLogStore(applicationContext)
        return try {
            val name = manager.backupRemote()
            log.append(BackupLogEntry(success = true, time = System.currentTimeMillis(), message = name))
            Result.success()
        } catch (error: Exception) {
            val attempt = runAttemptCount + 1
            log.append(
                BackupLogEntry(
                    success = false,
                    time = System.currentTimeMillis(),
                    message = error.message.orEmpty(),
                ),
            )
            if (attempt < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "vipledger.backup.periodic"
        const val MAX_ATTEMPTS = 3
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupWorkerEntryPoint {
    fun backupIoManager(): BackupIoManager
}

object BackupScheduler {
    fun configure(context: Context, enabled: Boolean, wifiOnly: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(BackupWorker.UNIQUE_NAME)
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(
            if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
        ).build()
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints).build()
        work.enqueueUniquePeriodicWork(BackupWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
