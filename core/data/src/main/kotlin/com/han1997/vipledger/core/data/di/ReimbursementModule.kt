package com.han1997.vipledger.core.data.di

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.data.repository.ReimbursementRepositoryImpl
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.ReimbursementRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.usecase.reimburse.CloseReimbursementUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.ObserveReimbursementsUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.ReceiveReimbursementUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.ReimbursementTxnGuard
import com.han1997.vipledger.core.domain.usecase.reimburse.SaveReimbursementUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReimbursementModule {
    @Provides
    @Singleton
    fun repository(db: LedgerDatabase): ReimbursementRepository = ReimbursementRepositoryImpl(db)

    @Provides
    fun observe(repository: ReimbursementRepository, txns: TxnRepository) = ObserveReimbursementsUseCase(repository, txns)

    @Provides
    fun save(repository: ReimbursementRepository, txns: TxnRepository, runner: TransactionRunner, time: TimeProvider, ids: IdGenerator) =
        SaveReimbursementUseCase(repository, txns, runner, time, ids)

    @Provides
    fun receive(repository: ReimbursementRepository, txns: TxnRepository, accounts: AccountRepository, create: CreateTxnUseCase, runner: TransactionRunner) =
        ReceiveReimbursementUseCase(repository, txns, accounts, create, runner)

    @Provides
    fun close(repository: ReimbursementRepository, runner: TransactionRunner, time: TimeProvider) =
        CloseReimbursementUseCase(repository, runner, time)

    @Provides
    fun guard(repository: ReimbursementRepository, txns: TxnRepository) = ReimbursementTxnGuard(repository, txns)
}
