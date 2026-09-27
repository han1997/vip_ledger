package com.han1997.vipledger.feature.ledger.ui.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.RecurringRule
import com.han1997.vipledger.core.domain.repository.RecurringRuleRepository
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.recurring.RecurringRuleInput
import com.han1997.vipledger.core.domain.usecase.recurring.SaveRecurringRuleUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toDateColumnLocalDate
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import com.han1997.vipledger.feature.ledger.ui.common.failureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * 周期规则编辑：新建与编辑共用一套表单，编辑态先把原规则读出来预填。
 */
@HiltViewModel
class RecurringEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAccounts: ObserveAccountsUseCase,
    observeCategoryTree: ObserveCategoryTreeUseCase,
    observeTags: ObserveTagsUseCase,
    private val ruleRepository: RecurringRuleRepository,
    private val saveRule: SaveRecurringRuleUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val ledgerId: String =
        savedStateHandle.get<String>(LedgerRoutes.ARG_LEDGER_ID).orEmpty()
    private val ruleId: String? =
        savedStateHandle.get<String>(LedgerRoutes.ARG_RULE_ID)?.takeIf { it.isNotBlank() }

    private val zone: TimeZone = timeProvider.zone

    private val _uiState = MutableStateFlow(
        RecurringEditUiState(
            ledgerId = ledgerId,
            ruleId = ruleId,
            isEditing = ruleId != null,
            startDate = timeProvider.nowMillis().toLocalDate(zone),
        ),
    )
    val uiState: StateFlow<RecurringEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeAccounts(ledgerId, includeArchived = false).collect { accounts ->
                _uiState.update { state ->
                    val current = state.accountId?.takeIf { id -> accounts.any { it.id == id } }
                    state.copy(accounts = accounts, accountId = current ?: accounts.firstOrNull()?.id)
                }
            }
        }
        viewModelScope.launch {
            observeCategoryTree(ledgerId).collect { nodes -> _uiState.update { it.copy(categoryNodes = nodes) } }
        }
        viewModelScope.launch {
            observeTags(ledgerId).collect { tags -> _uiState.update { it.copy(tags = tags) } }
        }
        if (ruleId != null) {
            viewModelScope.launch {
                ruleRepository.getById(ruleId)?.let { rule ->
                    _uiState.update { it.copy(isLoading = false).withRule(rule) }
                } ?: _uiState.update { it.copy(isLoading = false) }
            }
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onAction(action: RecurringEditAction) {
        when (action) {
            is RecurringEditAction.UpdateName -> update { it.copy(name = action.text) }
            is RecurringEditAction.SelectType -> update { it.copy(txnType = action.type, categoryId = null) }
            is RecurringEditAction.UpdateAmount -> update { it.copy(amountInput = action.text) }
            is RecurringEditAction.SelectAccount -> update { it.copy(accountId = action.accountId).resolvedTarget() }
            is RecurringEditAction.SelectTargetAccount -> update { it.copy(toAccountId = action.accountId) }
            is RecurringEditAction.SelectCategory -> update { it.copy(categoryId = action.categoryId) }
            is RecurringEditAction.ToggleTag -> update { state ->
                state.copy(
                    tagIds = if (action.tagId in state.tagIds) state.tagIds - action.tagId
                    else state.tagIds + action.tagId,
                )
            }
            is RecurringEditAction.UpdateTitle -> update { it.copy(title = action.text) }
            is RecurringEditAction.UpdateRemark -> update { it.copy(remark = action.text) }
            is RecurringEditAction.SelectFrequency -> update { it.copy(freq = action.freq) }
            is RecurringEditAction.UpdateInterval -> update { it.copy(intervalInput = action.text) }
            is RecurringEditAction.SelectWeekday -> update {
                it.copy(weekday = if (it.weekday == action.weekday) null else action.weekday)
            }
            is RecurringEditAction.UpdateMonthDay -> update { it.copy(monthDayInput = action.text) }
            is RecurringEditAction.UpdateMonth -> update { it.copy(monthInput = action.text) }
            is RecurringEditAction.SelectStartDate -> update { it.copy(startDate = action.date) }
            is RecurringEditAction.SelectEndType -> update { it.copy(endType = action.endType) }
            is RecurringEditAction.SelectEndDate -> update { it.copy(endDate = action.date) }
            is RecurringEditAction.UpdateMaxOccurrences -> update { it.copy(maxOccurrencesInput = action.text) }
            is RecurringEditAction.SelectGenerateMode -> update { it.copy(generateMode = action.mode) }
            is RecurringEditAction.SelectMissedPolicy -> update { it.copy(missedPolicy = action.policy) }
            RecurringEditAction.Save -> save()
            RecurringEditAction.ConsumeError -> update { it.copy(error = null, saveFailureMessage = null) }
        }
    }

    private fun update(transform: (RecurringEditUiState) -> RecurringEditUiState) {
        _uiState.update { transform(it).copy(error = null) }
    }

    private fun save() {
        val state = _uiState.value
        state.validationError()?.let { error ->
            _uiState.update { it.copy(error = error) }
            return
        }
        val input = RecurringRuleInput(
            ledgerId = ledgerId,
            ruleId = ruleId,
            name = state.name,
            txnType = state.txnType,
            amount = Money.parse(state.amountInput) ?: Money.ZERO,
            accountId = state.accountId.orEmpty(),
            toAccountId = state.toAccountId,
            categoryId = state.categoryId,
            tagIds = state.tagIds.toList(),
            title = state.title,
            remark = state.remark,
            freq = state.freq,
            interval = state.intervalValue() ?: 1,
            byWeekday = state.weekday,
            byMonthDay = state.monthDayValue(),
            byMonth = state.monthValue(),
            startDate = state.startDate,
            endType = state.endType,
            endDate = state.endDate,
            maxOccurrences = state.maxOccurrencesValue(),
            generateMode = state.generateMode,
            missedPolicy = state.missedPolicy,
        )
        viewModelScope.launch {
            val result = saveRule(input)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(saved = true)
                } else {
                    it.copy(error = RecurringEditError.SAVE_FAILED, saveFailureMessage = result.failureMessage())
                }
            }
        }
    }

    private fun RecurringEditUiState.withRule(rule: RecurringRule) = copy(
        name = rule.name,
        txnType = rule.txnType,
        amountInput = rule.amount.toPlainString(),
        accountId = rule.accountId,
        toAccountId = rule.toAccountId,
        categoryId = rule.categoryId,
        tagIds = rule.tagIds.toSet(),
        title = rule.title,
        remark = rule.remark,
        freq = rule.freq,
        intervalInput = rule.interval.toString(),
        weekday = rule.byWeekday,
        monthDayInput = rule.byMonthDay?.toString().orEmpty(),
        monthInput = rule.byMonth?.toString().orEmpty(),
        startDate = rule.startAt.toDateColumnLocalDate(),
        endType = rule.endType,
        endDate = rule.endDate?.toDateColumnLocalDate(),
        maxOccurrencesInput = rule.maxOccurrences?.toString().orEmpty(),
        generateMode = rule.generateMode,
        missedPolicy = rule.missedPolicy,
        saved = false,
    )

    /** 目标账户不能与转出账户相同；切换账户后自动清掉冲突选择。 */
    private fun RecurringEditUiState.resolvedTarget() =
        copy(toAccountId = toAccountId?.takeIf { it != accountId })
}
