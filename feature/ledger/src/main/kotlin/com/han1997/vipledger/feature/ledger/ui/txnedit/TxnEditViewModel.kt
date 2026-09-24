package com.han1997.vipledger.feature.ledger.ui.txnedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.TxnValidator
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.GetTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.UpdateTxnUseCase
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis

/**
 * 记账编辑。新建与编辑共用一套表单：编辑态先把原账目读出来预填，
 * 保存时把表单字段合并回原账目副本（保留来源、周期/分期归属等不可见字段）。
 */
@HiltViewModel
class TxnEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAccounts: ObserveAccountsUseCase,
    observeCategoryTree: ObserveCategoryTreeUseCase,
    observeTags: ObserveTagsUseCase,
    private val getTxn: GetTxnUseCase,
    private val createTxn: CreateTxnUseCase,
    private val updateTxn: UpdateTxnUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val ledgerId: String =
        savedStateHandle.get<String>(LedgerRoutes.ARG_LEDGER_ID).orEmpty()
    private val txnId: String? =
        savedStateHandle.get<String>(LedgerRoutes.ARG_TXN_ID)?.takeIf { it.isNotBlank() }

    /** 编辑态的原账目：保存时作为「不可见字段」的载体。 */
    private var original: Txn? = null

    private val _uiState = MutableStateFlow(
        TxnEditUiState(
            isLoading = true,
            isEditing = txnId != null,
            ledgerId = ledgerId,
            occurredAt = timeProvider.nowMillis(),
            todayMillis = timeProvider.nowMillis(),
            yesterdayMillis = timeProvider.nowMillis(),
        ),
    )
    val uiState: StateFlow<TxnEditUiState> = _uiState.asStateFlow()

    init {
        val zone = timeProvider.zone
        val today = timeProvider.nowMillis().toLocalDate(zone)
        _uiState.update {
            it.copy(
                todayMillis = today.startOfDayEpochMillis(zone),
                yesterdayMillis = today.minus(1, DateTimeUnit.DAY).startOfDayEpochMillis(zone),
            )
        }

        viewModelScope.launch {
            observeAccounts(ledgerId, includeArchived = false).collect { accounts ->
                _uiState.update { state ->
                    val current = state.accountId?.takeIf { id -> accounts.any { it.id == id } }
                    val next = state.copy(
                        accounts = accounts,
                        accountId = current ?: accounts.firstOrNull()?.id,
                        isLoading = state.isLoading && txnId != null,
                    )
                    next.copy(toAccountId = next.resolvedTargetAccountId())
                }
            }
        }
        viewModelScope.launch {
            observeCategoryTree(ledgerId).collect { nodes ->
                _uiState.update { it.copy(categoryNodes = nodes) }
            }
        }
        viewModelScope.launch {
            observeTags(ledgerId).collect { tags ->
                _uiState.update { it.copy(tags = tags) }
            }
        }
        if (txnId != null) {
            viewModelScope.launch { loadExisting(txnId) }
        }
    }

    private suspend fun loadExisting(id: String) {
        val txn = getTxn(id)
        if (txn == null) {
            _uiState.update { it.copy(isLoading = false, error = TxnEditError.SAVE_FAILED) }
            return
        }
        original = txn
        _uiState.update {
            it.copy(
                isLoading = false,
                isEditing = true,
                type = txn.type,
                amountInput = txn.amount.toPlainString().removePrefix("-"),
                title = txn.title,
                remark = txn.remark,
                occurredAt = txn.occurredAt,
                accountId = txn.accountId,
                toAccountId = txn.toAccountId,
                categoryId = txn.categoryId,
                selectedTagIds = txn.tagIds.toSet(),
                excludeFromStats = txn.excludeFromStats,
            )
        }
    }

    fun onAction(action: TxnEditAction) {
        when (action) {
            is TxnEditAction.SelectType -> _uiState.update { state ->
                val next = state.copy(
                    type = action.type,
                    categoryId = if (state.hasCategoryFor(action.type)) state.categoryId else null,
                )
                next.copy(toAccountId = next.resolvedTargetAccountId())
            }

            is TxnEditAction.UpdateAmount -> _uiState.update {
                it.copy(amountInput = action.text, amountError = false)
            }

            is TxnEditAction.UpdateTitle -> _uiState.update { it.copy(title = action.text) }

            is TxnEditAction.UpdateRemark -> _uiState.update { it.copy(remark = action.text) }

            is TxnEditAction.SelectAccount -> _uiState.update { state ->
                val next = state.copy(accountId = action.accountId)
                next.copy(toAccountId = next.resolvedTargetAccountId())
            }

            is TxnEditAction.SelectTargetAccount -> _uiState.update { it.copy(toAccountId = action.accountId) }

            is TxnEditAction.SelectCategory -> _uiState.update { it.copy(categoryId = action.categoryId) }

            is TxnEditAction.ToggleTag -> _uiState.update { state ->
                val tags = state.selectedTagIds.toMutableSet()
                if (!tags.add(action.tagId)) tags.remove(action.tagId)
                state.copy(selectedTagIds = tags)
            }

            is TxnEditAction.UpdateExcludeFromStats -> _uiState.update {
                it.copy(excludeFromStats = action.exclude)
            }

            is TxnEditAction.SelectDate -> _uiState.update { it.copy(occurredAt = action.millis) }

            TxnEditAction.Save -> save()

            TxnEditAction.ConsumeError -> _uiState.update { it.copy(error = null, saveFailureMessage = null) }
        }
    }

    private fun save() {
        val state = _uiState.value
        val parsed = Money.parse(state.amountInput)
        val amountValid = when (state.type) {
            TransactionType.ADJUST -> parsed != null && !parsed.isZero
            else -> parsed != null && parsed.isPositive
        }
        if (!amountValid) {
            _uiState.update { it.copy(amountError = true, error = TxnEditError.INVALID_AMOUNT) }
            return
        }
        val amount = requireNotNull(parsed)
        val accountId = state.accountId
        if (accountId == null) {
            _uiState.update { it.copy(error = TxnEditError.SAVE_FAILED) }
            return
        }

        viewModelScope.launch {
            val result = original?.let { existing ->
                updateTxn(
                    existing.copy(
                        type = state.type,
                        amount = amount,
                        accountId = accountId,
                        toAccountId = state.toAccountId.takeIf { state.type.requiresTargetAccount },
                        categoryId = state.categoryId,
                        occurredAt = state.occurredAt,
                        title = state.title.trim(),
                        remark = state.remark.trim(),
                        tagIds = state.selectedTagIds.toList(),
                        excludeFromStats = state.excludeFromStats,
                    ),
                )
            } ?: createTxn(
                TxnDraft(
                    ledgerId = ledgerId,
                    type = state.type,
                    amount = amount,
                    accountId = accountId,
                    toAccountId = state.toAccountId.takeIf { state.type.requiresTargetAccount },
                    categoryId = state.categoryId,
                    occurredAt = state.occurredAt,
                    title = state.title.trim(),
                    remark = state.remark.trim(),
                    tagIds = state.selectedTagIds.toList(),
                    excludeFromStats = state.excludeFromStats,
                ),
            )
            if (result.isSuccess) {
                _uiState.update { it.copy(saved = true, error = null, saveFailureMessage = null) }
            } else {
                // AppError.message 是领域层给用户看的说明（如「转账需要选择转入账户」），
                // 直接带出来比笼统的「保存失败」有用（见 core:common AppError 注释）。
                val reason = (result as? AppResult.Failure)?.error?.message
                _uiState.update { it.copy(error = TxnEditError.SAVE_FAILED, saveFailureMessage = reason) }
            }
        }
    }
}