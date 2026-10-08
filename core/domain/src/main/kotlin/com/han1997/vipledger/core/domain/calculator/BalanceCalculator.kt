package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money

/**
 * 账户余额与净资产（docs/DATA-MODEL.md 第 5 节）。
 *
 * 所有账户都用「资产式」符号：余额 = 期初 + 净额，负债账户欠钱时为负，
 * 因此净资产就是各账户余额之和，展示层再按负债类型显示成欠款。
 * 只有已确认（CONFIRMED）的流水参与，草稿确认后需要重算余额。
 */
object BalanceCalculator {

    /** 单笔流水对某个账户的影响；与该账户无关时返回 null。 */
    fun deltaOf(txn: Txn, accountId: String): Money? {
        if (txn.isDeleted || txn.draftStatus != DraftStatus.CONFIRMED) return null
        return when (txn.type) {
            TransactionType.EXPENSE -> if (txn.accountId == accountId) -txn.amount else null
            TransactionType.INCOME, TransactionType.REFUND -> if (txn.accountId == accountId) txn.amount else null
            TransactionType.TRANSFER, TransactionType.DEBT_REPAY -> when {
                txn.accountId == accountId -> -txn.amount
                txn.toAccountId == accountId -> txn.amount
                else -> null
            }
            // ADJUST 无目标账户时是「调增/调减」；带目标账户时按转账语义处理。
            TransactionType.ADJUST -> when (txn.toAccountId) {
                null -> if (txn.accountId == accountId) txn.amount else null
                accountId -> txn.amount
                else -> if (txn.accountId == accountId) -txn.amount else null
            }
        }
    }

    /** 账户的流水净额。 */
    fun deltaFor(accountId: String, txns: List<Txn>): Money =
        Money.sum(txns.mapNotNull { deltaOf(it, accountId) })

    /** 重算出的余额（用于刷新缓存或校验）。 */
    fun balanceOf(account: Account, txns: List<Txn>): Money =
        account.initialBalance + deltaFor(account.id, txns)

    /** 净资产 = 计入净资产的可用账户余额之和。 */
    fun netWorth(accounts: List<Account>, txns: List<Txn>): Money = Money.sum(
        accounts.filter { it.includeInNetWorth && it.isUsable }.map { balanceOf(it, txns) },
    )

    /** 负债合计（正数）：信用卡、应付、贷款。 */
    fun totalDebt(accounts: List<Account>, txns: List<Txn>): Money = Money.sum(
        accounts.filter { it.isDebt && it.isUsable }
            .map { balanceOf(it, txns) }
            .filter { it.isNegative }
            .map { -it },
    )

    /**
     * 首页用的净资产：直接累加账户余额缓存（[Account.currentBalance]），不重放全部流水。
     * 缓存由记账用例在同一事务内维护，见 docs/DATA-MODEL.md 第 6 节。
     */
    fun netWorthFromCache(accounts: List<Account>): Money = Money.sum(
        accounts.filter { it.includeInNetWorth && it.isUsable }.map { it.currentBalance },
    )

    /** 负债合计（正数），同样基于余额缓存。 */
    fun totalDebtFromCache(accounts: List<Account>): Money = Money.sum(
        accounts.filter { it.isDebt && it.isUsable }
            .map { it.currentBalance }
            .filter { it.isNegative }
            .map { -it },
    )}