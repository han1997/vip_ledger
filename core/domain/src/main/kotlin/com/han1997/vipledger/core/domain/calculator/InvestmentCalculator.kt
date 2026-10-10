package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.InvestmentLink
import com.han1997.vipledger.core.domain.model.InvestmentSummary
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.InvestmentLinkKind
import com.han1997.vipledger.core.model.money.Money

/** 理财金额台账的纯计算：只认未删除流水，本金与收益分账。 */
object InvestmentCalculator {

    fun summarize(
        links: List<InvestmentLink>,
        txnsById: Map<String, Txn>,
    ): InvestmentSummary = InvestmentSummary(
        invested = sum(links, txnsById, InvestmentLinkKind.INVEST),
        redeemed = sum(links, txnsById, InvestmentLinkKind.REDEEM),
        gain = sum(links, txnsById, InvestmentLinkKind.INCOME),
        loss = sum(links, txnsById, InvestmentLinkKind.EXPENSE),
    )

    /** 赎回不得超过在投本金。 */
    fun redeemable(links: List<InvestmentLink>, txnsById: Map<String, Txn>): Money =
        summarize(links, txnsById).principalOutstanding

    private fun sum(
        links: List<InvestmentLink>,
        txnsById: Map<String, Txn>,
        kind: InvestmentLinkKind,
    ): Money = Money.sum(
        links.filter { it.kind == kind }
            .mapNotNull { link -> txnsById[link.txnId]?.takeIf { it.deletedAt == null }?.amount },
    )
}