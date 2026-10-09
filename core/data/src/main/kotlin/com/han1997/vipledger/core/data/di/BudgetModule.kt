package com.han1997.vipledger.core.data.di

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.data.repository.BudgetRepositoryImpl
import com.han1997.vipledger.core.domain.repository.BudgetRepository
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.usecase.budget.ObserveBudgetUseCase
import com.han1997.vipledger.core.domain.usecase.budget.SaveBudgetUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObserveCalendarUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObserveLocalDayUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BudgetModule {
    @Provides fun pendingAuto(repository: com.han1997.vipledger.core.domain.autobook.AutoBookRepository) = com.han1997.vipledger.core.domain.usecase.home.ObservePendingAutoCountUseCase(repository)
    @Provides @Singleton fun budgets(db: LedgerDatabase): BudgetRepository = BudgetRepositoryImpl(db.budgetDao())
    @Provides fun observeBudget(budgets: BudgetRepository, txns: TxnRepository, categories: CategoryRepository) = ObserveBudgetUseCase(budgets, txns, categories)
    @Provides fun saveBudget(budgets: BudgetRepository, ledgers: LedgerRepository, categories: CategoryRepository, transaction: TransactionRunner, time: TimeProvider, ids: IdGenerator) = SaveBudgetUseCase(budgets, ledgers, categories, transaction, time, ids)
    @Provides fun calendar(txns: TxnRepository) = ObserveCalendarUseCase(txns)
    @Provides fun localDay(time: TimeProvider) = ObserveLocalDayUseCase(time)
}
