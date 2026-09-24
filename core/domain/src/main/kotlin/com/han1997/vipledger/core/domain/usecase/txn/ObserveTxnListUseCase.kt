package com.han1997.vipledger.core.domain.usecase.txn

import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.repository.TxnRepository
import kotlinx.coroutines.flow.Flow

/** 明细列表 / 搜索结果订阅（搜索条件见 [TxnFilter]）。 */
class ObserveTxnListUseCase(private val txnRepository: TxnRepository) {
    operator fun invoke(filter: TxnFilter): Flow<List<Txn>> = txnRepository.observe(filter)
}