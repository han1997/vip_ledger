@file:OptIn(ExperimentalTime::class)

package com.han1997.vipledger.core.designsystem.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.han1997.vipledger.core.designsystem.R
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import com.han1997.vipledger.core.model.time.toDateColumnEpochMillis
import com.han1997.vipledger.core.model.time.toDateColumnLocalDate
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlin.time.ExperimentalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * 界面文案拼装。
 *
 * 所有带中文的模板都放在 `strings.xml`，这里只做「取资源 + 填参数」，
 * 避免在 Compose 里硬编码中文（见 .trellis/spec/android/ui.md）。
 * 因为 `feature:ledger` 与 `feature:stats` 都要用，按架构规范放在 designsystem。
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

/** 相对日标签（原型 dateLabel）：今天 / 昨天，其余为「周X」。[today] 为空时退化为周X。 */
@Composable
fun relativeDayLabel(date: LocalDate, today: LocalDate?): String {
    val todayDate = today ?: return weekdayLabel(date)
    return when (date) {
        todayDate -> stringResource(R.string.day_label_today)
        todayDate.minus(1, DateTimeUnit.DAY) -> stringResource(R.string.day_label_yesterday)
        else -> weekdayLabel(date)
    }
}

/** 闭区间范围文案，如「2026-09-01 ~ 2026-09-24」。 */
@Composable
fun dateRangeLabel(range: DateRange): String =
    stringResource(R.string.date_range_span, fullDateLabel(range.start), fullDateLabel(range.endInclusive))

/** 时间段预设名；`null` 表示不限时间（搜索页的空条件）。 */
@Composable
fun rangePresetLabel(preset: DateRangePreset?): String = stringResource(
    when (preset) {
        null -> R.string.range_preset_all
        DateRangePreset.THIS_WEEK -> R.string.range_preset_this_week
        DateRangePreset.LAST_WEEK -> R.string.range_preset_last_week
        DateRangePreset.THIS_MONTH -> R.string.range_preset_this_month
        DateRangePreset.LAST_MONTH -> R.string.range_preset_last_month
        DateRangePreset.LAST_7_DAYS -> R.string.range_preset_last_7_days
        DateRangePreset.LAST_30_DAYS -> R.string.range_preset_last_30_days
        DateRangePreset.THIS_QUARTER -> R.string.range_preset_this_quarter
        DateRangePreset.LAST_QUARTER -> R.string.range_preset_last_quarter
        DateRangePreset.THIS_YEAR -> R.string.range_preset_this_year
        DateRangePreset.LAST_YEAR -> R.string.range_preset_last_year
    },
)

/** 时分，如 12:05。 */
@Composable
fun timeLabel(millis: Long, zone: TimeZone): String {
    val dateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    return stringResource(R.string.date_time_hm, dateTime.hour, dateTime.minute)
}

/** 时分秒，如 12:05:09。流水列表用它，同分钟内的多笔顺序才看得出来。 */
@Composable
fun timeLabelWithSeconds(millis: Long, zone: TimeZone): String {
    val dateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    return stringResource(R.string.date_time_hms, dateTime.hour, dateTime.minute, dateTime.second)
}

/** 设备时区下的某个时刻 → Material3 日期选择器要的「UTC 零点」毫秒。 */
fun Long.toPickerUtcMillis(zone: TimeZone): Long = toLocalDate(zone).toDateColumnEpochMillis()

/** 日期选择器返回的「UTC 零点」毫秒 → 设备时区当天的真实时刻。 */
fun Long.fromPickerUtcMillis(zone: TimeZone): Long = toDateColumnLocalDate().startOfDayEpochMillis(zone)

@Composable
fun transactionTypeLabel(type: TransactionType): String = stringResource(
    when (type) {
        TransactionType.REFUND -> R.string.txn_type_refund
        TransactionType.EXPENSE -> R.string.txn_type_expense
        TransactionType.INCOME -> R.string.txn_type_income
        TransactionType.TRANSFER -> R.string.txn_type_transfer
        TransactionType.DEBT_REPAY -> R.string.txn_type_repay
        TransactionType.ADJUST -> R.string.txn_type_adjust
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
        AccountType.PREPAID -> R.string.account_type_prepaid
    },
)

@Composable
fun categoryKindLabel(kind: CategoryKind): String = stringResource(
    when (kind) {
        CategoryKind.EXPENSE -> R.string.category_kind_expense
        CategoryKind.INCOME -> R.string.category_kind_income
        CategoryKind.TRANSFER -> R.string.category_kind_transfer
    },
)

/**
 * 卡号按 4 位分组展示(原型 v1.4):`6225880123456021` → `6225 8801 2345 6021`。
 * 非数字字符先剔除;不足 4 位不分组。
 */
fun formatCardNo(cardNo: String?): String = cardNo
    ?.filter { it.isDigit() }
    ?.chunked(4)
    ?.joinToString(" ")
    .orEmpty()

/** 卡号尾号(最后 4 位);不足 4 位返回全部。 */
fun cardNoLast4(cardNo: String?): String = cardNo
    ?.filter { it.isDigit() }
    ?.takeLast(4)
    .orEmpty()
