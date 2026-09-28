package com.han1997.vipledger.core.domain.usecase.backup

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.CsvWriter
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.time.toLocalDateTime

/**
 * 把某个账本的流水导出成 **M5 备份/导入用的对外 CSV**。
 *
 * 列顺序与 `.trellis/spec/android/import-export.md` 第一节一致：
 * `日期,类型,金额,分类,子分类,标签,账户,目标账户,标题,备注,来源`。
 * 这与 M3 明细导出的 [com.han1997.vipledger.core.domain.usecase.stats.BuildTxnCsvUseCase]
 * 是**两套不同格式**，绝不共用表头或行结构：
 * 后者带时间/状态/已删除等内部字段，前者是对外可再导入的精简格式。
 *
 * 日期只落到「天」，金额固定两位小数字符串；中文列名属于文件格式的一部分，
 * 因此直接写在领域层而不走 `strings.xml`（同 [com.han1997.vipledger.core.domain.usecase.stats.BuildTxnCsvUseCase]）。
 */
class BuildBackupCsvUseCase(private val timeProvider: TimeProvider) {

    operator fun invoke(
        txns: List<Txn>,
        categories: List<Category>,
        accountNames: Map<String, String> = emptyMap(),
        tagNames: Map<String, String> = emptyMap(),
    ): String {
        val categoryById = categories.associateBy { it.id }
        val rows = mutableListOf(HEADER)
        txns.forEach { txn ->
            val category = txn.categoryId?.let(categoryById::get)
            val parent = category?.parentId?.let(categoryById::get)
            val topLevelName = when {
                category == null -> ""
                category.parentId == null -> category.name
                else -> parent?.name.orEmpty()
            }
            val subCategoryName = if (category?.parentId != null) category.name else ""
            rows += listOf(
                txn.occurredAt.toLocalDateTime(timeProvider.zone).date.toString(),
                typeLabel(txn.type),
                txn.amount.toPlainString(),
                topLevelName,
                subCategoryName,
                txn.tagIds.mapNotNull { tagNames[it] }.joinToString(separator = "|"),
                accountNames[txn.accountId] ?: txn.accountId,
                txn.toAccountId?.let { accountNames[it] ?: it }.orEmpty(),
                txn.title,
                txn.remark,
                sourceLabel(txn.source),
            )
        }
        return CsvWriter.write(rows, includeBom = true)
    }

    private fun typeLabel(type: TransactionType): String = when (type) {
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
    }

    companion object {
        /** M5 对外备份/导入格式的表头；不要与 M3 明细导出混用。 */
        val HEADER: List<String> = listOf(
            "日期",
            "类型",
            "金额",
            "分类",
            "子分类",
            "标签",
            "账户",
            "目标账户",
            "标题",
            "备注",
            "来源",
        )
    }
}
