package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money

/** Run inside the mutation transaction, including manual edits and restores. */
object RefundValidator {
    fun validate(txn: Txn, ledgerTxns: List<Txn>): AppError? {
        val refunds = ledgerTxns.filter { it.id != txn.id && it.type == TransactionType.REFUND &&
            it.draftStatus == DraftStatus.CONFIRMED && !it.isDeleted }
        if (refunds.any { it.refundOfTxnId == txn.id }) {
            if (txn.type != TransactionType.EXPENSE || txn.isDeleted || txn.draftStatus != DraftStatus.CONFIRMED ||
                Money.sum(refunds.filter { it.refundOfTxnId == txn.id }.map { it.amount }) > txn.amount) {
                return AppError.Validation("请先处理关联退款，再修改或删除原消费")
            }
        }
        val originalId = txn.refundOfTxnId ?: return null
        if (txn.type != TransactionType.REFUND) return AppError.Validation("只有退款可以关联原消费")
        if (txn.isDeleted || txn.draftStatus != DraftStatus.CONFIRMED) return null
        val original = ledgerTxns.find { it.id == originalId && it.ledgerId == txn.ledgerId &&
            it.type == TransactionType.EXPENSE && !it.isDeleted && it.draftStatus == DraftStatus.CONFIRMED }
            ?: return AppError.Validation("原消费不存在或不可用")
        val total = Money.sum(refunds.filter { it.refundOfTxnId == originalId }.map { it.amount }) + txn.amount
        return if (total > original.amount) AppError.Validation("累计退款超过原消费金额") else null
    }
}
