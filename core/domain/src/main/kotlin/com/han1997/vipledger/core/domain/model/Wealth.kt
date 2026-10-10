package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

enum class AssetGroup {
    FUNDS, CREDIT, PREPAID, RECEIVABLE_PAYABLE, LOANS, INVESTMENTS;
    companion object {
        fun of(type: AccountType): AssetGroup = when (type) {
            AccountType.CASH, AccountType.BANK_DEBIT, AccountType.VIRTUAL -> FUNDS
            AccountType.CREDIT_CARD -> CREDIT
            AccountType.PREPAID -> PREPAID
            AccountType.RECEIVABLE, AccountType.PAYABLE -> RECEIVABLE_PAYABLE
            AccountType.LOAN -> LOANS
            AccountType.INVESTMENT -> INVESTMENTS
        }
    }
}

data class AssetBaseline(val accountId: String, val ledgerId: String, val capturedAt: Long, val balance: Money, val included: Boolean)
data class AssetsReport(val accounts: List<Account>, val assets: Money, val debt: Money, val netWorth: Money, val baselines: List<AssetBaseline>)

data class SavingsGoal(
    val id: String,
    val ledgerId: String,
    val accountId: String,
    val name: String,
    val target: Money,
    val deadline: LocalDate? = null,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
data class SavingsLink(val txnId: String, val goalId: String, val createdAt: Long)
data class SavingsProgress(val goal: SavingsGoal, val saved: Money, val txns: List<Txn>, val hasLinks: Boolean = false) {
    val achieved: Boolean get() = saved >= goal.target
    val fraction: Float get() = (saved.cents.toDouble() / goal.target.cents.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
}

enum class HomeCard { BUDGET, TODAY, SAVINGS, WEEK, PENDING }
data class HomeCardSetting(val card: HomeCard, val position: Int, val visible: Boolean)
object HomeCardLayout {
    fun defaults(): List<HomeCardSetting> = HomeCard.entries.mapIndexed { index, card -> HomeCardSetting(card, index, card != HomeCard.PENDING) }
    fun normalize(rows: List<HomeCardSetting>): List<HomeCardSetting> {
        val known = rows.distinctBy { it.card }.sortedWith(compareBy<HomeCardSetting> { it.position }.thenBy { it.card.ordinal })
        return (known + defaults().filter { default -> known.none { it.card == default.card } }).mapIndexed { index, row -> row.copy(position = index) }
    }
    fun move(rows: List<HomeCardSetting>, card: HomeCard, delta: Int): List<HomeCardSetting> {
        val list = normalize(rows).toMutableList()
        val from = list.indexOfFirst { it.card == card }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        list.add(to, list.removeAt(from))
        return list.mapIndexed { index, row -> row.copy(position = index) }
    }
}
