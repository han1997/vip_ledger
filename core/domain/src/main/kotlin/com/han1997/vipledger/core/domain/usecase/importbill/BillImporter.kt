package com.han1997.vipledger.core.domain.usecase.importbill

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType

/**
 * 导入落点:**二选一** —— 导进某个已有账本,或新建一个独立账本。
 *
 * 两者互斥:`ledgerId == null` 时 [createLedgerNamed] 必须非空,反之亦然;都非空/都为空都非法。
 * 「新建」分支由 `CreateLedgerUseCase` 落地(自带现金账户 + 内置分类 + 自己的事务),
 * 不在 Importer 里自行拼装。
 */
data class ImportTarget(
    val ledgerId: String? = null,
    val createLedgerNamed: String? = null,
) {
    init {
        require((ledgerId == null) != (createLedgerNamed == null)) {
            "ImportTarget 要求 ledgerId 与 createLedgerNamed 二选一(且只能选一个)"
        }
    }

    companion object {
        fun existing(ledgerId: String): ImportTarget = ImportTarget(ledgerId = ledgerId)

        fun newLedger(name: String): ImportTarget = ImportTarget(createLedgerNamed = name)
    }
}

/**
 * 单条待导入流水(**格式无关**):源文件的列序 / 列名一律不进本模型,
 * 分类只保留目标「父名 + 二级名」(二级为 null 表示落一级本身),账户只保留目标名,
 * 落库时再把名字解析成 id。
 *
 * [sourceRow] 是源文件内的 1 基行号(表头 = 1,第一条数据 = 2),失败样例按它报给用户。
 */
data class BillImportRow(
    val sourceRow: Int,
    val occurredAt: Long,
    val type: TransactionType,
    val amountCents: Long,
    val categoryParentName: String,
    val categoryChildName: String?,
    val accountName: String,
    val title: String,
    val remark: String,
    val excludeFromStats: Boolean,
    val tagName: String?,
)

/** 预览里「将新建」的二级分类。一级分类内置树已带,不需要新建(缺失时兜底新建,不进这里)。 */
data class PendingBillCategory(
    val parentName: String,
    val name: String,
    val kind: CategoryKind,
)

/** 预览里「将新建」的账户。 */
data class PendingBillAccount(
    val name: String,
    val type: AccountType,
)

/**
 * `inspect` 产出的预览计划(`.trellis/spec/android/import-export.md` 一木 xls 一节)。
 *
 * 只解析、不落库:分类 / 账户映射的结果全在这里,[rows] 直接交给 `commit` 落库,
 * 不重复解析源文件(真实样本 2.3MB / 6008 行)。
 */
data class BillImportPreview(
    /** 成功解析的数据行数(不含表头与跳过行)。 */
    val dataRowCount: Int,
    /** 最早 / 最晚发生时刻(epoch 毫秒),无有效行时 null。 */
    val startAt: Long?,
    val endAt: Long?,
    /** 支出合计(正数,单位分)。 */
    val expenseCents: Long,
    /** 收入合计(正数,单位分)。 */
    val incomeCents: Long,
    /** 整行空白等被跳过的行号。 */
    val skippedRows: List<Int>,
    /** 解析失败的行号(坏日期 / 坏金额 / 未知收支类型)。 */
    val failedRows: List<Int>,
    val newCategories: List<PendingBillCategory>,
    val newAccounts: List<PendingBillAccount>,
    /** 已映射、待落库的领域行。 */
    val rows: List<BillImportRow>,
) {
    /** 净额(分):收入 − 支出,可为负。 */
    val netCents: Long get() = incomeCents - expenseCents
}

/** `commit` 落库后的导入报告(与 CSV 报告同口径,另加新建分类 / 账户计数)。 */
data class BillImportReport(
    val ledgerId: String,
    val inserted: Int,
    val duplicates: Int,
    val failedRows: List<Int>,
    val skippedRows: List<Int>,
    val newCategoryCount: Int,
    val newAccountCount: Int,
)

/**
 * 记账 App 账单的**格式无关**导入接口(`.trellis/spec/android/import-export.md` 「导入」一节)。
 *
 * - [inspect] 只解析,**不落库**:返回一个可预览的计划(行数、日期区间、金额合计、将新建的分类/账户、失败行)。
 * - [commit] 落库:把 [inspect] 产出的计划写进数据库,整批在一个事务内、失败整体回滚。
 *
 * 接口位于 `:core:domain`,`:core:data` 提供实现(`YimuLedgerImporter`),`feature:*` 只注入本接口,
 * 不依赖 `:core:data`(architecture.md「接口向内、实现向外」)。
 * `CsvLedgerImporter` 作为既有 CSV 实现保持原样不动,不迁入本接口;等接第三种真实格式时再让 CSV 迁入。
 *
 * [commit] 的 [onProgress] 用于大文件导入的逐行进度回报(可选,默认空实现)。
 */
interface BillImporter {
    suspend fun inspect(source: ByteArray, target: ImportTarget): AppResult<BillImportPreview>

    suspend fun commit(
        preview: BillImportPreview,
        target: ImportTarget,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): AppResult<BillImportReport>
}