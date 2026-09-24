package com.han1997.vipledger.core.domain.usecase.txn

import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.repository.TxnRepository

/** 按 id 取单笔账目（编辑页预填、后续的还原入口都要用）。 */
class GetTxnUseCase(private val txnRepository: TxnRepository) {
    suspend operator fun invoke(txnId: String): Txn? = txnRepository.getById(txnId)
}