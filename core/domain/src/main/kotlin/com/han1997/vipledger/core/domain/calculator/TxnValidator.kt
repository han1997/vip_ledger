package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType

/**
 * 记账的业务校验（docs/DATA-MODEL.md 3.5 与第 5 节）。
 * 用例在写库前调用，返回 null 表示通过。
 */
object TxnValidator {

    fun validate(txn: Txn, accounts: List<Account>, categories: List<Category>): AppError? {
        val amountValid = if (txn.type == TransactionType.ADJUST) !txn.amount.isZero else txn.amount.isPositive
        if (!amountValid) {
            return AppError.Validation("金额必须大于 0")
        }

        val account = accounts.firstOrNull { it.id == txn.accountId }
            ?: return AppError.Validation("账户不存在")
        if (account.ledgerId != txn.ledgerId) {
            return AppError.Validation("账户不属于当前账本")
        }
        if (account.deletedAt != null) {
            return AppError.Validation("账户已删除")
        }

        if (txn.type.requiresTargetAccount) {
            val targetId = txn.toAccountId
                ?: return AppError.Validation(if (txn.type == TransactionType.TRANSFER) "转账需要选择转入账户" else "还款需要选择目标账户")
            if (targetId == txn.accountId) {
                return AppError.Validation("转出与转入账户不能相同")
            }
            val target = accounts.firstOrNull { it.id == targetId }
                ?: return AppError.Validation("目标账户不存在")
            if (target.ledgerId != txn.ledgerId) {
                return AppError.Validation("目标账户不属于当前账本")
            }
            if (target.deletedAt != null) {
                return AppError.Validation("目标账户已删除")
            }
            if (txn.type == TransactionType.DEBT_REPAY && !target.type.isDebt) {
                return AppError.Validation("还款的目标账户必须是负债账户")
            }
        } else if (txn.toAccountId != null && txn.toAccountId != txn.accountId) {
            return AppError.Validation("该类型不支持转入账户")
        }

        val categoryId = txn.categoryId ?: return null
        val category = categories.firstOrNull { it.id == categoryId }
            ?: return AppError.Validation("分类不存在")
        if (category.ledgerId != txn.ledgerId) {
            return AppError.Validation("分类不属于当前账本")
        }
        if (category.kind != expectedKind(txn.type)) {
            return AppError.Validation("分类的收支方向与账目类型不一致")
        }
        return null
    }

    fun expectedKind(type: TransactionType): CategoryKind = when (type) {
        TransactionType.EXPENSE, TransactionType.REFUND -> CategoryKind.EXPENSE
        TransactionType.INCOME -> CategoryKind.INCOME
        TransactionType.TRANSFER, TransactionType.DEBT_REPAY, TransactionType.ADJUST -> CategoryKind.TRANSFER
    }
}