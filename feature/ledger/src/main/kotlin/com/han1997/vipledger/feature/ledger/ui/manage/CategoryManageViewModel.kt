package com.han1997.vipledger.feature.ledger.ui.manage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.usecase.category.DeleteCategoryUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.category.SaveCategoryUseCase
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

/** 分类管理：两级树的增删改；被账目引用的分类不允许删除。 */
@HiltViewModel
class CategoryManageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeCategoryTree: ObserveCategoryTreeUseCase,
    private val saveCategory: SaveCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
) : ViewModel() {

    private val ledgerId: String =
        savedStateHandle.get<String>(LedgerRoutes.ARG_LEDGER_ID).orEmpty()

    private val local = MutableStateFlow(CategoryManageLocal())

    val uiState: StateFlow<CategoryManageUiState> =
        combine(observeCategoryTree(ledgerId), local) { nodes, local ->
            CategoryManageUiState(
                isLoading = false,
                nodes = nodes,
                editor = local.editor,
                pendingDelete = local.pendingDelete,
                notice = local.notice,
            noticeDetail = local.noticeDetail,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryManageUiState())

    fun onAction(action: CategoryManageAction) {
        when (action) {
            is CategoryManageAction.OpenCreateTop -> local.update {
                it.copy(editor = CategoryEditor(kind = action.kind))
            }

            is CategoryManageAction.OpenCreateChild -> local.update {
                it.copy(
                    editor = CategoryEditor(
                        parentId = action.parent.id,
                        kind = action.parent.kind,
                    ),
                )
            }

            is CategoryManageAction.OpenEdit -> local.update {
                it.copy(
                    editor = CategoryEditor(
                        categoryId = action.category.id,
                        parentId = action.category.parentId,
                        name = action.category.name,
                        kind = action.category.kind,
                    ),
                )
            }

            is CategoryManageAction.UpdateName -> local.update {
                it.copy(editor = it.editor?.copy(name = action.text))
            }

            is CategoryManageAction.UpdateKind -> local.update {
                it.copy(editor = it.editor?.copy(kind = action.kind))
            }

            CategoryManageAction.DismissEditor -> local.update { it.copy(editor = null) }

            CategoryManageAction.SaveEditor -> saveEditor()

            is CategoryManageAction.RequestDelete -> local.update { it.copy(pendingDelete = action.category) }

            CategoryManageAction.ConfirmDelete -> confirmDelete()

            CategoryManageAction.DismissDelete -> local.update { it.copy(pendingDelete = null) }

            CategoryManageAction.ConsumeNotice -> local.update { it.copy(notice = null, noticeDetail = null) }
        }
    }

    private fun saveEditor() {
        val editor = local.value.editor ?: return
        val block: suspend () -> AppResult<*> = {
            saveCategory(
                categoryId = editor.categoryId,
                ledgerId = ledgerId,
                parentId = editor.parentId,
                name = editor.name,
                kind = editor.kind,
            )
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
        val category = local.value.pendingDelete ?: return
        viewModelScope.launch {
            val result = deleteCategory(category.id)
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

    private data class CategoryManageLocal(
        val editor: CategoryEditor? = null,
        val pendingDelete: Category? = null,
        val notice: ManageNotice? = null,
        val noticeDetail: String? = null,
    )
}