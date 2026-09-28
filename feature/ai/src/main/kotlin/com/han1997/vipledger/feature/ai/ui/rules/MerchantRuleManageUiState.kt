package com.han1997.vipledger.feature.ai.ui.rules

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.domain.usecase.rule.DEFAULT_USER_RULE_PRIORITY
import com.han1997.vipledger.core.domain.usecase.rule.MerchantRuleInput
import com.han1997.vipledger.core.domain.usecase.rule.validateMerchantRuleSyntax
import com.han1997.vipledger.core.model.RuleMatchType
import com.han1997.vipledger.core.model.RuleSource
import com.han1997.vipledger.core.model.RuleTargetField

/** 规则来源的展示排序：用户手写在前，学习规则最后（便于集中审查与清理）。 */
private val SOURCE_ORDER: Map<RuleSource, Int> = mapOf(
    RuleSource.USER to 0,
    RuleSource.BUILTIN to 1,
    RuleSource.LEARNED to 2,
)

/** 规则列表行：规则 + 分类名（id→名字查表在 ViewModel 组装，界面不再查表）。 */
data class MerchantRuleRow(
    val rule: MerchantRule,
    val categoryName: String,
) {
    val source: RuleSource get() = rule.source
    val hitCount: Int get() = rule.hitCount
}

/**
 * 规则列表的排序：来源分组（USER → BUILTIN → LEARNED）→ 优先级升序（越小越先命中）→ 关键字。
 *
 * 先按来源是为了让学习出来的规则在列表里聚成一块、便于审查与清理（PRD Requirement 9）；
 * 同一来源内按 `priority` 升序，命中最优先的排前面；最后按 `pattern` 保证顺序稳定可测。
 */
fun ruleRowsOf(rules: List<MerchantRule>, categoryNames: Map<String, String>): List<MerchantRuleRow> =
    rules
        .map { MerchantRuleRow(rule = it, categoryName = categoryNames[it.categoryId].orEmpty()) }
        .sortedWith(
            compareBy(
                { SOURCE_ORDER[it.source] ?: Int.MAX_VALUE },
                { it.rule.priority },
                { it.rule.pattern },
            ),
        )

/** 编辑框的目标分类候选（只存 id 与名字，界面不查表）。 */
data class RuleCategory(val id: String, val name: String)

/**
 * 规则新建 / 编辑表单。
 *
 * [originalSource] 是被编辑规则的当前来源（新建为 [RuleSource.USER]），用于判断能否「提升为
 * 用户规则」；[promoteToUser] 是保存时是否执行提升（PRD Requirement 9：学习规则可被审查、清理
 * 与提升）。[error] 与用例共用同一份语法判据，非法正则在这里就被拦下（不存下一条永不命中的规则）。
 */
data class MerchantRuleEditor(
    val ruleId: String? = null,
    val pattern: String = "",
    val matchType: RuleMatchType = RuleMatchType.CONTAINS,
    val targetField: RuleTargetField = RuleTargetField.ANY,
    val categoryId: String? = null,
    val priority: Int = DEFAULT_USER_RULE_PRIORITY,
    val enabled: Boolean = true,
    val originalSource: RuleSource = RuleSource.USER,
    val promoteToUser: Boolean = false,
) {
    val isEditing: Boolean get() = ruleId != null

    /** 已有且非用户手写的规则才能提升为用户规则。 */
    val canPromote: Boolean get() = isEditing && originalSource != RuleSource.USER

    /** 保存前的即时校验；null 表示可保存。 */
    fun error(): AppError? = validateMerchantRuleSyntax(
        pattern = pattern,
        matchType = matchType,
        categoryId = categoryId,
        priority = priority,
    )

    fun toInput(ledgerId: String): MerchantRuleInput = MerchantRuleInput(
        ledgerId = ledgerId,
        ruleId = ruleId,
        pattern = pattern,
        matchType = matchType,
        targetField = targetField,
        categoryId = categoryId.orEmpty(),
        priority = priority,
        enabled = enabled,
        promoteToUser = promoteToUser,
    )
}

/** 新建规则的初始表单。 */
fun newRuleEditor(): MerchantRuleEditor = MerchantRuleEditor()

/** 由已有规则进入编辑；默认保持原来源，提升需要用户显式切换。 */
fun editorOf(rule: MerchantRule): MerchantRuleEditor = MerchantRuleEditor(
    ruleId = rule.id,
    pattern = rule.pattern,
    matchType = rule.matchType,
    targetField = rule.targetField,
    categoryId = rule.categoryId,
    priority = rule.priority,
    enabled = rule.enabled,
    originalSource = rule.source,
    promoteToUser = false,
)

