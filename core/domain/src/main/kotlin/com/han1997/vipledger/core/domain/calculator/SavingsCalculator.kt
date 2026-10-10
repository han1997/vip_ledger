package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.money.Money

object SavingsCalculator {
    fun direction(goal: SavingsGoal, txn: Txn): Int = when {
        txn.ledgerId != goal.ledgerId || txn.type != TransactionType.TRANSFER || txn.accountId == txn.toAccountId -> 0
        txn.toAccountId == goal.accountId -> 1
        txn.accountId == goal.accountId -> -1
        else -> 0
    }
    fun progress(goal: SavingsGoal, links: List<SavingsLink>, txns: List<Txn>): SavingsProgress {
        val ids = links.filter { it.goalId == goal.id }.map { it.txnId }.toSet()
        val entries = txns.filter { it.id in ids && !it.isDeleted && it.draftStatus == DraftStatus.CONFIRMED && direction(goal, it) != 0 }
            .sortedWith(compareByDescending<Txn> { it.occurredAt }.thenByDescending { it.createdAt }.thenByDescending { it.id })
        val saved = Money.sum(entries.map { it.amount * direction(goal, it) })
        return SavingsProgress(goal, saved, entries, ids.isNotEmpty())
    }
}
