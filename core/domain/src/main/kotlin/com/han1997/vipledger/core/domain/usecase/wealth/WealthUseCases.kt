package com.han1997.vipledger.core.domain.usecase.wealth

import com.han1997.vipledger.core.common.result.*
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.BalanceCalculator
import com.han1997.vipledger.core.domain.calculator.SavingsCalculator
import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.domain.repository.*
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.model.*
import com.han1997.vipledger.core.model.money.Money
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate

private class WealthWriteFailure(val error: AppError) : RuntimeException()
private fun <T> AppResult<T>.orRollback(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> throw WealthWriteFailure(error)
}
private suspend fun <T> checkedWrite(block: suspend () -> T): AppResult<T> = try { AppResult.success(block()) }
catch (cancelled: CancellationException) { throw cancelled }
catch (failed: WealthWriteFailure) { AppResult.failure(failed.error) }
catch (bad: IllegalArgumentException) { AppResult.failure(AppError.Validation(bad.message, bad)) }
catch (overflow: ArithmeticException) { AppResult.failure(AppError.Validation("Amount overflow", overflow)) }
catch (error: Exception) { AppResult.failure(AppError.Storage("Unable to save", error)) }

class ObserveAssetsUseCase(private val accounts: AccountRepository, private val wealth: WealthRepository, private val time: TimeProvider) {
    operator fun invoke(ledgerId: String): Flow<AssetsReport> = combine(
        accounts.observeByLedger(ledgerId, true).onEach { wealth.ensureBaselines(it, time.nowMillis()).getOrThrow() },
        wealth.observeBaselines(ledgerId),
    ) { rows, baseline -> AssetsReport(rows, BalanceCalculator.totalAssetsFromCache(rows), BalanceCalculator.totalDebtFromCache(rows), BalanceCalculator.netWorthFromCache(rows), baseline) }
}

class ObserveSavingsUseCase(private val wealth: WealthRepository, private val txns: TxnRepository) {
    operator fun invoke(ledgerId: String): Flow<List<SavingsProgress>> = combine(
        wealth.observeGoals(ledgerId), wealth.observeLinks(ledgerId),
        txns.observe(TxnFilter(ledgerId = ledgerId, types = setOf(TransactionType.TRANSFER), draftStatus = DraftStatus.CONFIRMED)),
    ) { goals, links, entries -> goals.map { SavingsCalculator.progress(it, links, entries) } }
}

class SaveSavingsGoalUseCase(private val wealth: WealthRepository, private val accounts: AccountRepository, private val transactions: TransactionRunner, private val time: TimeProvider, private val ids: IdGenerator) {
    suspend operator fun invoke(id: String?, ledgerId: String, accountId: String, name: String, target: Money, deadline: LocalDate?): AppResult<Unit> = checkedWrite {
        transactions.inTransaction {
            require(name.isNotBlank() && target.cents > 0)
            val account = requireNotNull(accounts.getById(accountId))
            require(account.ledgerId == ledgerId && account.isUsable && !account.isDebt)
            val old = id?.let { requireNotNull(wealth.goal(it)) }
            require(old == null || old.ledgerId == ledgerId)
            require(old == null || old.accountId == accountId || wealth.links(old.id).isEmpty())
            val now = time.nowMillis()
            wealth.saveGoal(SavingsGoal(id ?: ids.newId(), ledgerId, accountId, name.trim(), target, deadline, old?.archived ?: false, old?.createdAt ?: now, now)).orRollback()
        }
    }
    suspend fun archive(id: String, archived: Boolean): AppResult<Unit> = checkedWrite {
        transactions.inTransaction {
            val goal = requireNotNull(wealth.goal(id))
            wealth.saveGoal(goal.copy(archived = archived, updatedAt = time.nowMillis())).orRollback()
        }
    }
}

