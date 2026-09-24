package com.han1997.vipledger.core.domain.usecase.account

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.BalanceCalculator
import com.han1997.vipledger.core.domain.model.AccountBalanceDiff
import com.han1997.vipledger.core.domain.model.RecomputeReport
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.TransactionRunner

/**
 * 「重算余额」工具（docs/DATA-MODEL.md 第 6 节）：按流水全量重算并修正缓存，
 * 同时报告差异，用于发现历史脏数据。
 */
class RecomputeAccountBalancesUseCase(
    private val accountRepository: AccountRepository,
    private val txnRepository: TxnRepository,
    private val transactionRunner: TransactionRunner,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(ledgerId: String): AppResult<RecomputeReport> {
        val accounts = accountRepository.listByLedger(ledgerId, includeArchived = true)
        val txns = txnRepository.listByLedger(ledgerId, includeDeleted = false)
        val diffs = accounts.map { account ->
            AccountBalanceDiff(
                accountId = account.id,
                accountName = account.name,
                cached = account.currentBalance,
                computed = BalanceCalculator.balanceOf(account, txns),
            )
        }
        val fixed = diffs.filterNot { it.isConsistent }
        val now = timeProvider.nowMillis()
        if (fixed.isNotEmpty()) {
            transactionRunner.inTransaction {
                fixed.forEach { accountRepository.updateBalance(it.accountId, it.computed, now) }
            }
        }
        return AppResult.success(RecomputeReport(checked = diffs.size, fixed = fixed))
    }
}