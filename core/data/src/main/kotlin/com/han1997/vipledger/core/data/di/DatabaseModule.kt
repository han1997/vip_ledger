package com.han1997.vipledger.core.data.di

import android.content.Context
import androidx.room.Room
import com.han1997.vipledger.core.database.ALL_MIGRATIONS
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.dao.AccountDao
import com.han1997.vipledger.core.database.dao.AttachmentDao
import com.han1997.vipledger.core.database.dao.CategoryDao
import com.han1997.vipledger.core.database.dao.InstallmentPeriodDao
import com.han1997.vipledger.core.database.dao.InstallmentPlanDao
import com.han1997.vipledger.core.database.dao.LedgerDao
import com.han1997.vipledger.core.database.dao.MerchantRuleDao
import com.han1997.vipledger.core.database.dao.MlTrainingSampleDao
import com.han1997.vipledger.core.database.dao.RecurringRuleDao
import com.han1997.vipledger.core.database.dao.TagDao
import com.han1997.vipledger.core.database.dao.TxnDao
import com.han1997.vipledger.core.database.dao.TxnTagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** 本地主库：只走显式迁移，禁止 fallbackToDestructiveMigration。 */
    @Provides
    @Singleton
    fun provideLedgerDatabase(@ApplicationContext context: Context): LedgerDatabase =
        Room.databaseBuilder(context, LedgerDatabase::class.java, LedgerDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideLedgerDao(database: LedgerDatabase): LedgerDao = database.ledgerDao()

    @Provides
    fun provideAccountDao(database: LedgerDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideCategoryDao(database: LedgerDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTagDao(database: LedgerDatabase): TagDao = database.tagDao()

    @Provides
    fun provideTxnDao(database: LedgerDatabase): TxnDao = database.txnDao()

    @Provides
    fun provideTxnTagDao(database: LedgerDatabase): TxnTagDao = database.txnTagDao()

    @Provides
    fun provideRecurringRuleDao(database: LedgerDatabase): RecurringRuleDao = database.recurringRuleDao()

    @Provides
    fun provideInstallmentPlanDao(database: LedgerDatabase): InstallmentPlanDao = database.installmentPlanDao()

    @Provides
    fun provideInstallmentPeriodDao(database: LedgerDatabase): InstallmentPeriodDao = database.installmentPeriodDao()

    @Provides
    fun provideMerchantRuleDao(database: LedgerDatabase): MerchantRuleDao = database.merchantRuleDao()

    @Provides
    fun provideAttachmentDao(database: LedgerDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun provideMlTrainingSampleDao(database: LedgerDatabase): MlTrainingSampleDao = database.mlTrainingSampleDao()
}