@file:OptIn(ExperimentalTime::class)

package com.han1997.vipledger.feature.ledger.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import com.han1997.vipledger.core.model.time.toDateColumnEpochMillis
import com.han1997.vipledger.core.model.time.toDateColumnLocalDate
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.ledger.R
import kotlin.time.ExperimentalTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 界面文案拼装。
 *
 * 所有带中文的模板都放在 `strings.xml`，这里只做「取资源 + 填参数」，
 * 避免在 Compose 里硬编码中文（见 .trellis/spec/android/ui.md）。
 */

@Composable
fun monthDayLabel(date: LocalDate): String =
    stringResource(R.string.date_month_day, date.monthNumber, date.dayOfMonth)

@Composable
fun fullDateLabel(date: LocalDate): String =
    stringResource(R.string.date_year_month_day, date.year, date.monthNumber, date.dayOfMonth)

@Composable
fun weekdayLabel(date: LocalDate): String = stringResource(
    when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> R.string.weekday_mon
        DayOfWeek.TUESDAY -> R.string.weekday_tue
        DayOfWeek.WEDNESDAY -> R.string.weekday_wed
        DayOfWeek.THURSDAY -> R.string.weekday_thu
        DayOfWeek.FRIDAY -> R.string.weekday_fri
        DayOfWeek.SATURDAY -> R.string.weekday_sat
        DayOfWeek.SUNDAY -> R.string.weekday_sun
        else -> R.string.weekday_mon
    },
)

/** 明细列表的按天分组标题，如「9月24日 周三」。 */
@Composable
fun dayHeaderLabel(date: LocalDate): String = monthDayLabel(date) + " " + weekdayLabel(date)

/** 时分，如 12:05。 */
@Composable
fun timeLabel(millis: Long, zone: TimeZone): String {
    val dateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    return stringResource(R.string.date_time_hm, dateTime.hour, dateTime.minute)
}

/** 设备时区下的某个时刻 → Material3 日期选择器要的「UTC 零点」毫秒。 */
fun Long.toPickerUtcMillis(zone: TimeZone): Long = toLocalDate(zone).toDateColumnEpochMillis()

/** 日期选择器返回的「UTC 零点」毫秒 → 设备时区当天的真实时刻。 */
fun Long.fromPickerUtcMillis(zone: TimeZone): Long = toDateColumnLocalDate().startOfDayEpochMillis(zone)

@Composable
fun transactionTypeLabel(type: TransactionType): String = stringResource(
    when (type) {
        TransactionType.EXPENSE -> R.string.txn_edit_type_expense
        TransactionType.INCOME -> R.string.txn_edit_type_income
        TransactionType.TRANSFER -> R.string.txn_edit_type_transfer
        TransactionType.DEBT_REPAY -> R.string.txn_edit_type_repay
        TransactionType.ADJUST -> R.string.txn_edit_type_adjust
    },
)

@Composable
fun accountTypeLabel(type: AccountType): String = stringResource(
    when (type) {
        AccountType.CASH -> R.string.account_type_cash
        AccountType.BANK_DEBIT -> R.string.account_type_bank_debit
        AccountType.CREDIT_CARD -> R.string.account_type_credit_card
        AccountType.VIRTUAL -> R.string.account_type_virtual
        AccountType.RECEIVABLE -> R.string.account_type_receivable
        AccountType.PAYABLE -> R.string.account_type_payable
        AccountType.LOAN -> R.string.account_type_loan
        AccountType.INVESTMENT -> R.string.account_type_investment
    },
)

@Composable
fun categoryKindLabel(kind: CategoryKind): String = stringResource(
    when (kind) {
        CategoryKind.EXPENSE -> R.string.category_manage_kind_expense
        CategoryKind.INCOME -> R.string.category_manage_kind_income
        CategoryKind.TRANSFER -> R.string.category_manage_kind_transfer
    },
)