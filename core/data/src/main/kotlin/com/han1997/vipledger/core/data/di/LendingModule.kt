package com.han1997.vipledger.core.data.di

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.data.repository.LendingRepositoryImpl
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.LendingRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.usecase.lending.CloseLendingUseCase
import com.han1997.vipledger.core.domain.usecase.lending.CreateLendingUseCase
import com.han1997.vipledger.core.domain.usecase.lending.LendingTxnGuard
import com.han1997.vipledger.core.domain.usecase.lending.ObserveLendingsUseCase
import com.han1997.vipledger.core.domain.usecase.lending.RecordLendingInterestUseCase
import com.han1997.vipledger.core.domain.usecase.lending.SettleLendingUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LendingModule {
    @Provides
    @Singleton
    fun repository(db: LedgerDatabase): LendingRepository = LendingRepositoryImpl(db)

    @Provides
    fun observe(repository: LendingRepository, txns: TxnRepository) = ObserveLendingsUseCase(repository, txns)

    @Provides
    fun create(repository: LendingRepository, accounts: AccountRepository, createTxn: CreateTxnUseCase, runner: TransactionRunner, time: TimeProvider, ids: IdGenerator) =
        CreateLendingUseCase(repository, accounts, createTxn, runner, time, ids)

    @Provides
    fun settle(repository: LendingRepository, txns: TxnRepository, accounts: AccountRepository, createTxn: CreateTxnUseCase, runner: TransactionRunner, time: TimeProvider) =
        SettleLendingUseCase(repository, txns, accounts, createTxn, runner, time)

    @Provides
    fun interest(repository: LendingRepository, accounts: AccountRepository, createTxn: CreateTxnUseCase, runner: TransactionRunner, time: TimeProvider) =
        RecordLendingInterestUseCase(repository, accounts, createTxn, runner, time)

    @Provides
    fun close(repository: LendingRepository, runner: TransactionRunner, time: TimeProvider) =
        CloseLendingUseCase(repository, runner, time)

    @Provides
    fun guard(repository: LendingRepository, txns: TxnRepository) = LendingTxnGuard(repository, txns)
}
