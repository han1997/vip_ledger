package com.han1997.vipledger

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.han1997.vipledger.notify.ReminderNotifier
import com.han1997.vipledger.work.RecurringDailyWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 应用入口：注册通知渠道，并挂上「每日周期生成 + 提醒」的后台任务。
 *
 * 任务用 [ExistingPeriodicWorkPolicy.KEEP] 唯一排队，重复启动应用不会叠加。
 */
@HiltAndroidApp
class VipLedgerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.ensureChannel(this)
        scheduleDailyWork()
    }

    private fun scheduleDailyWork() {
        val request = PeriodicWorkRequestBuilder<RecurringDailyWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RecurringDailyWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** 距下次 09:00 的毫秒数（按设备时区）。 */
    private fun initialDelayMillis(): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
