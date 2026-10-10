package com.han1997.vipledger.feature.ledger.ui.txnedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.ai.AiSettingsRepository
import com.han1997.vipledger.core.domain.calculator.TxnValidator
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.nlp.NlEntryPipeline
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.domain.nlp.CategoryClassifier
import com.han1997.vipledger.core.domain.nlp.LlmParserClient
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.core.domain.usecase.tag.SaveTagUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.ConfirmAiEntryUseCase
import com.han1997.vipledger.core.domain.usecase.txn.GetTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.UpdateTxnUseCase
import com.han1997.vipledger.core.domain.support.LastAccountStore
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.core.domain.ai.cloudClientFor
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    private val observeAccounts: ObserveAccountsUseCase,
    private val observeCategoryTree: ObserveCategoryTreeUseCase,
    private val observeTags: ObserveTagsUseCase,
    private val saveTag: SaveTagUseCase,
    private val getTxn: GetTxnUseCase,
    private val createTxn: CreateTxnUseCase,
    private val updateTxn: UpdateTxnUseCase,
    private val confirmAiEntry: ConfirmAiEntryUseCase,
    private val lastAccountStore: LastAccountStore,
    private val aiSettingsRepository: AiSettingsRepository,
    private val llmClient: LlmParserClient,
    private val classifier: CategoryClassifier,
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
            accountId = if (txnId == null) savedStateHandle.get<String>("accountId")?.takeIf { it.isNotBlank() } else null,
            type = if (txnId == null && savedStateHandle.get<String>("entryType") == "TRANSFER") TransactionType.TRANSFER else TransactionType.EXPENSE,
            occurredAt = timeProvider.nowMillis(),
            todayMillis = timeProvider.nowMillis(),
            yesterdayMillis = timeProvider.nowMillis(),
        ),
    )
    val uiState: StateFlow<TxnEditUiState> = _uiState.asStateFlow()

    init {
        val zone = timeProvider.zone
        if (txnId == null) {
            com.han1997.vipledger.feature.ledger.ui.common.validCalendarDate(savedStateHandle.get<String>(LedgerRoutes.ARG_DATE))?.let { selected ->
                _uiState.update { it.copy(occurredAt = replaceEntryDate(it.occurredAt, selected, zone)) }
            }
        }
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
                    val previous = lastAccountStore.getLastAccountId(ledgerId)
                    val next = state.copy(
                        accounts = accounts,
                        accountId = if (state.isEditing && original != null) state.accountId
                            else state.copy(accounts = accounts).resolvedAccountId(previous),
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
            _uiState.update { it.copy(isLoading = false, editingUnavailable = true, error = TxnEditError.SAVE_FAILED) }
            return
        }
        original = txn
        val nodes = observeCategoryTree(ledgerId).first()
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
                categoryNodes = nodes,
                expandedCategoryId = nodes.firstOrNull { node ->
                    node.category.id == txn.categoryId || node.children.any { child -> child.id == txn.categoryId }
                }?.category?.id,
                selectedTagIds = txn.tagIds.toSet(),
                excludeFromStats = txn.excludeFromStats,
            )
        }
    }

    fun onAction(action: TxnEditAction) {
        if (_uiState.value.isBusy && action != TxnEditAction.ConsumeError) return
        val before = _uiState.value
        val changesForm = when (action) {
            is TxnEditAction.SelectType -> action.type != before.type
            is TxnEditAction.UpdateAmount -> action.text != before.amountInput
            is TxnEditAction.UpdateTitle -> action.text != before.title
            is TxnEditAction.UpdateRemark -> action.text != before.remark
            is TxnEditAction.SelectAccount -> action.accountId != before.accountId
            is TxnEditAction.SelectTargetAccount -> action.accountId != before.toAccountId
            is TxnEditAction.SelectCategory -> action.categoryId != before.categoryId
            is TxnEditAction.ToggleTag -> true
            is TxnEditAction.UpdateExcludeFromStats -> action.exclude != before.excludeFromStats
            else -> false
        }
        if (changesForm) _uiState.update { it.copy(hasUnsavedChanges = true) }
        when (action) {
            is TxnEditAction.SelectType -> _uiState.update { state ->
                val next = state.copy(
                    type = action.type,
                    categoryId = if (state.hasCategoryFor(action.type)) state.categoryId else null,
                )
                next.copy(toAccountId = next.resolvedTargetAccountId(),
                    expandedCategoryId = if (state.categoryKind == next.categoryKind) state.expandedCategoryId else null)
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
            is TxnEditAction.ToggleCategoryParent -> _uiState.update { it.toggleCategoryParent(action.categoryId) }
            TxnEditAction.ToggleTagEditor -> _uiState.update { it.copy(tagEditorExpanded = !it.tagEditorExpanded, tagCreateFailed = false) }
            is TxnEditAction.UpdateTagInput -> _uiState.update { it.copy(tagInput = action.text, tagCreateFailed = false) }
            TxnEditAction.CreateTag -> createTag()

            is TxnEditAction.ToggleTag -> _uiState.update { state ->
                val tags = state.selectedTagIds.toMutableSet()
                if (!tags.add(action.tagId)) tags.remove(action.tagId)
                state.copy(selectedTagIds = tags)
            }

            is TxnEditAction.UpdateExcludeFromStats -> _uiState.update {
                it.copy(excludeFromStats = action.exclude)
            }

            is TxnEditAction.SelectDate -> changeTimestamp { replaceEntryDate(it, action.date, timeProvider.zone) }
            is TxnEditAction.SelectTime -> changeTimestamp { replaceEntryTime(it, action.time, timeProvider.zone) }
            is TxnEditAction.SelectRelativeDate -> {
                val today = timeProvider.nowMillis().toLocalDate(timeProvider.zone)
                changeTimestamp { replaceEntryDate(it, today.minus(action.daysAgo, DateTimeUnit.DAY), timeProvider.zone) }
                refreshToday()
            }
            TxnEditAction.SelectNow -> { changeTimestamp { timeProvider.nowMillis() }; refreshToday() }
            TxnEditAction.RefreshToday -> refreshToday()

            is TxnEditAction.UpdateAiInput -> _uiState.update {
                it.copy(aiInput = action.text, aiError = null)
            }

            TxnEditAction.EnterAiMode -> _uiState.update {
                it.copy(aiMode = true, aiInput = "", aiParsed = null, aiError = null)
            }

            TxnEditAction.ParseAi -> parseAi()

            is TxnEditAction.ApplyAiEntry -> applyParsed(action.parsed)

            TxnEditAction.ConfirmAiEntry -> {
                _uiState.value.aiParsed?.let { parsed ->
                    applyParsed(parsed)
                    save()
                }
            }

            TxnEditAction.ExitAiMode -> _uiState.update { state ->
                state.copy(aiMode = false, aiInput = "", aiParsing = false, aiError = null, aiParsed = null)
            }

            TxnEditAction.Save -> save()

            TxnEditAction.ConsumeError -> _uiState.update { it.copy(error = null, saveFailureMessage = null) }
        }
    }

    private fun changeTimestamp(transform: (Long) -> Long) {
        _uiState.update { state ->
            val value = transform(state.occurredAt)
            state.copy(occurredAt = value, hasUnsavedChanges = state.hasUnsavedChanges || value != state.occurredAt)
        }
    }

    private fun refreshToday() {
        val today = timeProvider.nowMillis().toLocalDate(timeProvider.zone)
        _uiState.update { it.copy(todayMillis = today.startOfDayEpochMillis(timeProvider.zone),
            yesterdayMillis = today.minus(1, DateTimeUnit.DAY).startOfDayEpochMillis(timeProvider.zone)) }
    }

    private fun createTag() {
        val name = _uiState.value.tagInput.trim()
        if (name.isEmpty()) return
        _uiState.update { it.copy(isCreatingTag = true, tagCreateFailed = false) }
        viewModelScope.launch {
            val existing = observeTags(ledgerId).first().firstOrNull { it.name == name }
            val result = existing?.let { AppResult.success(it) } ?: saveTag(null, ledgerId, name)
            val tag = (result as? AppResult.Success)?.value
                ?: observeTags(ledgerId).first().firstOrNull { it.name == name }
            _uiState.update { state ->
                if (tag == null) state.copy(isCreatingTag = false, tagCreateFailed = true)
                else state.copy(isCreatingTag = false, tagCreateFailed = false, tagInput = "",
                    selectedTagIds = state.selectedTagIds + tag.id, hasUnsavedChanges = true,
                    tags = if (state.tags.any { it.id == tag.id }) state.tags else state.tags + tag)
            }
        }
    }

    private fun parseAi() {
        val text = _uiState.value.aiInput.trim()
        if (text.isBlank() || _uiState.value.aiParsing) return
        _uiState.update { it.copy(aiParsing = true, aiError = null) }
        viewModelScope.launch {
            val accountList = observeAccounts(ledgerId, includeArchived = true).first()
            val categoryNodes = observeCategoryTree(ledgerId).first()
            val pipeline = NlEntryPipeline(
                accounts = accountList,
                categories = categoryNodes.flatMap { listOf(it.category) + it.children },
                classifier = classifier,
                cloud = cloudClientFor(aiSettingsRepository.settings(), llmClient),
            )
            val parsedEntries = runCatching {
                pipeline.parse(text, timeProvider.nowMillis().toLocalDate(timeProvider.zone)).entries
            }.getOrNull()
            val parsed = parsedEntries?.singleOrNull()
            _uiState.update { state ->
                state.copy(
                    aiParsing = false,
                    aiError = when {
                        parsed != null -> null
                        parsedEntries.isNullOrEmpty() -> TxnEditError.AI_PARSE_FAILED
                        else -> TxnEditError.AI_MULTIPLE_ENTRIES
                    },
                    aiParsed = parsed,
                )
            }
        }
    }

    private fun applyParsed(parsed: ParsedEntry) {
        _uiState.update { state ->
            val next = state.copy(
                type = parsed.type,
                amountInput = parsed.amount.toPlainString().removePrefix("-"),
                title = parsed.title,
                remark = parsed.remark,
                accountId = parsed.accountId ?: state.accountId,
                toAccountId = parsed.toAccountId ?: state.toAccountId,
                categoryId = parsed.categoryId ?: state.categoryId,
                occurredAt = parsedEntryTime(state.occurredAt, parsed.date, parsed.time, timeProvider.zone),
                hasUnsavedChanges = true,
                aiMode = false,
                aiInput = "",
                aiError = null,
            )
            val account = next.resolvedAccountId(null)
            val validated = next.copy(accountId = account,
                categoryId = next.categoryId.takeIf { next.hasCategoryFor(parsed.type) })
            validated.copy(toAccountId = validated.resolvedTargetAccountId(), expandedCategoryId = validated.categoryParentId)
        }
    }

    private fun save() {
        val state = _uiState.value
        if (state.isBusy || state.editingUnavailable) return
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

        _uiState.update { it.copy(isSaving = true) }
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
            } ?: if (state.aiParsed != null) {
                saveAiEntry(state, amount, accountId, state.aiParsed)
            } else createTxn(
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
                runCatching { lastAccountStore.setLastAccountId(ledgerId, accountId) }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saved = true,
                        error = null,
                        saveFailureMessage = null,
                        aiPendingTxnId = null,
                    )
                }
            } else {
                // AppError.message 是领域层给用户看的说明（如「转账需要选择转入账户」），
                // 直接带出来比笼统的「保存失败」有用（见 core:common AppError 注释）。
                val reason = (result as? AppResult.Failure)?.error?.message
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = TxnEditError.SAVE_FAILED,
                        saveFailureMessage = reason,
                    )
                }
            }
        }
    }

    private suspend fun saveAiEntry(
        state: TxnEditUiState,
        amount: Money,
        accountId: String,
        parsed: ParsedEntry,
    ): AppResult<Txn> {
        val draft = TxnDraft(
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
            source = if (parsed.matchedRuleId != null) TxnSource.RULE else TxnSource.AI,
            draftStatus = DraftStatus.PENDING,
            excludeFromStats = state.excludeFromStats,
        )
        val now = timeProvider.nowMillis()
        val proposed = Txn(
            id = "",
            ledgerId = draft.ledgerId,
            type = state.type,
            amount = amount,
            accountId = accountId,
            toAccountId = draft.toAccountId,
            categoryId = state.categoryId,
            occurredAt = requireNotNull(draft.occurredAt),
            title = state.title.trim(),
            remark = state.remark.trim(),
            source = draft.source,
            draftStatus = DraftStatus.PENDING,
            tagIds = state.selectedTagIds.toList(),
            excludeFromStats = state.excludeFromStats,
            createdAt = now,
            updatedAt = now,
        )
        TxnValidator.validate(
            proposed,
            state.accounts,
            state.categoryNodes.flatMap { listOf(it.category) + it.children },
        )?.let { return AppResult.failure(it) }
        val pending = state.aiPendingTxnId?.let { getTxn(it) } ?: run {
            val created = createTxn(draft)
            val txn = (created as? AppResult.Success)?.value ?: return created
            _uiState.update { it.copy(aiPendingTxnId = txn.id) }
            txn
        }
        return confirmAiEntry(pending, parsed)
    }

}