/** 商户规则管理页状态；只放数据与枚举，不放中文文案（见 .trellis/spec/android/ui.md）。 */
data class MerchantRuleManageUiState(
    val isLoading: Boolean = true,
    val ledgerId: String? = null,
    val rules: List<MerchantRuleRow> = emptyList(),
    val categories: List<RuleCategory> = emptyList(),
    val editor: MerchantRuleEditor? = null,
    val pendingDelete: MerchantRuleRow? = null,
    val notice: RuleNotice? = null,
    /** 失败原因（来自 AppError.message）；成功或没有说明时为 null。 */
    val noticeDetail: String? = null,
) {
    fun categoryNameOf(categoryId: String): String? =
        categories.firstOrNull { it.id == categoryId }?.name
}

/** 一次性提示：界面层查表成文案。 */
enum class RuleNotice { SAVED, DELETED, TOGGLED, FAILED }

sealed interface MerchantRuleAction {
    data object OpenCreate : MerchantRuleAction

    data class OpenEdit(val rule: MerchantRule) : MerchantRuleAction

    data class UpdatePattern(val text: String) : MerchantRuleAction

    data class UpdateMatchType(val matchType: RuleMatchType) : MerchantRuleAction

    data class UpdateTargetField(val targetField: RuleTargetField) : MerchantRuleAction

    data class UpdateCategory(val categoryId: String) : MerchantRuleAction

    data class UpdatePriority(val priority: Int) : MerchantRuleAction

    data class UpdateEnabled(val enabled: Boolean) : MerchantRuleAction

    data class UpdatePromoteToUser(val promote: Boolean) : MerchantRuleAction

    data object DismissEditor : MerchantRuleAction

    data object SaveEditor : MerchantRuleAction

    data class ToggleEnabled(val rule: MerchantRule) : MerchantRuleAction

    data class RequestDelete(val rule: MerchantRule) : MerchantRuleAction

    data object ConfirmDelete : MerchantRuleAction

    data object DismissDelete : MerchantRuleAction

    data object ConsumeNotice : MerchantRuleAction
}

/**
 * 界面本地态：编辑框、待删除规则与一次性提示（规则列表本身来自仓库 Flow，不在此）。
 *
 * 转移逻辑做成纯函数 [reduce]，ViewModel 只在其上叠加用例副作用，于是「打开编辑 / 请求删除 /
 * 关闭弹窗」这些状态跳转都能脱离 Android 与用例单测（见 .trellis/spec/android/testing.md）。
 */
data class MerchantRuleLocalState(
    val editor: MerchantRuleEditor? = null,
    val pendingDelete: MerchantRule? = null,
    val notice: RuleNotice? = null,
    val noticeDetail: String? = null,
) {
    /**
     * 只处理不产生副作用的动作；带副作用的动作（[MerchantRuleAction.SaveEditor] /
     * [MerchantRuleAction.ToggleEnabled] / [MerchantRuleAction.ConfirmDelete]）返回自身，由 ViewModel
     * 先调用用例再写回。
     */
    fun reduce(action: MerchantRuleAction): MerchantRuleLocalState = when (action) {
        MerchantRuleAction.OpenCreate -> copy(editor = newRuleEditor())
        is MerchantRuleAction.OpenEdit -> copy(editor = editorOf(action.rule))
        is MerchantRuleAction.UpdatePattern -> editEditor { it.copy(pattern = action.text) }
        is MerchantRuleAction.UpdateMatchType -> editEditor { it.copy(matchType = action.matchType) }
        is MerchantRuleAction.UpdateTargetField -> editEditor { it.copy(targetField = action.targetField) }
        is MerchantRuleAction.UpdateCategory -> editEditor { it.copy(categoryId = action.categoryId) }
        is MerchantRuleAction.UpdatePriority -> editEditor { it.copy(priority = action.priority) }
        is MerchantRuleAction.UpdateEnabled -> editEditor { it.copy(enabled = action.enabled) }
        is MerchantRuleAction.UpdatePromoteToUser -> editEditor { it.copy(promoteToUser = action.promote) }
        MerchantRuleAction.DismissEditor -> copy(editor = null)
        is MerchantRuleAction.RequestDelete -> copy(pendingDelete = action.rule)
        MerchantRuleAction.DismissDelete -> copy(pendingDelete = null)
        MerchantRuleAction.ConsumeNotice -> copy(notice = null, noticeDetail = null)
        MerchantRuleAction.SaveEditor,
        MerchantRuleAction.ConfirmDelete,
        is MerchantRuleAction.ToggleEnabled,
        -> this
    }

    fun withNotice(notice: RuleNotice?, detail: String? = null): MerchantRuleLocalState =
        copy(notice = notice, noticeDetail = detail)

    private fun editEditor(transform: (MerchantRuleEditor) -> MerchantRuleEditor): MerchantRuleLocalState =
        copy(editor = editor?.let(transform))
}
