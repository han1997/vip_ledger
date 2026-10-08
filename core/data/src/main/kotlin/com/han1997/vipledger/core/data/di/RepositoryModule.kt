package com.han1997.vipledger.core.data.di

import com.han1997.vipledger.core.data.builtin.ResourceBuiltinCategorySource
import com.han1997.vipledger.core.data.repository.AccountRepositoryImpl
import com.han1997.vipledger.core.data.repository.AttachmentRepositoryImpl
import com.han1997.vipledger.core.data.repository.CategoryRepositoryImpl
import com.han1997.vipledger.core.data.repository.InstallmentRepositoryImpl
import com.han1997.vipledger.core.data.repository.LedgerRepositoryImpl
import com.han1997.vipledger.core.data.repository.MerchantRuleRepositoryImpl
import com.han1997.vipledger.core.data.repository.MlSampleRepositoryImpl
import com.han1997.vipledger.core.data.repository.RecurringRuleRepositoryImpl
import com.han1997.vipledger.core.data.repository.TagRepositoryImpl
import com.han1997.vipledger.core.data.repository.TxnRepositoryImpl
import com.han1997.vipledger.core.data.support.AccountBalanceSyncerImpl
import com.han1997.vipledger.core.data.support.PreferenceCurrentLedgerStore
import com.han1997.vipledger.core.data.support.PreferenceLastAccountStore
import com.han1997.vipledger.core.data.support.TransactionRunnerImpl
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.AttachmentRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.InstallmentRepository
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.repository.MerchantRuleRepository
import com.han1997.vipledger.core.domain.repository.MlSampleRepository
import com.han1997.vipledger.core.domain.repository.RecurringRuleRepository
import com.han1997.vipledger.core.domain.repository.TagRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.BuiltinCategorySource
import com.han1997.vipledger.core.domain.support.CurrentLedgerStore
import com.han1997.vipledger.core.domain.support.LastAccountStore
import com.han1997.vipledger.core.domain.support.TransactionRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLedgerRepository(impl: LedgerRepositoryImpl): LedgerRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindTxnRepository(impl: TxnRepositoryImpl): TxnRepository

    @Binds
    @Singleton
    abstract fun bindRecurringRuleRepository(impl: RecurringRuleRepositoryImpl): RecurringRuleRepository

    @Binds
    @Singleton
    abstract fun bindInstallmentRepository(impl: InstallmentRepositoryImpl): InstallmentRepository

    @Binds
    @Singleton
    abstract fun bindMerchantRuleRepository(impl: MerchantRuleRepositoryImpl): MerchantRuleRepository

    @Binds
    @Singleton
    abstract fun bindAttachmentRepository(impl: AttachmentRepositoryImpl): AttachmentRepository

    @Binds
    @Singleton
    abstract fun bindMlSampleRepository(impl: MlSampleRepositoryImpl): MlSampleRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRunner(impl: TransactionRunnerImpl): TransactionRunner

    @Binds
    @Singleton
    abstract fun bindAccountBalanceSyncer(impl: AccountBalanceSyncerImpl): AccountBalanceSyncer

    @Binds
    @Singleton
    abstract fun bindBuiltinCategorySource(impl: ResourceBuiltinCategorySource): BuiltinCategorySource

    @Binds
    @Singleton
    abstract fun bindCurrentLedgerStore(impl: PreferenceCurrentLedgerStore): CurrentLedgerStore

    @Binds
    @Singleton
    abstract fun bindLastAccountStore(impl: PreferenceLastAccountStore): LastAccountStore
}
