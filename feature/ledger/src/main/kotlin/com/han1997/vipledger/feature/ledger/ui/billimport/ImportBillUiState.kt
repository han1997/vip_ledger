package com.han1997.vipledger.feature.ledger.ui.billimport

import com.han1997.vipledger.core.domain.usecase.importbill.BillImportPreview
import com.han1997.vipledger.core.domain.usecase.importbill.BillImportReport
import com.han1997.vipledger.core.domain.usecase.importbill.PendingBillAccount
import com.han1997.vipledger.core.domain.usecase.importbill.PendingBillCategory

/** 导入落点二选一(R3):新建独立账本,或并入当前账本。 */
enum class ImportTargetChoice { NEW_LEDGER, CURRENT_LEDGER }

/** 落库进度:已处理行数 / 总行数(照 `AiEntryViewModel.ImageProgress` 的形态)。 */
data class ImportProgress(val done: Int, val total: Int)

/** 一次性提示;中文文案由界面层查 `strings.xml`,UI 状态里不放中文(ui.md)。 */
enum class ImportNotice { DONE, EXISTING_LEDGER }

/**
 * 「导入账单」页状态(R9 四态)。
 *
 * 只暴露枚举、计数与 `:core:domain` 的预览/报告模型(不 import `:core:data`),
 * 不放中文、不放 `Uri`;解析失败以 [error] 承载(界面层映射成 `billimport_error_*` 文案),
 * **不展示 `AppError.message`**。
 */
data class ImportBillUiState(
    /** 正在解析选中的文件(选文件 → 预览 的过渡态)。 */
    val parsing: Boolean = false,
    /** 正在落库;进行中 [progress] 非空。 */
    val importing: Boolean = false,
    val progress: ImportProgress? = null,
    /** 解析成功后的预览计划;为 null 表示还在「选文件」空态。 */
    val preview: BillImportPreview? = null,
    /** 导入落点选择,默认「新建独立账本」(最安全的迁移方式)。 */
    val target: ImportTargetChoice = ImportTargetChoice.NEW_LEDGER,
    /** 当前账本 id / 名字(null = 还没账本,「导入当前账本」禁用)。 */
    val currentLedgerId: String? = null,
    val currentLedgerName: String = "",
    /** 当前账本已有流水条数;> 0 时选「导入当前账本」要二次确认(R3)。 */
    val currentLedgerTxnCount: Int = 0,
    /** 新建账本的默认名(原型:新建账本「一木记账」)。 */
    val newLedgerName: String = DEFAULT_NEW_LEDGER_NAME,
    val showNewCategories: Boolean = false,
    val showNewAccounts: Boolean = false,
    /** 落库完成后的报告;非空即「报告」态。 */
    val report: BillImportReport? = null,
    /** 解析/读取失败:界面映射成 `billimport_error_format` / `billimport_error_read`。 */
    val error: ImportError? = null,
    val notice: ImportNotice? = null,
) {
    /** 当前账本可选(有账本即可选;无流水的账本不需要二次确认)。 */
    val canImportToCurrent: Boolean get() = currentLedgerId != null

    /** 「导入当前账本」需要二次确认(账本里已有流水,R3)。 */
    val currentNeedsConfirm: Boolean get() = currentLedgerId != null && currentLedgerTxnCount > 0

    /** 可开始导入:有预览计划、不在进行中、还没出报告。 */
    val canStart: Boolean get() = preview != null && !importing && !parsing && report == null

    /** 预览里将新建的分类清单(界面展示用)。 */
    val newCategories: List<PendingBillCategory> get() = preview?.newCategories.orEmpty()

    /** 预览里将新建的账户清单(界面展示用)。 */
    val newAccounts: List<PendingBillAccount> get() = preview?.newAccounts.orEmpty()

    companion object {
        const val DEFAULT_NEW_LEDGER_NAME: String = "一木记账"
    }
}

/** 失败原因分类(具体错误详情与 `AppError.message` 一律不展示给用户,只做分类文案)。 */
enum class ImportError { READ_FAILED, UNSUPPORTED_FORMAT, IMPORT_FAILED }

sealed interface ImportBillAction {
    /** 用户通过 SAF 选中并读回了文件字节(界面层读出,VM 只收字节)。 */
    data class FilePicked(val bytes: ByteArray) : ImportBillAction

    /** SAF 读回失败或没选中文件。 */
    data object FilePickFailed : ImportBillAction

    data class SelectTarget(val target: ImportTargetChoice) : ImportBillAction

    data object ToggleCategories : ImportBillAction

    data object ToggleAccounts : ImportBillAction

    data object StartImport : ImportBillAction

    /** 报告态「重新选择文件」:清空预览与报告,回到空态。 */
    data object Reset : ImportBillAction

    data object ConsumeNotice : ImportBillAction
}