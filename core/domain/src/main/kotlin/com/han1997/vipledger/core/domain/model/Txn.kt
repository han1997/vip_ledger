package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.money.Money

/**
 * 账目流水（docs/DATA-MODEL.md 3.5）。
 *
 * [amount] 对除 [TransactionType.ADJUST] 外的类型恒为正数，方向由 [type] 决定；
 * 余额调整需要双向，故 ADJUST 允许负值表示调减（见 DATA-MODEL 第 1 节的例外说明）。
 */
data class Txn(
    val id: String,
    val ledgerId: String,
    val type: TransactionType,
    val amount: Money,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val occurredAt: Long,
    val title: String = "",
    val remark: String = "",
    val source: TxnSource = TxnSource.MANUAL,
    val draftStatus: DraftStatus = DraftStatus.CONFIRMED,
    val excludeFromStats: Boolean = false,
    val recurringRuleId: String? = null,
    val installmentPlanId: String? = null,
    val installmentPeriodNo: Int? = null,
    val importHash: String? = null,
    val refundOfTxnId: String? = null,
    val tagIds: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    val isDraft: Boolean get() = draftStatus == DraftStatus.PENDING
}

/** 新建账目时的入参：id 与时间戳由用例生成，调用方不用关心。 */
data class TxnDraft(
    val ledgerId: String,
    val type: TransactionType,
    val amount: Money,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val occurredAt: Long? = null,
    val title: String = "",
    val remark: String = "",
    val tagIds: List<String> = emptyList(),
    val source: TxnSource = TxnSource.MANUAL,
    val draftStatus: DraftStatus = DraftStatus.CONFIRMED,
    val excludeFromStats: Boolean = false,
    val recurringRuleId: String? = null,
    val installmentPlanId: String? = null,
    val installmentPeriodNo: Int? = null,
    val importHash: String? = null,
    val refundOfTxnId: String? = null,
)

/** 搜索/列表的过滤条件（docs/DATA-MODEL.md 3.5）；空集合表示该维度不过滤。 */
data class TxnFilter(
    val ledgerId: String,
    val keyword: String? = null,
    val range: com.han1997.vipledger.core.model.time.DateRange? = null,
    val categoryIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val accountIds: Set<String> = emptySet(),
    val types: Set<TransactionType> = TransactionType.entries.toSet(),
    val minAmount: Money? = null,
    val maxAmount: Money? = null,
    val draftStatus: DraftStatus? = null,
    val includeDeleted: Boolean = false,
    val limit: Int? = null,
)