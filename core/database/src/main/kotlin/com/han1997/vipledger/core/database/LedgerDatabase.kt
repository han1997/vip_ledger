package com.han1997.vipledger.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.AttachmentEntity
import com.han1997.vipledger.core.database.entity.CategoryEntity
import com.han1997.vipledger.core.database.entity.InstallmentPeriodEntity
import com.han1997.vipledger.core.database.entity.InstallmentPlanEntity
import com.han1997.vipledger.core.database.entity.LedgerEntity
import com.han1997.vipledger.core.database.entity.MerchantRuleEntity
import com.han1997.vipledger.core.database.entity.MlTrainingSampleEntity
import com.han1997.vipledger.core.database.entity.RecurringRuleEntity
import com.han1997.vipledger.core.database.entity.TagEntity
import com.han1997.vipledger.core.database.entity.TxnEntity
import com.han1997.vipledger.core.database.entity.TxnTagEntity

/**
 * 本地主库（docs/DATA-MODEL.md）。
 *
 * 版本从 1 起，`exportSchema = true`：schema JSON 提交到 `core/database/schemas/`，
 * 迁移测试用它校验（见 `.trellis/spec/android/database.md`）。
 */
@Database(
    entities = [
        LedgerEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        TxnTagEntity::class,
        TxnEntity::class,
        RecurringRuleEntity::class,
        InstallmentPlanEntity::class,
        InstallmentPeriodEntity::class,
        MerchantRuleEntity::class,
        AttachmentEntity::class,
        MlTrainingSampleEntity::class,
    ],
    version = LedgerDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class LedgerDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao

    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun tagDao(): TagDao

    abstract fun txnDao(): TxnDao

    abstract fun txnTagDao(): TxnTagDao

    abstract fun recurringRuleDao(): RecurringRuleDao

    abstract fun installmentPlanDao(): InstallmentPlanDao

    abstract fun installmentPeriodDao(): InstallmentPeriodDao

    abstract fun merchantRuleDao(): MerchantRuleDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun mlTrainingSampleDao(): MlTrainingSampleDao

    companion object {
        const val SCHEMA_VERSION: Int = 1
        const val NAME: String = "vipledger.db"
    }
}