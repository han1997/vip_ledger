package com.han1997.vipledger.core.database

import androidx.room.TypeConverter
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.GenerateMode
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.LendingDirection
import com.han1997.vipledger.core.model.LendingLinkKind
import com.han1997.vipledger.core.model.MissedPolicy
import com.han1997.vipledger.core.model.MlSampleSource
import com.han1997.vipledger.core.model.RecurrenceEndType
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.core.model.RuleMatchType
import com.han1997.vipledger.core.model.RuleSource
import com.han1997.vipledger.core.model.RuleTargetField
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource

/**
 * 枚举一律以枚举名（TEXT）存取，禁止存序号（docs/DATA-MODEL.md 第 1 节）：
 * 序号会在枚举顺序调整后静默串味，名字才是稳定契约。
 */
class EnumConverters {

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun fromCategoryKind(value: CategoryKind): String = value.name

    @TypeConverter
    fun toCategoryKind(value: String): CategoryKind = CategoryKind.valueOf(value)

    @TypeConverter
    fun fromDraftStatus(value: DraftStatus): String = value.name

    @TypeConverter
    fun toDraftStatus(value: String): DraftStatus = DraftStatus.valueOf(value)

    @TypeConverter
    fun fromTxnSource(value: TxnSource): String = value.name

    @TypeConverter
    fun toTxnSource(value: String): TxnSource = TxnSource.valueOf(value)

    @TypeConverter
    fun fromRecurrenceFrequency(value: RecurrenceFrequency): String = value.name

    @TypeConverter
    fun toRecurrenceFrequency(value: String): RecurrenceFrequency = RecurrenceFrequency.valueOf(value)

    @TypeConverter
    fun fromRecurrenceEndType(value: RecurrenceEndType): String = value.name

    @TypeConverter
    fun toRecurrenceEndType(value: String): RecurrenceEndType = RecurrenceEndType.valueOf(value)

    @TypeConverter
    fun fromGenerateMode(value: GenerateMode): String = value.name

    @TypeConverter
    fun toGenerateMode(value: String): GenerateMode = GenerateMode.valueOf(value)

    @TypeConverter
    fun fromMissedPolicy(value: MissedPolicy): String = value.name

    @TypeConverter
    fun toMissedPolicy(value: String): MissedPolicy = MissedPolicy.valueOf(value)

    @TypeConverter
    fun fromInstallmentMethod(value: InstallmentMethod): String = value.name

    @TypeConverter
    fun toInstallmentMethod(value: String): InstallmentMethod = InstallmentMethod.valueOf(value)

    @TypeConverter
    fun fromInstallmentPlanStatus(value: InstallmentPlanStatus): String = value.name

    @TypeConverter
    fun toInstallmentPlanStatus(value: String): InstallmentPlanStatus = InstallmentPlanStatus.valueOf(value)

    @TypeConverter
    fun fromInstallmentPeriodStatus(value: InstallmentPeriodStatus): String = value.name

    @TypeConverter
    fun toInstallmentPeriodStatus(value: String): InstallmentPeriodStatus = InstallmentPeriodStatus.valueOf(value)

    @TypeConverter
    fun fromLendingDirection(value: LendingDirection): String = value.name

    @TypeConverter
    fun toLendingDirection(value: String): LendingDirection = LendingDirection.valueOf(value)

    @TypeConverter
    fun fromLendingLinkKind(value: LendingLinkKind): String = value.name

    @TypeConverter
    fun toLendingLinkKind(value: String): LendingLinkKind = LendingLinkKind.valueOf(value)

    @TypeConverter
    fun fromRuleMatchType(value: RuleMatchType): String = value.name

    @TypeConverter
    fun toRuleMatchType(value: String): RuleMatchType = RuleMatchType.valueOf(value)

    @TypeConverter
    fun fromRuleTargetField(value: RuleTargetField): String = value.name

    @TypeConverter
    fun toRuleTargetField(value: String): RuleTargetField = RuleTargetField.valueOf(value)

    @TypeConverter
    fun fromRuleSource(value: RuleSource): String = value.name

    @TypeConverter
    fun toRuleSource(value: String): RuleSource = RuleSource.valueOf(value)

    @TypeConverter
    fun fromMlSampleSource(value: MlSampleSource): String = value.name

    @TypeConverter
    fun toMlSampleSource(value: String): MlSampleSource = MlSampleSource.valueOf(value)
}