/** 一条转账和目标关联处于同一外层事务；任意Failure转换异常触发回滚。 */
class MoveSavingsUseCase(private val wealth: WealthRepository, private val txns: TxnRepository, private val accounts: AccountRepository, private val create: CreateTxnUseCase, private val transactions: TransactionRunner, private val time: TimeProvider) {
    suspend operator fun invoke(goalId: String, otherAccountId: String, amount: Money, deposit: Boolean, title: String): AppResult<Txn> = checkedWrite {
        transactions.inTransaction {
            val goal = requireNotNull(wealth.goal(goalId))
            require(!goal.archived && amount.cents > 0 && goal.accountId != otherAccountId)
            val target = requireNotNull(accounts.getById(goal.accountId))
            val other = requireNotNull(accounts.getById(otherAccountId))
            require(target.isUsable && !target.isDebt && other.isUsable && !other.isDebt && other.ledgerId == goal.ledgerId && target.ledgerId == goal.ledgerId)
            if (!deposit) {
                val progress = SavingsCalculator.progress(goal, wealth.links(goal.id), txns.listByLedger(goal.ledgerId))
                require(amount <= progress.saved)
            }
            val txn = create(TxnDraft(goal.ledgerId, TransactionType.TRANSFER, amount, if (deposit) otherAccountId else goal.accountId, toAccountId = if (deposit) goal.accountId else otherAccountId, occurredAt = time.nowMillis(), title = title)).orRollback()
            wealth.link(SavingsLink(txn.id, goal.id, time.nowMillis())).orRollback()
            txn
        }
    }
}

class SavingsTxnGuard(private val wealth: WealthRepository, private val txns: TxnRepository) {
    companion object {
        const val INVALID_LINK = "savings_invalid_link"
        const val DIRECTION = "savings_direction"
        const val NEGATIVE = "savings_negative"
        const val OVERFLOW = "savings_overflow"
    }
    suspend fun validate(updated: Txn): AppError? {
        val link = wealth.linkForTxn(updated.id) ?: return null
        val goal = wealth.goal(link.goalId) ?: return AppError.Validation(INVALID_LINK)
        val original = txns.getById(updated.id) ?: return AppError.Validation(INVALID_LINK)
        if (SavingsCalculator.direction(goal, updated) == 0 || SavingsCalculator.direction(goal, original) != SavingsCalculator.direction(goal, updated) || updated.draftStatus != DraftStatus.CONFIRMED)
            return AppError.Validation(DIRECTION)
        val rows = txns.listByLedger(goal.ledgerId, true).filter { it.id != updated.id } + updated
        return try {
            if (SavingsCalculator.progress(goal, wealth.links(goal.id), rows).saved.cents < 0) AppError.Validation(NEGATIVE) else null
        } catch (error: ArithmeticException) { AppError.Validation(OVERFLOW, error) }
    }
}

class HomeCardUseCases(private val wealth: WealthRepository, private val ledgers: LedgerRepository) {
    fun observe(ledgerId: String) = wealth.observeCards(ledgerId).map(HomeCardLayout::normalize)
    suspend fun save(ledgerId: String, rows: List<HomeCardSetting>): AppResult<Unit> {
        if (ledgers.getById(ledgerId) == null) return AppResult.failure(AppError.Validation("Ledger unavailable"))
        return wealth.saveCards(ledgerId, HomeCardLayout.normalize(rows))
    }
}

class AdjustAccountBalanceUseCase(private val accounts: AccountRepository, private val create: CreateTxnUseCase, private val transactions: TransactionRunner) {
    suspend operator fun invoke(accountId: String, balance: Money, title: String): AppResult<Unit> = checkedWrite {
        transactions.inTransaction {
            val account = requireNotNull(accounts.getById(accountId))
            require(account.isUsable)
            val delta = balance - account.currentBalance
            if (!delta.isZero) create(TxnDraft(account.ledgerId, TransactionType.ADJUST, delta, accountId, title = title)).orRollback()
            Unit
        }
    }
}
