package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.Investment
import com.han1997.vipledger.core.domain.model.InvestmentLink
import kotlinx.coroutines.flow.Flow

/** 理财台账读写（P4）。 */
interface InvestmentRepository {
    fun observeInvestments(ledgerId: String): Flow<List<Investment>>

    fun observeLinks(ledgerId: String): Flow<List<InvestmentLink>>

    suspend fun investment(id: String): Investment?

    suspend fun links(investmentId: String): List<InvestmentLink>

    suspend fun linksForLedger(ledgerId: String): List<InvestmentLink>

    suspend fun linkForTxn(txnId: String): InvestmentLink?

    suspend fun save(investment: Investment): AppResult<Unit>

    suspend fun link(link: InvestmentLink): AppResult<Unit>
}
