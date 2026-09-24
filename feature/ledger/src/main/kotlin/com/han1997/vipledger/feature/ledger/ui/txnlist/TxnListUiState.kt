package com.han1997.vipledger.feature.ledger.ui.txnlist

import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

/** 明细列表按天分组；当天合计只算参与收支统计的账目。 */
data class TxnDayGroup(
    val date: LocalDate,
    val txns: List<Txn>,
    val income: Money,
    val expense: Money,
)

data class TxnListUiState(
    val isLoading: Boolean = true,
    val ledgerName: String = "",
    val groups: List<TxnDayGroup> = emptyList(),
    val accountNames: Map<String, String> = emptyMap(),
    val categoryNames: Map<String, String> = emptyMap(),
    val tagNames: Map<String, String> = emptyMap(),
    val totalCount: Int = 0,
    val notice: TxnListNotice? = null,
)

/** 一次性提示，界面负责映射成 strings.xml 文案（不在状态里存中文）。 */
enum class TxnListNotice { DELETED, DELETE_FAILED }

sealed interface TxnListAction {
    data class Delete(val txnId: String) : TxnListAction

    data object ConsumeNotice : TxnListAction
}