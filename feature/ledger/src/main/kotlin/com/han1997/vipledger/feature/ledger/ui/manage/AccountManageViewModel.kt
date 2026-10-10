package com.han1997.vipledger.feature.ledger.ui.manage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.AccountDraft
import com.han1997.vipledger.core.domain.usecase.account.ArchiveAccountUseCase
import com.han1997.vipledger.core.domain.usecase.account.DeleteAccountUseCase
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.account.SaveAccountUseCase
import com.han1997.vipledger.core.model.money.Money
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** 账户管理：新建 / 编辑 / 归档 / 删除（有流水时只能归档）。 */
@HiltViewModel
class AccountManageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAccounts: ObserveAccountsUseCase,
    private val saveAccount: SaveAccountUseCase,
    private val archiveAccount: ArchiveAccountUseCase,
    private val deleteAccount: DeleteAccountUseCase,
) : ViewModel() {

    private val ledgerId: String =
        savedStateHandle.get<String>(LedgerRoutes.ARG_LEDGER_ID).orEmpty()

    private val local = MutableStateFlow(AccountManageLocal())

    private val content = observeAccounts(ledgerId, includeArchived = true).onEach { accounts ->
        val requested = savedStateHandle.get<String>("editAccountId")
        if (savedStateHandle.get<Boolean>("editorOpened") != true && !requested.isNullOrBlank()) {
            accounts.find { it.id == requested }?.let { account ->
                savedStateHandle["editorOpened"] = true
                local.update { it.copy(editor = account.toEditor()) }
            }
        }
    }

    val uiState: StateFlow<AccountManageUiState> = combine(content, local) { accounts, local ->
        AccountManageUiState(
            isLoading = false,
            accounts = accounts.filter { local.showArchived || !it.isArchived },
            showArchived = local.showArchived,
            editor = local.editor,
            pendingDelete = local.pendingDelete,
            notice = local.notice,
            noticeDetail = local.noticeDetail,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountManageUiState())

    fun onAction(action: AccountManageAction) {
        when (action) {
            AccountManageAction.OpenCreate -> local.update { it.copy(editor = AccountEditor()) }

            is AccountManageAction.OpenEdit -> local.update { it.copy(editor = action.account.toEditor()) }

            is AccountManageAction.UpdateEditorName -> updateEditor { it.copy(name = action.text) }

            is AccountManageAction.UpdateEditorType -> updateEditor { it.copy(type = action.type) }

            is AccountManageAction.UpdateInitialBalance -> updateEditor {
                it.copy(initialBalanceInput = action.text)
            }

            is AccountManageAction.UpdateCreditLimit -> updateEditor { it.copy(creditLimitInput = action.text) }

            is AccountManageAction.UpdateBillingDay -> updateEditor { it.copy(billingDayInput = action.text) }

            is AccountManageAction.UpdateDueDay -> updateEditor { it.copy(dueDayInput = action.text) }

            is AccountManageAction.UpdateIncludeInNetWorth -> updateEditor {
                it.copy(includeInNetWorth = action.include)
            }

            AccountManageAction.DismissEditor -> local.update { it.copy(editor = null) }

            AccountManageAction.SaveEditor -> saveEditor()

            is AccountManageAction.SetArchived -> execute(
                success = if (action.archived) ManageNotice.ARCHIVED else ManageNotice.UNARCHIVED,
                block = { archiveAccount(action.accountId, action.archived) },
            )

            is AccountManageAction.RequestDelete -> local.update { it.copy(pendingDelete = action.account) }

            AccountManageAction.ConfirmDelete -> confirmDelete()

            AccountManageAction.DismissDelete -> local.update { it.copy(pendingDelete = null) }

            AccountManageAction.ToggleShowArchived -> local.update { it.copy(showArchived = !it.showArchived) }

            AccountManageAction.ConsumeNotice -> local.update { it.copy(notice = null, noticeDetail = null) }
        }
    }

    private fun updateEditor(transform: (AccountEditor) -> AccountEditor) {
        local.update { state -> state.copy(editor = state.editor?.let(transform)) }
    }

    private fun saveEditor() {
        val editor = local.value.editor ?: return
        val draft = AccountDraft(
            ledgerId = ledgerId,
            name = editor.name,
            type = editor.type,
            initialBalance = Money.parse(editor.initialBalanceInput) ?: Money.ZERO,
            creditLimit = editor.creditLimitInput.takeIf { it.isNotBlank() }?.let { Money.parse(it) },
            billingDay = editor.billingDayInput.toIntOrNull(),
            dueDay = editor.dueDayInput.toIntOrNull(),
            includeInNetWorth = editor.includeInNetWorth,
        )
        val block: suspend () -> AppResult<*> = { saveAccount(editor.accountId, draft) }
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
        val account = local.value.pendingDelete ?: return
        viewModelScope.launch {
            val result = deleteAccount(account.id)
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

    private fun Account.toEditor() = AccountEditor(
        accountId = id,
        name = name,
        type = type,
        initialBalanceInput = initialBalance.toPlainString(),
        creditLimitInput = creditLimit?.toPlainString().orEmpty(),
        billingDayInput = billingDay?.toString().orEmpty(),
        dueDayInput = dueDay?.toString().orEmpty(),
        includeInNetWorth = includeInNetWorth,
    )

    private data class AccountManageLocal(
        val showArchived: Boolean = false,
        val editor: AccountEditor? = null,
        val pendingDelete: Account? = null,
        val notice: ManageNotice? = null,
        val noticeDetail: String? = null,
    )
}
