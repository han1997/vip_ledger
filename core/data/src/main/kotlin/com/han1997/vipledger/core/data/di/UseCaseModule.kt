package com.han1997.vipledger.core.data.di

import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.InstallmentRepository
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.repository.TagRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.BuiltinCategorySource
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.usecase.EnsureInitialDataUseCase
import com.han1997.vipledger.core.domain.usecase.account.RecomputeAccountBalancesUseCase
import com.han1997.vipledger.core.domain.usecase.loan.SaveInstallmentPlanUseCase
import com.han1997.vipledger.core.domain.usecase.search.SearchTxnsUseCase
import com.han1997.vipledger.core.domain.usecase.stats.BuildTxnCsvUseCase
import com.han1997.vipledger.core.domain.usecase.stats.ObserveStatsReportUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.DeleteTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.GetTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.ObserveTxnListUseCase
import com.han1997.vipledger.core.domain.usecase.txn.UpdateTxnUseCase
import com.han1997.vipledger.core.common.time.TimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 用例是无状态的纯逻辑类（`:core:domain` 是纯 JVM 模块，不引 Hilt 注解），
 * 所以在数据层统一提供实例。
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideEnsureInitialDataUseCase(
        ledgerRepository: LedgerRepository,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        builtinCategorySource: BuiltinCategorySource,
        transactionRunner: TransactionRunner,
        timeProvider: TimeProvider,
        idGenerator: IdGenerator,
    ) = EnsureInitialDataUseCase(
        ledgerRepository = ledgerRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        builtinCategorySource = builtinCategorySource,
        transactionRunner = transactionRunner,
        timeProvider = timeProvider,
        idGenerator = idGenerator,
    )

    @Provides
    @Singleton
    fun provideCreateTxnUseCase(
        txnRepository: TxnRepository,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        transactionRunner: TransactionRunner,
        balanceSyncer: AccountBalanceSyncer,
        timeProvider: TimeProvider,
        idGenerator: IdGenerator,
    ) = CreateTxnUseCase(
        txnRepository = txnRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        transactionRunner = transactionRunner,
        balanceSyncer = balanceSyncer,
        timeProvider = timeProvider,
        idGenerator = idGenerator,
    )

    @Provides
    @Singleton
    fun provideUpdateTxnUseCase(
        txnRepository: TxnRepository,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        transactionRunner: TransactionRunner,
        balanceSyncer: AccountBalanceSyncer,
        timeProvider: TimeProvider,
    ) = UpdateTxnUseCase(
        txnRepository = txnRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        transactionRunner = transactionRunner,
        balanceSyncer = balanceSyncer,
        timeProvider = timeProvider,
    )

    @Provides
    @Singleton
    fun provideDeleteTxnUseCase(
        txnRepository: TxnRepository,
        transactionRunner: TransactionRunner,
        balanceSyncer: AccountBalanceSyncer,
        timeProvider: TimeProvider,
    ) = DeleteTxnUseCase(
        txnRepository = txnRepository,
        transactionRunner = transactionRunner,
        balanceSyncer = balanceSyncer,
        timeProvider = timeProvider,
    )

    @Provides
    @Singleton
    fun provideObserveTxnListUseCase(txnRepository: TxnRepository) = ObserveTxnListUseCase(txnRepository)

    @Provides
    @Singleton
    fun provideGetTxnUseCase(txnRepository: TxnRepository) = GetTxnUseCase(txnRepository)

    @Provides
    @Singleton
    fun provideRecomputeAccountBalancesUseCase(
        accountRepository: AccountRepository,
        txnRepository: TxnRepository,
        transactionRunner: TransactionRunner,
        timeProvider: TimeProvider,
    ) = RecomputeAccountBalancesUseCase(
        accountRepository = accountRepository,
        txnRepository = txnRepository,
        transactionRunner = transactionRunner,
        timeProvider = timeProvider,
    )

    @Provides
    @Singleton
    fun provideSearchTxnsUseCase(
        txnRepository: TxnRepository,
        categoryRepository: CategoryRepository,
        timeProvider: TimeProvider,
    ) = SearchTxnsUseCase(
        txnRepository = txnRepository,
        categoryRepository = categoryRepository,
        timeProvider = timeProvider,
    )

    @Provides
    @Singleton
    fun provideObserveStatsReportUseCase(
        txnRepository: TxnRepository,
        categoryRepository: CategoryRepository,
        tagRepository: TagRepository,
        timeProvider: TimeProvider,
    ) = ObserveStatsReportUseCase(
        txnRepository = txnRepository,
        categoryRepository = categoryRepository,
        tagRepository = tagRepository,
        timeProvider = timeProvider,
    )

    @Provides
    @Singleton
    fun provideBuildTxnCsvUseCase(timeProvider: TimeProvider) = BuildTxnCsvUseCase(timeProvider)
    @Provides
    @Singleton
    fun provideSaveInstallmentPlanUseCase(
        installmentRepository: InstallmentRepository,
        accountRepository: AccountRepository,
        transactionRunner: TransactionRunner,
        timeProvider: TimeProvider,
        idGenerator: IdGenerator,
    ) = SaveInstallmentPlanUseCase(
        installmentRepository = installmentRepository,
        accountRepository = accountRepository,
        transactionRunner = transactionRunner,
        timeProvider = timeProvider,
        idGenerator = idGenerator,
    )
}