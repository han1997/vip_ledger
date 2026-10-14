package com.han1997.vipledger.feature.ledger.ui.billimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.usecase.importbill.BillImporter
import com.han1997.vipledger.core.domain.usecase.importbill.ImportTarget
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「导入账单」页状态持有者(R9)。
 *
 * 只注入 `:core:domain` 的 [BillImporter] 接口与领域用例;**不碰 `Uri`**(SAF 读盘由界面层
 * `readBytesFromUri` 完成,VM 只收字节),一次性提示用枚举,中文文案在 Screen 里查 `strings.xml`。
 *
 * 解析发生在 `inspect` 内(真实样本 6008 行放 `Dispatchers.IO`);落库在 `commit` 的单个事务里,
 * 通过 [ImportBillAction.StartImport] 触发的 `onProgress` 回报逐行进度。失败**不展示 `AppError.message`**,
 * 只映射成 [ImportError] 的分类文案。
 */
@HiltViewModel
class ImportBillViewModel @Inject constructor(
    private val importer: BillImporter,
    observeCurrentLedger: ObserveCurrentLedgerUseCase,
    private val txnRepository: TxnRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ImportBillUiState())
    val uiState: StateFlow<ImportBillUiState> = mutableState.asStateFlow()

    /** 选中的文件字节;不含任何 Uri(界面层读完即丢 Uri)。 */
    private var fileBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            observeCurrentLedger().collectLatest { ledger ->
                val count = ledger?.let { current ->
                    runCatching { txnRepository.countByLedger(current.id) }.getOrDefault(0)
                } ?: 0
                mutableState.update {
                    it.copy(
                        currentLedgerId = ledger?.id,
                        currentLedgerName = ledger?.name.orEmpty(),
                        currentLedgerTxnCount = count,
                    )
                }
            }
        }
    }

    fun onAction(action: ImportBillAction) {
        when (action) {
            is ImportBillAction.FilePicked -> {
                fileBytes = action.bytes
                parsePreview()
            }

            ImportBillAction.FilePickFailed -> mutableState.update {
                it.copy(parsing = false, error = ImportError.READ_FAILED, preview = null)
            }

            is ImportBillAction.SelectTarget -> selectTarget(action.target)

            ImportBillAction.ToggleCategories -> mutableState.update {
                it.copy(showNewCategories = !it.showNewCategories)
            }

            ImportBillAction.ToggleAccounts -> mutableState.update {
                it.copy(showNewAccounts = !it.showNewAccounts)
            }

            ImportBillAction.StartImport -> startImport()

            ImportBillAction.Reset -> {
                fileBytes = null
                mutableState.update { state ->
                    ImportBillUiState(
                        currentLedgerId = state.currentLedgerId,
                        currentLedgerName = state.currentLedgerName,
                        currentLedgerTxnCount = state.currentLedgerTxnCount,
                    )
                }
            }

            ImportBillAction.ConsumeNotice -> mutableState.update { it.copy(notice = null) }
        }
    }

    private fun parsePreview() {
        val bytes = fileBytes ?: return
        val target = targetFor(mutableState.value)
        viewModelScope.launch {
            mutableState.update { it.copy(parsing = true, error = null, preview = null, report = null) }
            val result = withContext(Dispatchers.IO) { importer.inspect(bytes, target) }
            mutableState.update { state ->
                when (result) {
                    is AppResult.Success -> state.copy(parsing = false, preview = result.value, error = null)
                    is AppResult.Failure -> state.copy(
                        parsing = false,
                        preview = null,
                        error = errorOf(result.error),
                    )
                }
            }
        }
    }

    /**
     * 落点 → [ImportTarget]:新建分支按内置分类树预判,导入当前账本按该账本现况预判。
     * 「当前账本」不可用时兜底新建,保证 `ImportTarget` 二选一约束恒成立。
     */
    private fun targetFor(state: ImportBillUiState): ImportTarget = when (state.target) {
        ImportTargetChoice.NEW_LEDGER -> ImportTarget.newLedger(state.newLedgerName)
        ImportTargetChoice.CURRENT_LEDGER -> state.currentLedgerId
            ?.let { ImportTarget.existing(it) }
            ?: ImportTarget.newLedger(state.newLedgerName)
    }

    private fun selectTarget(target: ImportTargetChoice) {
        if (target == ImportTargetChoice.CURRENT_LEDGER && !mutableState.value.canImportToCurrent) {
            // 原型:无账本时点「导入当前账本」给一次性提示,不改选择。
            mutableState.update { it.copy(notice = ImportNotice.EXISTING_LEDGER) }
            return
        }
        mutableState.update { it.copy(target = target) }
        // 换落点后「将新建」的清单口径会变(按目标账本现况判重),有源文件就重算预览。
        if (fileBytes != null && mutableState.value.preview != null) parsePreview()
    }

    private fun startImport() {
        val state = mutableState.value
        val preview = state.preview ?: return
        if (!state.canStart) return
        val target = targetFor(state)
        viewModelScope.launch {
            mutableState.update {
                it.copy(importing = true, error = null, progress = ImportProgress(0, preview.rows.size))
            }
            // 预览由同一份字节在本页解析产出;commit 直接消费预览,不再重复解析源文件。
            val result = withContext(Dispatchers.IO) {
                importer.commit(preview, target) { done, total ->
                    mutableState.update { it.copy(progress = ImportProgress(done, total)) }
                }
            }
            mutableState.update { current ->
                when (result) {
                    is AppResult.Success -> current.copy(
                        importing = false,
                        progress = null,
                        preview = null,
                        report = result.value,
                        notice = ImportNotice.DONE,
                    )

                    is AppResult.Failure -> current.copy(
                        importing = false,
                        progress = null,
                        error = ImportError.IMPORT_FAILED,
                    )
                }
            }
        }
    }

    private fun errorOf(error: AppError): ImportError = when (error) {
        is AppError.Parse -> ImportError.UNSUPPORTED_FORMAT
        is AppError.Storage -> ImportError.READ_FAILED
        else -> ImportError.UNSUPPORTED_FORMAT
    }
}