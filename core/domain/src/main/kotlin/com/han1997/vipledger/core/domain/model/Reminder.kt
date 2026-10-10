package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

/** 提醒类型。界面按类型选文案模板，领域层不拼中文。 */
enum class ReminderKind {
    /** 分期即将到期（还没过期）。 */
    INSTALLMENT_DUE,

    /** 分期已经逾期。 */
    INSTALLMENT_OVERDUE,

    /** 信用卡/贷款的账单日临近。 */
    CARD_BILLING,

    /** 信用卡/贷款的还款日临近且当前有欠款。 */
    CARD_DUE,

    /** 借出/借入临近到期（还没过期）。 */
    LENDING_DUE,

    /** 借出/借入已经逾期。 */
    LENDING_OVERDUE,
}

/**
 * 一条提醒（docs/DATA-MODEL.md 3.2 / 3.7）。
 *
 * [daysUntilDue] 为负数表示逾期天数；[amount] 是应还金额（信用卡提醒用当前欠款）。
 * [name] 是计划名或账户名，属于用户数据，界面直接展示。
 */
data class Reminder(
    val kind: ReminderKind,
    val ledgerId: String,
    val dueDate: LocalDate,
    val daysUntilDue: Int,
    val amount: Money,
    val name: String,
    val planId: String? = null,
    val periodNo: Int? = null,
    val accountId: String? = null,
    val lendingId: String? = null,
) {
    val isOverdue: Boolean get() = daysUntilDue < 0

    /** 稳定标识：同一目标同一天只提醒一次（通知去重用）。 */
    val stableId: String
        get() = listOf(kind.name, planId ?: lendingId ?: accountId ?: "", periodNo?.toString() ?: "", dueDate.toString())
            .joinToString("-")
}
