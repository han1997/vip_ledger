package com.han1997.vipledger.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.han1997.vipledger.notify.ReminderNotifier
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

/**
 * 每日后台任务：推进周期规则生成流水 + 生成提醒通知。
 *
 * 两个动作顺序执行：先生成（可能产生新的待确认草稿），再按最新的分期 / 账户状态
 * 算提醒。任何一步出错都不影响另一步，整体返回 `Result.success()` 避免重试风暴。
 */
class RecurringDailyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            RecurringWorkEntryPoint::class.java,
        )
        return try {
            val ledgers = entryPoint.observeLedgers()(includeArchived = false).first()
            ledgers.forEach { ledger ->
                entryPoint.generateRecurringTxns()(ledger.id)
            }
            val current = entryPoint.observeCurrentLedger()().first() ?: ledgers.firstOrNull()
            val reminders = current
                ?.let { ledger -> entryPoint.observeReminders()(ledger.id).first() }
                .orEmpty()
            ReminderNotifier.notify(applicationContext, reminders)
            Result.success()
        } catch (_: Throwable) {
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME: String = "vipledger.recurring.daily"
    }
}
