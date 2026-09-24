package com.han1997.vipledger.core.data.di

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.repository.TagRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.BuiltinCategorySource
import com.han1997.vipledger.core.domain.support.CurrentLedgerStore
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.usecase.account.ArchiveAccountUseCase
import com.han1997.vipledger.core.domain.usecase.account.DeleteAccountUseCase
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.account.SaveAccountUseCase
import com.han1997.vipledger.core.domain.usecase.category.DeleteCategoryUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.category.SaveCategoryUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObserveHomeOverviewUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ArchiveLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.CreateLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.DeleteLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveLedgersUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.SelectLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.SetDefaultLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.UpdateLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.tag.DeleteTagUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.core.domain.usecase.tag.SaveTagUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * M2 记账主干的用例装配（账本 / 账户 / 分类 / 标签 / 首页概览）。
 * 用例本身是无状态纯逻辑类，见 [UseCaseModule] 的说明。
 */
@Module
@InstallIn(SingletonComponent::class)
object LedgerUseCaseModule {

    @Provides
    @Singleton
    fun provideObserveLedgersUseCase(ledgerRepository: LedgerRepository) =
        ObserveLedgersUseCase(ledgerRepository)

    @Provides
    @Singleton
    fun provideObserveCurrentLedgerUseCase(
        currentLedgerStore: CurrentLedgerStore,
        ledgerRepository: LedgerRepository,
    ) = ObserveCurrentLedgerUseCase(currentLedgerStore, ledgerRepository)

    @Provides
    @Singleton
    fun provideSelectLedgerUseCase(
        currentLedgerStore: CurrentLedgerStore,
        ledgerRepository: LedgerRepository,
    ) = SelectLedgerUseCase(currentLedgerStore, ledgerRepository)

    @Provides
    @Singleton
    fun provideCreateLedgerUseCase(
        ledgerRepository: LedgerRepository,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        builtinCategorySource: BuiltinCategorySource,
        transactionRunner: TransactionRunner,
        timeProvider: TimeProvider,
        idGenerator: IdGenerator,
    ) = CreateLedgerUseCase(
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
    fun provideUpdateLedgerUseCase(
        ledgerRepository: LedgerRepository,
        timeProvider: TimeProvider,
    ) = UpdateLedgerUseCase(ledgerRepository, timeProvider)

    @Provides
    @Singleton
    fun provideSetDefaultLedgerUseCase(
        ledgerRepository: LedgerRepository,
        transactionRunner: TransactionRunner,
        timeProvider: TimeProvider,
    ) = SetDefaultLedgerUseCase(ledgerRepository, transactionRunner, timeProvider)

    @Provides
    @Singleton
    fun provideArchiveLedgerUseCase(
        ledgerRepository: LedgerRepository,
        timeProvider: TimeProvider,
    ) = ArchiveLedgerUseCase(ledgerRepository, timeProvider)

    @Provides
    @Singleton
    fun provideDeleteLedgerUseCase(
        ledgerRepository: LedgerRepository,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        tagRepository: TagRepository,
        txnRepository: TxnRepository,
        transactionRunner: TransactionRunner,
        timeProvider: TimeProvider,
    ) = DeleteLedgerUseCase(
        ledgerRepository = ledgerRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        tagRepository = tagRepository,
        txnRepository = txnRepository,
        transactionRunner = transactionRunner,
        timeProvider = timeProvider,
    )

    @Provides
    @Singleton
    fun provideObserveAccountsUseCase(accountRepository: AccountRepository) =
        ObserveAccountsUseCase(accountRepository)

    @Provides
    @Singleton
    fun provideSaveAccountUseCase(
        accountRepository: AccountRepository,
        transactionRunner: TransactionRunner,
        balanceSyncer: AccountBalanceSyncer,
        timeProvider: TimeProvider,
        idGenerator: IdGenerator,
    ) = SaveAccountUseCase(
        accountRepository = accountRepository,
        transactionRunner = transactionRunner,
        balanceSyncer = balanceSyncer,
        timeProvider = timeProvider,
        idGenerator = idGenerator,
    )

    @Provides
    @Singleton
    fun provideArchiveAccountUseCase(
        accountRepository: AccountRepository,
        timeProvider: TimeProvider,
    ) = ArchiveAccountUseCase(accountRepository, timeProvider)

    @Provides
    @Singleton
    fun provideDeleteAccountUseCase(
        accountRepository: AccountRepository,
        txnRepository: TxnRepository,
        timeProvider: TimeProvider,
    ) = DeleteAccountUseCase(accountRepository, txnRepository, timeProvider)

    @Provides
    @Singleton
    fun provideObserveCategoryTreeUseCase(categoryRepository: CategoryRepository) =
        ObserveCategoryTreeUseCase(categoryRepository)

    @Provides
    @Singleton
    fun provideSaveCategoryUseCase(
        categoryRepository: CategoryRepository,
        timeProvider: TimeProvider,
        idGenerator: IdGenerator,
    ) = SaveCategoryUseCase(categoryRepository, timeProvider, idGenerator)

    @Provides
    @Singleton
    fun provideDeleteCategoryUseCase(
        categoryRepository: CategoryRepository,
        txnRepository: TxnRepository,
        transactionRunner: TransactionRunner,
        timeProvider: TimeProvider,
    ) = DeleteCategoryUseCase(categoryRepository, txnRepository, transactionRunner, timeProvider)

    @Provides
    @Singleton
    fun provideObserveTagsUseCase(tagRepository: TagRepository) = ObserveTagsUseCase(tagRepository)

    @Provides
    @Singleton
    fun provideSaveTagUseCase(
        tagRepository: TagRepository,
        timeProvider: TimeProvider,
        idGenerator: IdGenerator,
    ) = SaveTagUseCase(tagRepository, timeProvider, idGenerator)

    @Provides
    @Singleton
    fun provideDeleteTagUseCase(
        tagRepository: TagRepository,
        timeProvider: TimeProvider,
    ) = DeleteTagUseCase(tagRepository, timeProvider)

    @Provides
    @Singleton
    fun provideObserveHomeOverviewUseCase(
        ledgerRepository: LedgerRepository,
        accountRepository: AccountRepository,
        txnRepository: TxnRepository,
        timeProvider: TimeProvider,
    ) = ObserveHomeOverviewUseCase(ledgerRepository, accountRepository, txnRepository, timeProvider)
}