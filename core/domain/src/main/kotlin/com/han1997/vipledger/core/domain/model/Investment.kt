package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.InvestmentLinkKind
import com.han1997.vipledger.core.model.money.Money

/**
 * 理财台账（P4）。只记金额，不接行情、不自动估值。
 *
 * [accountId] 是首次投入时自动建立的「投资」类型账户；本金进出走转账，收益/损失走收支。
 */
data class Investment(
    val id: String,
    val ledgerId: String,
    val name: String,
    val accountId: String,
    val remark: String = "",
    val closed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 理财操作流水与台账的关联。 */
data class InvestmentLink(
    val txnId: String,
    val investmentId: String,
    val kind: InvestmentLinkKind,
    val createdAt: Long,
)

/** 理财金额汇总：累计投入、已赎回本金、已实现收益与损失。 */
data class InvestmentSummary(
    val invested: Money,
    val redeemed: Money,
    val gain: Money,
    val loss: Money,
) {
    /** 在投本金 = 累计投入 − 已赎回本金。 */
    val principalOutstanding: Money get() = invested - redeemed

    /** 已实现收益净额 = 收益 − 损失/费用。 */
    val realizedNet: Money get() = gain - loss
}

/** 理财台账 + 关联流水 → 进度。 */
data class InvestmentProgress(
    val investment: Investment,
    val links: List<InvestmentLink>,
    val txns: List<Txn>,
) {
    val summary: InvestmentSummary
        get() = com.han1997.vipledger.core.domain.calculator.InvestmentCalculator
            .summarize(links, txns.associateBy { it.id })

    /** 有在投本金且未手工终止时视为进行中。 */
    val active: Boolean get() = !investment.closed && summary.principalOutstanding.isPositive
}
