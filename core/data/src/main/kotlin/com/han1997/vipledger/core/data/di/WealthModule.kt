package com.han1997.vipledger.core.data.di

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.data.repository.WealthRepositoryImpl
import com.han1997.vipledger.core.domain.repository.WealthRepository
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.domain.usecase.wealth.ObserveAssetsUseCase
import com.han1997.vipledger.core.domain.usecase.wealth.ObserveSavingsUseCase
import com.han1997.vipledger.core.domain.usecase.wealth.SaveSavingsGoalUseCase
import com.han1997.vipledger.core.domain.usecase.wealth.MoveSavingsUseCase
import com.han1997.vipledger.core.domain.usecase.wealth.SavingsTxnGuard
import com.han1997.vipledger.core.domain.usecase.wealth.HomeCardUseCases
import com.han1997.vipledger.core.domain.usecase.wealth.AdjustAccountBalanceUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object WealthModule {
    @Provides @Singleton fun repository(db: LedgerDatabase): WealthRepository = WealthRepositoryImpl(db)
    @Provides fun assets(accounts: AccountRepository, wealth: WealthRepository, time: TimeProvider) = ObserveAssetsUseCase(accounts, wealth, time)
    @Provides fun goals(wealth: WealthRepository, txns: TxnRepository) = ObserveSavingsUseCase(wealth, txns)
    @Provides fun saveGoal(wealth: WealthRepository, accounts: AccountRepository, runner: TransactionRunner, time: TimeProvider, ids: IdGenerator) = SaveSavingsGoalUseCase(wealth, accounts, runner, time, ids)
    @Provides fun movement(wealth: WealthRepository, txns: TxnRepository, accounts: AccountRepository, create: CreateTxnUseCase, runner: TransactionRunner, time: TimeProvider) = MoveSavingsUseCase(wealth, txns, accounts, create, runner, time)
    @Provides fun guard(wealth: WealthRepository, txns: TxnRepository) = SavingsTxnGuard(wealth, txns)
    @Provides fun cards(wealth: WealthRepository, ledgers: LedgerRepository) = HomeCardUseCases(wealth, ledgers)
    @Provides fun adjust(accounts: AccountRepository, create: CreateTxnUseCase, runner: TransactionRunner) = AdjustAccountBalanceUseCase(accounts, create, runner)
}
