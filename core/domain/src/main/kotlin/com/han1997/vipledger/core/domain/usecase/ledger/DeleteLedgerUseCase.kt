package com.han1997.vipledger.core.domain.usecase.ledger

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.repository.TagRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.TransactionRunner

/**
 * 删除账本（物理删）。仅当账本内一笔账目都没有时允许，此时账本下的账户 / 分类 / 标签
 * 一并软删（表之间没有级联外键，靠这里的同一个事务保证不留可见残留）。
 */
class DeleteLedgerUseCase(
    private val ledgerRepository: LedgerRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val txnRepository: TxnRepository,
    private val transactionRunner: TransactionRunner,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(ledgerId: String): AppResult<Unit> {
        val ledger = ledgerRepository.getById(ledgerId)
            ?: return AppResult.failure(AppError.NotFound("账本不存在"))
        if (ledger.isDefault) {
            return AppResult.failure(AppError.Validation("默认账本不能删除，请先把其他账本设为默认"))
        }
        if (ledgerRepository.count() <= 1) {
            return AppResult.failure(AppError.Validation("至少要保留一个账本"))
        }
        if (txnRepository.countByLedger(ledgerId) > 0) {
            return AppResult.failure(AppError.Validation("账本内还有账目，请先清空或改为归档"))
        }

        val now = timeProvider.nowMillis()
        return transactionRunner.inTransaction {
            accountRepository.listByLedger(ledgerId, includeArchived = true)
                .forEach { accountRepository.softDelete(it.id, now) }
            categoryRepository.listByLedger(ledgerId, includeArchived = true)
                .forEach { categoryRepository.softDelete(it.id, now) }
            tagRepository.listByLedger(ledgerId)
                .forEach { tagRepository.softDelete(it.id, now) }
            ledgerRepository.delete(ledgerId)
        }
    }
}