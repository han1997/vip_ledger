package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.money.Money

/**
 * 报销项目（docs/DATA-MODEL.md 3.8）。
 *
 * [closed] 表示终止：终止后不再允许到账；已到账与待报销金额按实际发生保留。
 * 待报销只在报销入口展示，不会作为应收资产进入净资产。
 */
data class Reimbursement(
    val id: String,
    val ledgerId: String,
    val title: String,
    val counterparty: String = "",
    val remark: String = "",
    val closed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 报销项目关联的原支出。 */
data class ReimbursementItem(val reimbursementId: String, val txnId: String)

/** 报销到账关联的退款流水。 */
data class ReimbursementReceipt(val reimbursementId: String, val txnId: String)

/**
 * 报销进度：由关联的原支出与到账退款推导，不额外记账。
 *
 * 待报销 = 全部原支出金额 − 已到账合计；[settled] 表示已全部到账。
 */
data class ReimbursementProgress(
    val project: Reimbursement,
    val items: List<Txn>,
    val receipts: List<Txn>,
) {
    val claimable: Money get() = Money.sum(items.map { it.amount })

    val received: Money get() = Money.sum(receipts.map { it.amount })

    val pending: Money get() = claimable - received

    val settled: Boolean get() = !pending.isPositive
}

/** 报销相关的纯计算。 */
object ReimbursementCalculator {

    /** 单个原支出还能到账的额度：支出金额 − 该支出已到账合计。 */
    fun remainingFor(item: Txn, receipts: List<Txn>): Money =
        item.amount - Money.sum(receipts.filter { it.refundOfTxnId == item.id }.map { it.amount })

    /** 未终止项目的待报销合计，用于资产页「报销」入口摘要。 */
    fun pendingTotal(progress: List<ReimbursementProgress>): Money =
        Money.sum(progress.filter { !it.project.closed }.map { it.pending })
}
