package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigInteger

/** 按月生效的设置；空 scope 是总预算，null 额度显式停止继承。 */
data class BudgetLimit(
    val id: String,
    val ledgerId: String,
    val month: LocalDate,
    val scopeKey: String,
    val amount: Money?,
    val updatedAt: Long,
)

data class BudgetProgress(val limit: BudgetLimit, val spent: Money) {
    val remaining: Money = requireNotNull(limit.amount) - spent
    val fraction: Float get() {
        val cents = limit.amount?.cents ?: return 0f
        return if (cents <= 0) { if (spent.cents > 0) 1f else 0f }
        else (spent.cents.toDouble() / cents).toFloat().coerceIn(0f, 1f)
    }
    fun reached(threshold: Int): Boolean = spent.cents > 0 &&
        BigInteger.valueOf(spent.cents).multiply(BigInteger.valueOf(100)) >=
        BigInteger.valueOf(requireNotNull(limit.amount).cents).multiply(BigInteger.valueOf(threshold.toLong()))
}

data class BudgetReport(
    val ledgerId: String,
    val month: LocalDate,
    val progress: List<BudgetProgress>,
) {
    val total: BudgetProgress? get() = progress.firstOrNull { it.limit.scopeKey.isEmpty() }
}
