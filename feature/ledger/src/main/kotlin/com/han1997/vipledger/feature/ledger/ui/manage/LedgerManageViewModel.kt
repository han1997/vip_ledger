package com.han1997.vipledger.feature.ledger.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.usecase.ledger.ArchiveLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.CreateLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.DeleteLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveLedgersUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.SelectLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.SetDefaultLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.UpdateLedgerUseCase
import com.han1997.vipledger.feature.ledger.ui.common.ManageNotice
import com.han1997.vipledger.feature.ledger.ui.common.failureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 账本管理：新建（自带内置分类）/ 改名 / 设为默认 / 归档 / 删除（仅限无账目）。 */
@HiltViewModel
class LedgerManageViewModel @Inject constructor(
    observeLedgers: ObserveLedgersUseCase,
    observeCurrentLedger: ObserveCurrentLedgerUseCase,
    private val createLedger: CreateLedgerUseCase,
    private val updateLedger: UpdateLedgerUseCase,
    private val setDefaultLedger: SetDefaultLedgerUseCase,
    private val archiveLedger: ArchiveLedgerUseCase,
    private val deleteLedger: DeleteLedgerUseCase,
    private val selectLedger: SelectLedgerUseCase,
) : ViewModel() {

    /** 纯粹的界面态（弹窗、确认框、一次性提示），与数据流分开，避免相互触发重建。 */
    private val local = MutableStateFlow(LedgerManageLocal())

    private val content = combine(
        observeLedgers(includeArchived = true),
        observeCurrentLedger(),
    ) { ledgers, current ->
        LedgerSnapshot(ledgers = ledgers, currentLedgerId = current?.id, loaded = true)
    }

    val uiState: StateFlow<LedgerManageUiState> = combine(content, local) { snapshot, local ->
        LedgerManageUiState(
            isLoading = !snapshot.loaded,
            ledgers = snapshot.ledgers,
            currentLedgerId = snapshot.currentLedgerId,
            editor = local.editor,
            pendingDelete = local.pendingDelete,
            notice = local.notice,
            noticeDetail = local.noticeDetail,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerManageUiState())

    fun onAction(action: LedgerManageAction) {
        when (action) {
            LedgerManageAction.OpenCreate -> local.update { it.copy(editor = LedgerEditor()) }

            is LedgerManageAction.OpenEdit -> local.update {
                it.copy(editor = LedgerEditor(ledgerId = action.ledger.id, name = action.ledger.name))
            }

            is LedgerManageAction.UpdateEditorName -> local.update {
                it.copy(editor = it.editor?.copy(name = action.text))
            }

            LedgerManageAction.DismissEditor -> local.update { it.copy(editor = null) }

            LedgerManageAction.SaveEditor -> saveEditor()

            is LedgerManageAction.Select -> execute(
                success = ManageNotice.SWITCHED,
                block = { selectLedger(action.ledgerId) },
            )

            is LedgerManageAction.SetDefault -> execute(
                success = ManageNotice.DEFAULT_SET,
                block = { setDefaultLedger(action.ledgerId) },
            )

            is LedgerManageAction.SetArchived -> execute(
                success = if (action.archived) ManageNotice.ARCHIVED else ManageNotice.UNARCHIVED,
                block = { archiveLedger(action.ledgerId, action.archived) },
            )

            is LedgerManageAction.RequestDelete -> local.update { it.copy(pendingDelete = action.ledger) }

            LedgerManageAction.ConfirmDelete -> confirmDelete()

            LedgerManageAction.DismissDelete -> local.update { it.copy(pendingDelete = null) }

            LedgerManageAction.ConsumeNotice -> local.update { it.copy(notice = null, noticeDetail = null) }
        }
    }

    private fun saveEditor() {
        val editor = local.value.editor ?: return
        val block: suspend () -> AppResult<*> = {
            if (editor.ledgerId == null) createLedger(editor.name) else updateLedger(editor.ledgerId, editor.name)
        }
        viewModelScope.launch {
            val result = block()
            val success = result.isSuccess
            local.update {
                it.copy(
                    editor = if (success) null else it.editor,
                    notice = if (success) ManageNotice.SAVED else ManageNotice.FAILED,
                    noticeDetail = result.failureMessage(),
                )
            }
        }
    }

    private fun confirmDelete() {
        val ledger = local.value.pendingDelete ?: return
        viewModelScope.launch {
            val result = deleteLedger(ledger.id)
            val success = result.isSuccess
            local.update {
                it.copy(
                    pendingDelete = null,
                    notice = if (success) ManageNotice.DELETED else ManageNotice.FAILED,
                    noticeDetail = result.failureMessage(),
                )
            }
        }
    }

    private fun execute(success: ManageNotice, block: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            val result = block()
            local.update {
                it.copy(
                    notice = if (result.isSuccess) success else ManageNotice.FAILED,
                    noticeDetail = result.failureMessage(),
                )
            }
        }
    }

    private data class LedgerManageLocal(
        val editor: LedgerEditor? = null,
        val pendingDelete: Ledger? = null,
        val notice: ManageNotice? = null,
        val noticeDetail: String? = null,
    )

    private data class LedgerSnapshot(
        val ledgers: List<Ledger> = emptyList(),
        val currentLedgerId: String? = null,
        val loaded: Boolean = false,
    )
}