package com.han1997.vipledger.feature.ai.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.designsystem.format.categoryNameMap
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.rule.DeleteMerchantRuleUseCase
import com.han1997.vipledger.core.domain.usecase.rule.ObserveMerchantRulesUseCase
import com.han1997.vipledger.core.domain.usecase.rule.SaveMerchantRuleUseCase
import com.han1997.vipledger.core.domain.usecase.rule.SetMerchantRuleEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 商户规则管理页状态持有者（PRD Requirement 9）。
 *
 * 只依赖 `:core:domain` 的用例，不依赖 `:core:data` 或任何其它 `:feature:*`（architecture.md 硬规则）。
 * 账本取「当前账本」，与 AI 记账页同源（PRD D2：管理页收敛在 `:feature:ai` 内）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MerchantRuleManageViewModel @Inject constructor(
    observeCurrentLedger: ObserveCurrentLedgerUseCase,
    private val observeMerchantRules: ObserveMerchantRulesUseCase,
    observeCategoryTree: ObserveCategoryTreeUseCase,
    private val saveMerchantRule: SaveMerchantRuleUseCase,
    private val setMerchantRuleEnabled: SetMerchantRuleEnabledUseCase,
    private val deleteMerchantRule: DeleteMerchantRuleUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(MerchantRuleLocalState())

    val uiState: StateFlow<MerchantRuleManageUiState> = observeCurrentLedger()
        .distinctUntilChangedBy { ledger -> ledger?.id }
        .flatMapLatest { ledger ->
            val ledgerId = ledger?.id
            combine(
                if (ledgerId == null) flowOf(emptyList()) else observeMerchantRules(ledgerId),
                if (ledgerId == null) flowOf(emptyList()) else observeCategoryTree(ledgerId, includeArchived = true),
                local,
            ) { rules, nodes, local ->
                val names = nodes.categoryNameMap()
                MerchantRuleManageUiState(
                    isLoading = false,
                    ledgerId = ledgerId,
                    rules = ruleRowsOf(rules, names),
                    categories = flatten(nodes).map { RuleCategory(it.id, it.name) },
                    editor = local.editor,
                    pendingDelete = local.pendingDelete?.let { rule ->
                        MerchantRuleRow(rule = rule, categoryName = names[rule.categoryId].orEmpty())
                    },
                    notice = local.notice,
                    noticeDetail = local.noticeDetail,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MerchantRuleManageUiState())

    fun onAction(action: MerchantRuleAction) {
        when (action) {
            // 带副作用的动作：先调用用例，再写回本地态。
            MerchantRuleAction.SaveEditor -> saveEditor()
            is MerchantRuleAction.ToggleEnabled -> toggleEnabled(action.rule)
            MerchantRuleAction.ConfirmDelete -> confirmDelete()
            // 其余为纯状态转移，收敛在 MerchantRuleLocalState.reduce 里（可单测）。
            else -> local.update { it.reduce(action) }
        }
    }

    private fun saveEditor() {
        val editor = local.value.editor ?: return
        val ledgerId = uiState.value.ledgerId
        if (ledgerId == null) {
            fail(AppError.Unknown("账本未就绪"))
            return
        }
        // 界面已用 editor.error() 禁用保存按钮并即时提示；这里再兜一层，保证非法规则永不入库。
        editor.error()?.let { error ->
            fail(error)
            return
        }
        viewModelScope.launch {
            val result = saveMerchantRule(editor.toInput(ledgerId))
            local.update {
                if (result.isSuccess) {
                    it.copy(editor = null).withNotice(RuleNotice.SAVED)
                } else {
                    it.copy(editor = it.editor)
                        .withNotice(RuleNotice.FAILED, (result as? AppResult.Failure)?.error?.message)
                }
            }
        }
    }

    private fun toggleEnabled(rule: MerchantRule) {
        val ledgerId = uiState.value.ledgerId ?: return
        viewModelScope.launch {
            val result = setMerchantRuleEnabled(ledgerId, rule.id, !rule.enabled)
            local.update {
                it.withNotice(
                    if (result.isSuccess) RuleNotice.TOGGLED else RuleNotice.FAILED,
                    (result as? AppResult.Failure)?.error?.message,
                )
            }
        }
    }

    private fun confirmDelete() {
        val rule = local.value.pendingDelete ?: return
        viewModelScope.launch {
            val result = deleteMerchantRule(rule.id)
            local.update {
                it.copy(pendingDelete = null).withNotice(
                    if (result.isSuccess) RuleNotice.DELETED else RuleNotice.FAILED,
                    (result as? AppResult.Failure)?.error?.message,
                )
            }
        }
    }

    private fun fail(error: AppError) {
        local.update { it.withNotice(RuleNotice.FAILED, error.message) }
    }

    private fun flatten(nodes: List<CategoryNode>) =
        nodes.flatMap { node -> listOf(node.category) + node.children }
}
