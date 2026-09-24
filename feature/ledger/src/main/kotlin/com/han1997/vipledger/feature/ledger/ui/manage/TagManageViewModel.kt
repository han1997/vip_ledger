package com.han1997.vipledger.feature.ledger.ui.manage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.domain.usecase.tag.DeleteTagUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.core.domain.usecase.tag.SaveTagUseCase
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
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

/** 标签管理：新建 / 改名 / 删除（软删，历史账目的标签关联保留）。 */
@HiltViewModel
class TagManageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeTags: ObserveTagsUseCase,
    private val saveTag: SaveTagUseCase,
    private val deleteTag: DeleteTagUseCase,
) : ViewModel() {

    private val ledgerId: String =
        savedStateHandle.get<String>(LedgerRoutes.ARG_LEDGER_ID).orEmpty()

    private val local = MutableStateFlow(TagManageLocal())

    val uiState: StateFlow<TagManageUiState> = combine(observeTags(ledgerId), local) { tags, local ->
        TagManageUiState(
            isLoading = false,
            tags = tags,
            editor = local.editor,
            pendingDelete = local.pendingDelete,
            notice = local.notice,
            noticeDetail = local.noticeDetail,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagManageUiState())

    fun onAction(action: TagManageAction) {
        when (action) {
            TagManageAction.OpenCreate -> local.update { it.copy(editor = TagEditor()) }

            is TagManageAction.OpenEdit -> local.update {
                it.copy(editor = TagEditor(tagId = action.tag.id, name = action.tag.name))
            }

            is TagManageAction.UpdateName -> local.update {
                it.copy(editor = it.editor?.copy(name = action.text))
            }

            TagManageAction.DismissEditor -> local.update { it.copy(editor = null) }

            TagManageAction.SaveEditor -> saveEditor()

            is TagManageAction.RequestDelete -> local.update { it.copy(pendingDelete = action.tag) }

            TagManageAction.ConfirmDelete -> confirmDelete()

            TagManageAction.DismissDelete -> local.update { it.copy(pendingDelete = null) }

            TagManageAction.ConsumeNotice -> local.update { it.copy(notice = null, noticeDetail = null) }
        }
    }

    private fun saveEditor() {
        val editor = local.value.editor ?: return
        val block: suspend () -> AppResult<*> = { saveTag(editor.tagId, ledgerId, editor.name) }
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
        val tag = local.value.pendingDelete ?: return
        viewModelScope.launch {
            val result = deleteTag(tag.id)
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

    private data class TagManageLocal(
        val editor: TagEditor? = null,
        val pendingDelete: Tag? = null,
        val notice: ManageNotice? = null,
        val noticeDetail: String? = null,
    )
}