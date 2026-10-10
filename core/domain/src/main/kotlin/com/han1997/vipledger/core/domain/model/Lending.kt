package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.LendingDirection
import com.han1997.vipledger.core.model.LendingLinkKind
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

/**
 * 借贷记录（docs/DATA-MODEL.md 3.8）。
 *
 * [principal] 是原始本金，[accountId] 是自动建立的应收/应付账户；剩余本金由关联的本金
 * 归流水推导（见 [LendingProgress]），不在此重复存储。[closed] 只表示手工终止。
 */
data class LendingRecord(
    val id: String,
    val ledgerId: String,
    val direction: LendingDirection,
    val counterparty: String,
    val principal: Money,
    val accountId: String,
    val dueDate: LocalDate? = null,
    val remark: String = "",
    val closed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 借贷关联流水：一条流水只属于一个借贷记录。 */
data class LendingLink(
    val txnId: String,
    val lendingId: String,
    val kind: LendingLinkKind,
    val createdAt: Long,
)

/**
 * 借贷进度：原始/剩余本金、累计利息与关联流水都从 [links] + [txns] 推导。
 *
 * [settled] 由剩余本金归零推导；[closed] 是用户手工终止，两者都会让记录不再活动。
 */
data class LendingProgress(
    val record: LendingRecord,
    val links: List<LendingLink>,
    val txns: List<Txn>,
) {
    val settledPrincipal: Money get() = amountOf(LendingLinkKind.SETTLE)

    val outstanding: Money
        get() = (record.principal - settledPrincipal).let { if (it.isNegative) Money.ZERO else it }

    val interestTotal: Money get() = amountOf(LendingLinkKind.INTEREST)

    val settled: Boolean get() = outstanding.isZero

    val active: Boolean get() = !record.closed && !settled

    private fun amountOf(kind: LendingLinkKind): Money {
        val ids = links.filter { it.kind == kind }.map { it.txnId }.toSet()
        return Money.sum(txns.filter { it.id in ids }.map { it.amount })
    }
}

/** 借贷相关的纯计算。 */
object LendingCalculator {

    /** 剩余本金 = 原始本金 − 已结清本金，下限为 0。 */
    fun outstanding(record: LendingRecord, links: List<LendingLink>, txns: List<Txn>): Money =
        LendingProgress(record, links, txns).outstanding

    /** 是否可结清：剩余本金为正且金额不超过剩余本金。 */
    fun canSettle(record: LendingRecord, links: List<LendingLink>, txns: List<Txn>, amount: Money): Boolean =
        amount.isPositive && amount <= outstanding(record, links, txns)
}
