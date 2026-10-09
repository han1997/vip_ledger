package com.han1997.vipledger.notify

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.han1997.vipledger.MainActivity
import com.han1997.vipledger.R
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.repository.BudgetRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.usecase.budget.ObserveBudgetUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObserveLocalDayUseCase
import com.han1997.vipledger.core.domain.model.BudgetReport
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** 进程存活时观察账目更新（含后台采集）；系统通知不可用时仍由页面展示实时预算状态。 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BudgetMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ledgers: LedgerRepository,
    private val categories: CategoryRepository,
    private val budgets: BudgetRepository,
    private val observe: ObserveBudgetUseCase,
    private val day: ObserveLocalDayUseCase,
    private val time: TimeProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    fun start() {
        if (job != null) return
        job = scope.launch {
            ledgers.observeAll().flatMapLatest { all ->
                if (all.isEmpty()) emptyFlow<BudgetReport>()
                else merge(*all.map { ledger -> day().flatMapLatest { local -> observe(ledger.id, local.date, local.zone) } }.toTypedArray())
            }.onEach(::notifyReport).retryWhen { _, _ -> delay(10_000); true }.collect()
        }
    }
    private suspend fun notifyReport(report: BudgetReport) {
        if (!canNotify()) return
        for (progress in report.progress) {
            var notifyThreshold: Int? = null
            for (threshold in listOf(80, 100)) {
                if (!progress.reached(threshold)) continue
                val claimed = budgets.claimAlert(report.ledgerId, report.month, progress.limit.scopeKey, threshold, time.nowMillis())
                if (claimed is AppResult.Success && claimed.value) notifyThreshold = threshold
            }
            val threshold = notifyThreshold ?: continue
            val title = if (progress.limit.scopeKey.isEmpty()) context.getString(R.string.notify_budget_total)
                else categories.getById(progress.limit.scopeKey)?.name ?: context.getString(R.string.notify_budget_category)
            val ledgerName = ledgers.getById(report.ledgerId)?.name.orEmpty()
            val intent = Intent(context, MainActivity::class.java).putExtra("openBudgetLedger", report.ledgerId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val id = "${report.ledgerId}/${report.month}/${progress.limit.scopeKey}/$threshold".hashCode()
            val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, ReminderNotifier.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notify_budget_title, ledgerName, title))
                .setContentText(context.getString(R.string.notify_budget_body, threshold, progress.spent.toDisplayString(), requireNotNull(progress.limit.amount).toDisplayString()))
                .setContentIntent(pending).setAutoCancel(true).build()
            try { NotificationManagerCompat.from(context).notify("budget", id, notification) }
            catch (_: SecurityException) { /* 权限可能在检查之后撤销；进度仍可在应用内读取。 */ }
        }
    }
    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() && manager.getNotificationChannel(ReminderNotifier.CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }
}
