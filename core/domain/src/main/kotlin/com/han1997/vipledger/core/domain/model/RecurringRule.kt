package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.GenerateMode
import com.han1997.vipledger.core.model.MissedPolicy
import com.han1997.vipledger.core.model.RecurrenceEndType
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money

/** 周期记账规则（docs/DATA-MODEL.md 3.6）。日期推算引擎在 M4，本层只负责存取。 */
data class RecurringRule(
    val id: String,
    val ledgerId: String,
    val name: String,
    val txnType: TransactionType,
    val amount: Money,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val tagIds: List<String> = emptyList(),
    val title: String = "",
    val remark: String = "",
    val freq: RecurrenceFrequency,
    val interval: Int = 1,
    val byWeekday: Int? = null,
    val byMonthDay: Int? = null,
    val byMonth: Int? = null,
    val startAt: Long,
    val endType: RecurrenceEndType = RecurrenceEndType.NEVER,
    val endDate: Long? = null,
    val maxOccurrences: Int? = null,
    val generatedCount: Int = 0,
    val nextOccurrenceAt: Long? = null,
    val lastGeneratedAt: Long? = null,
    val generateMode: GenerateMode = GenerateMode.DRAFT,
    val missedPolicy: MissedPolicy = MissedPolicy.SKIP,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)