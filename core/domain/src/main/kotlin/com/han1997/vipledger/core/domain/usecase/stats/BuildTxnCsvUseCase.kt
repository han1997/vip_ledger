package com.han1997.vipledger.core.domain.usecase.stats

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.CsvWriter
import com.han1997.vipledger.core.domain.calculator.StatsCalculator
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.time.toLocalDateTime

/**
 * 把账目列表导出成 CSV 文本。
 *
 * 这里的中文列名与取值是**文件格式的一部分**（用户拿 Excel 打开的就是它），
 * 所以直接写在领域层，不走 `strings.xml`；界面上的可见文案仍然走资源文件。
 * 账号名等查表由调用方传入（界面层已有 id → 名字的映射）。
 */
class BuildTxnCsvUseCase(private val timeProvider: TimeProvider) {

    operator fun invoke(
        txns: List<Txn>,
        categoryNames: Map<String, String> = emptyMap(),
        accountNames: Map<String, String> = emptyMap(),
        tagNames: Map<String, String> = emptyMap(),
    ): String {
        val zone = timeProvider.zone
        val rows = mutableListOf(HEADER)
        txns.forEach { txn ->
            val dateTime = txn.occurredAt.toLocalDateTime(zone)
            rows += listOf(
                dateTime.date.toString(),
                twoDigit(dateTime.hour) + ":" + twoDigit(dateTime.minute),
                typeLabel(txn.type),
                txn.amount.toPlainString(),
                accountNames[txn.accountId] ?: txn.accountId,
                txn.toAccountId?.let { accountNames[it] ?: it }.orEmpty(),
                txn.categoryId?.let { categoryNames[it] ?: it }.orEmpty(),
                txn.tagIds.mapNotNull { tagNames[it] }.joinToString(separator = "|"),
                txn.title,
                txn.remark,
                yesNo(StatsCalculator.isCounted(txn)),
                sourceLabel(txn.source),
                draftLabel(txn.draftStatus),
                yesNo(txn.isDeleted),
            )
        }
        return CsvWriter.write(rows, includeBom = true)
    }


    private fun twoDigit(value: Int): String = value.toString().padStart(2, '0')

    private fun yesNo(value: Boolean): String = if (value) "是" else "否"

    private fun typeLabel(type: TransactionType): String = when (type) {
        TransactionType.REFUND -> "退款"
        TransactionType.EXPENSE -> "支出"
        TransactionType.INCOME -> "收入"
        TransactionType.TRANSFER -> "转账"
        TransactionType.DEBT_REPAY -> "还款"
        TransactionType.ADJUST -> "调整"
    }

    private fun sourceLabel(source: TxnSource): String = when (source) {
        TxnSource.MANUAL -> "手动"
        TxnSource.RECURRING -> "周期"
        TxnSource.AI -> "AI"
        TxnSource.IMPORT -> "导入"
        TxnSource.RULE -> "规则"
        TxnSource.AUTO -> "自动记账"
    }

    private fun draftLabel(status: DraftStatus): String = when (status) {
        DraftStatus.CONFIRMED -> "已确认"
        DraftStatus.PENDING -> "待确认"
        DraftStatus.DISCARDED -> "已丢弃"
    }


    companion object {
        val HEADER: List<String> = listOf(
            "日期",
            "时间",
            "类型",
            "金额",
            "账户",
            "目标账户",
            "分类",
            "标签",
            "标题",
            "备注",
            "计入统计",
            "来源",
            "状态",
            "已删除",
        )
    }
}