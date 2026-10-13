package com.han1997.vipledger.feature.ledger.ui.txnedit

import com.han1997.vipledger.core.domain.calculator.TxnValidator
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class TxnEditUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val ledgerId: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val title: String = "",
    val remark: String = "",
    val occurredAt: Long = 0L,
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val expandedCategoryId: String? = null,
    val selectedTagIds: Set<String> = emptySet(),
    val excludeFromStats: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val categoryNodes: List<CategoryNode> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val todayMillis: Long = 0L,
    val yesterdayMillis: Long = 0L,
    val amountError: Boolean = false,
    val error: TxnEditError? = null,
    val saveFailureMessage: String? = null,
    val saved: Boolean = false,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val editingUnavailable: Boolean = false,
    val tagEditorExpanded: Boolean = false,
    val tagInput: String = "",
    val isCreatingTag: Boolean = false,
    val tagCreateFailed: Boolean = false,
    val aiPendingTxnId: String? = null,
    val aiMode: Boolean = false,
    val aiInput: String = "",
    val aiParsing: Boolean = false,
    val aiError: TxnEditError? = null,
    val aiParsed: ParsedEntry? = null,
    /** 编辑态才允许删除（顶栏删除入口，原型 v2.1）。 */
    val canDelete: Boolean = false,
    /** 删除完成，界面收到后返回。 */
    val deleted: Boolean = false,
) {
    val isAiMode: Boolean get() = aiMode
    val isBusy: Boolean get() = isSaving || isCreatingTag
    val visibleCategories: List<CategoryNode> get() = categoryNodes.filter { it.category.kind == categoryKind }
    val categoryParentId: String? get() = visibleCategories.firstOrNull {
        it.category.id == categoryId || it.children.any { child -> child.id == categoryId }
    }?.category?.id
    val selectedCategoryNames: List<String> get() = visibleCategories.firstOrNull { it.category.id == categoryParentId }?.let { node ->
        listOfNotNull(node.name, node.children.firstOrNull { it.id == categoryId }?.name)
    }.orEmpty()
    val matchingTags: List<Tag> get() = tags.filter { it.name.contains(tagInput.trim(), ignoreCase = true) }
    val existingTag: Tag? get() = tags.firstOrNull { it.name == tagInput.trim() }

    fun toggleCategoryParent(id: String): TxnEditUiState {
        if (visibleCategories.none { it.category.id == id }) return this
        return if (categoryParentId == id) copy(expandedCategoryId = id.takeUnless { expandedCategoryId == id })
        else copy(categoryId = id, expandedCategoryId = id, hasUnsavedChanges = true)
    }

    /**
     * 分类恒有值（任务决策 D2，原型 v2.1 口径）：未选分类时默认选当前方向的
     * 第一个一级分类并展开其二级面板。自动默认不破动 [hasUnsavedChanges]。
     */
    fun withDefaultCategory(): TxnEditUiState {
        if (categoryId != null) return this
        val first = visibleCategories.firstOrNull() ?: return this
        return copy(categoryId = first.category.id, expandedCategoryId = first.category.id)
    }

    /** 当前类型对应的分类方向（支出 / 收入 / 转账各自成树）。 */
    val categoryKind: CategoryKind get() = TxnValidator.expectedKind(type)

    val selectedAccount: Account? get() = accounts.firstOrNull { it.id == accountId }

    /** New entries keep their valid current choice, otherwise reuse the last ledger-specific account. */
    fun resolvedAccountId(lastAccountId: String?): String? {
        val availableIds = accounts.mapTo(mutableSetOf()) { it.id }
        return accountId?.takeIf { it in availableIds }
            ?: lastAccountId?.takeIf { it in availableIds }
            ?: accounts.firstOrNull()?.id
    }

    /**
     * 目标账户候选：不能是转出账户本身；还款只能挑负债账户。
     * 界面渲染候选、默认选中第一个，都用这里，避免两处过滤条件走偏。
     */
    fun targetCandidatesFor(target: TransactionType): List<Account> = accounts.filter { account ->
        account.id != accountId && (target != TransactionType.DEBT_REPAY || account.type.isDebt)
    }

    val targetCandidates: List<Account> get() = targetCandidatesFor(type)

    /**
     * 目标账户：只有「需要目标账户」的类型才有值；保留仍然合法的选择，
     * 否则默认第一个候选（转账切过来就能直接保存，见 TxnEditUiStateTest）。
     */
    fun resolvedTargetAccountId(): String? {
        if (!type.requiresTargetAccount) return null
        val candidates = targetCandidates
        return toAccountId?.takeIf { id -> candidates.any { it.id == id } } ?: candidates.firstOrNull()?.id
    }

    /** 当前选中的分类是否属于给定类型的方向（切换类型时用来决定要不要清空分类）。 */
    fun hasCategoryFor(target: TransactionType): Boolean {
        val id = categoryId ?: return false
        val kind = TxnValidator.expectedKind(target)
        return categoryNodes.any { node ->
            node.category.kind == kind && (node.category.id == id || node.children.any { it.id == id })
        }
    }
}

/** 一次性提示，界面映射成 strings.xml 文案。 */
enum class TxnEditError { INVALID_AMOUNT, SAVE_FAILED, AI_PARSE_FAILED, AI_MULTIPLE_ENTRIES }

sealed interface TxnEditAction {
    data class SelectType(val type: TransactionType) : TxnEditAction

    data class UpdateAmount(val text: String) : TxnEditAction

    data class UpdateTitle(val text: String) : TxnEditAction

    data class UpdateRemark(val text: String) : TxnEditAction

    data class SelectAccount(val accountId: String) : TxnEditAction

    data class SelectTargetAccount(val accountId: String) : TxnEditAction

    data class SelectCategory(val categoryId: String?) : TxnEditAction
    data class ToggleCategoryParent(val categoryId: String) : TxnEditAction

    data class ToggleTag(val tagId: String) : TxnEditAction
    data object ToggleTagEditor : TxnEditAction
    data class UpdateTagInput(val text: String) : TxnEditAction
    data object CreateTag : TxnEditAction

    data class UpdateExcludeFromStats(val exclude: Boolean) : TxnEditAction

    data class SelectDate(val date: LocalDate) : TxnEditAction
    data class SelectTime(val time: LocalTime) : TxnEditAction
    data class SelectRelativeDate(val daysAgo: Int) : TxnEditAction
    data object SelectNow : TxnEditAction
    data object RefreshToday : TxnEditAction

    data class UpdateAiInput(val text: String) : TxnEditAction

    data object EnterAiMode : TxnEditAction

    data object ParseAi : TxnEditAction

    data class ApplyAiEntry(val parsed: ParsedEntry) : TxnEditAction

    data object ConfirmAiEntry : TxnEditAction

    data object ExitAiMode : TxnEditAction

    data object Save : TxnEditAction

    /** 编辑态删除当前账目（危险操作，界面必须先二次确认）。 */
    data object Delete : TxnEditAction

    data object ConsumeError : TxnEditAction
}
