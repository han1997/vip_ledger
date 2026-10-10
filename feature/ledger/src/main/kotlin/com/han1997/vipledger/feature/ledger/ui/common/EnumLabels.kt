package com.han1997.vipledger.feature.ledger.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.han1997.vipledger.core.model.GenerateMode
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentOrigin
import com.han1997.vipledger.core.model.MissedPolicy
import com.han1997.vipledger.core.model.RecurrenceEndType
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.feature.ledger.R

/**
 * 周期 / 分期相关枚举的文案。
 *
 * 状态里只放枚举、不放中文（见 .trellis/spec/android/ui.md），界面层在这里查表。
 */

@Composable
fun recurrenceFrequencyLabel(freq: RecurrenceFrequency): String = stringResource(
    when (freq) {
        RecurrenceFrequency.DAILY -> R.string.recurring_freq_daily
        RecurrenceFrequency.WEEKLY -> R.string.recurring_freq_weekly
        RecurrenceFrequency.MONTHLY -> R.string.recurring_freq_monthly
        RecurrenceFrequency.YEARLY -> R.string.recurring_freq_yearly
        RecurrenceFrequency.EVERY_N_DAYS -> R.string.recurring_freq_every_n_days
        RecurrenceFrequency.EVERY_N_MONTHS -> R.string.recurring_freq_every_n_months
    },
)

@Composable
fun recurrenceEndTypeLabel(endType: RecurrenceEndType): String = stringResource(
    when (endType) {
        RecurrenceEndType.NEVER -> R.string.recurring_end_never
        RecurrenceEndType.UNTIL_DATE -> R.string.recurring_end_until_date
        RecurrenceEndType.COUNT -> R.string.recurring_end_count
    },
)

@Composable
fun generateModeLabel(mode: GenerateMode): String = stringResource(
    when (mode) {
        GenerateMode.DRAFT -> R.string.recurring_mode_draft
        GenerateMode.AUTO -> R.string.recurring_mode_auto
    },
)

@Composable
fun missedPolicyLabel(policy: MissedPolicy): String = stringResource(
    when (policy) {
        MissedPolicy.SKIP -> R.string.recurring_missed_skip
        MissedPolicy.CATCH_UP -> R.string.recurring_missed_catch_up
    },
)

@Composable
fun installmentMethodLabel(method: InstallmentMethod): String = stringResource(
    when (method) {
        InstallmentMethod.EQUAL_INSTALLMENT -> R.string.loan_method_equal_installment
        InstallmentMethod.EQUAL_PRINCIPAL -> R.string.loan_method_equal_principal
        InstallmentMethod.INTEREST_FIRST -> R.string.loan_method_interest_first
        InstallmentMethod.CUSTOM -> R.string.loan_method_custom
    },
)

/** 计划来源：已有负债 / 新放款。 */
@Composable
fun installmentOriginLabel(origin: InstallmentOrigin): String = stringResource(
    when (origin) {
        InstallmentOrigin.EXISTING -> R.string.loan_origin_existing
        InstallmentOrigin.DISBURSEMENT -> R.string.loan_origin_disbursement
    },
)

/** 星期选择项：`byWeekday` 用「0 = 周一 … 6 = 周日」。 */
val weekdayOptions: List<Int> = (0..6).toList()

@Composable
fun weekdayShortLabel(weekday: Int): String = stringResource(
    when (weekday) {
        0 -> R.string.weekday_short_mon
        1 -> R.string.weekday_short_tue
        2 -> R.string.weekday_short_wed
        3 -> R.string.weekday_short_thu
        4 -> R.string.weekday_short_fri
        5 -> R.string.weekday_short_sat
        else -> R.string.weekday_short_sun
    },
)
