package com.han1997.vipledger.work

import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveLedgersUseCase
import com.han1997.vipledger.core.domain.usecase.recurring.GenerateRecurringTxnsUseCase
import com.han1997.vipledger.core.domain.usecase.reminder.ObserveRemindersUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 后台任务取依赖用的入口。
 *
 * 项目不引 `hilt-work`（避免额外的版本约束），WorkManager 用默认 WorkerFactory
 * 反射建 `CoroutineWorker`，依赖在 worker 里通过本入口从 Application 组件取。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecurringWorkEntryPoint {
    fun observeLedgers(): ObserveLedgersUseCase

    fun observeCurrentLedger(): ObserveCurrentLedgerUseCase

    fun generateRecurringTxns(): GenerateRecurringTxnsUseCase

    fun observeReminders(): ObserveRemindersUseCase
}
