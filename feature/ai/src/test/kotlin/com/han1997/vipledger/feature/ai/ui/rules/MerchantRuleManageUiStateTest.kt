package com.han1997.vipledger.feature.ai.ui.rules

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.model.RuleMatchType
import com.han1997.vipledger.core.model.RuleSource
import com.han1997.vipledger.core.model.RuleTargetField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 商户规则管理页的纯状态规则（PRD Requirement 9）。
 *
 * 覆盖：来源分组排序与命中次数映射、非法正则保存前被拒、编辑 / 删除弹窗的状态转移、
 * 学习规则可辨识可删除且可提升为用户规则。
 */
class MerchantRuleManageUiStateTest {

    private val names = mapOf("c_food" to "餐饮", "c_traffic" to "交通")

    private fun rule(
        id: String,
        pattern: String,
        source: RuleSource = RuleSource.USER,
        priority: Int = 100,
        categoryId: String = "c_food",
        enabled: Boolean = true,
        hitCount: Int = 0,
        matchType: RuleMatchType = RuleMatchType.CONTAINS,
    ) = MerchantRule(
        id = id,
        ledgerId = "l1",
        pattern = pattern,
        matchType = matchType,
        targetField = RuleTargetField.ANY,
        categoryId = categoryId,
        priority = priority,
        source = source,
        enabled = enabled,
        hitCount = hitCount,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun rowsAreGroupedBySourceThenPriorityThenPattern() {
        val rows = ruleRowsOf(
            listOf(
                rule("l1", "学习B", source = RuleSource.LEARNED, priority = 110),
                rule("u2", "用户B", source = RuleSource.USER, priority = 200),
                rule("b1", "内置A", source = RuleSource.BUILTIN, priority = 100),
                rule("u1", "用户A", source = RuleSource.USER, priority = 100),
                rule("l2", "学习A", source = RuleSource.LEARNED, priority = 110),
            ),
            names,
        )

        assertEquals(
            listOf("u1", "u2", "b1", "l2", "l1"),
            rows.map { it.rule.id },
            "排序应为 来源(USER→BUILTIN→LEARNED) → 优先级升序 → 关键字",
        )
        assertEquals(
            listOf(RuleSource.USER, RuleSource.USER, RuleSource.BUILTIN, RuleSource.LEARNED, RuleSource.LEARNED),
            rows.map { it.source },
        )
    }

    @Test
    fun rowExposesSourceHitCountAndCategoryName() {
        val row = ruleRowsOf(
            listOf(rule("r1", "午饭", source = RuleSource.LEARNED, hitCount = 7, categoryId = "c_food")),
            names,
        ).single()

        assertEquals(RuleSource.LEARNED, row.source, "学习规则在行上标注为 LEARNED，界面据此显示「学习」徽标")
        assertEquals(7, row.hitCount)
        assertEquals("餐饮", row.categoryName)
    }

    @Test
    fun unknownCategoryYieldsBlankNameSoUiCanFallBack() {
        val row = ruleRowsOf(listOf(rule("r1", "午饭", categoryId = "c_missing")), names).single()
        assertEquals("", row.categoryName)
    }

    @Test
    fun invalidRegexIsRejectedBeforeSaving() {
        val editor = MerchantRuleEditor(pattern = "([unclosed", matchType = RuleMatchType.REGEX, categoryId = "c_food")
        val error = editor.error()
        assertIs<AppError.Validation>(error)
        assertTrue(error.message.orEmpty().isNotBlank(), "非法正则必须有可见提示")

        assertNull(
            editor.copy(pattern = "^午饭.*").error(),
            "合法正则应通过即时校验",
        )
    }

    @Test
    fun blankPatternMissingCategoryAndBadPriorityAreRejected() {
        assertIs<AppError.Validation>(newRuleEditor().error())
        assertIs<AppError.Validation>(MerchantRuleEditor(pattern = "午饭").error())
        assertIs<AppError.Validation>(MerchantRuleEditor(pattern = "午饭", categoryId = "c_food", priority = 0).error())
    }

    @Test
    fun newRuleDefaultsToUserSourceAndDefaultPriority() {
        val editor = newRuleEditor()
        assertEquals(RuleSource.USER, editor.originalSource)
        assertEquals(100, editor.priority)
        assertTrue(editor.enabled)
        assertFalse(editor.canPromote, "新建规则没有可提升的来源")
        assertFalse(editor.isEditing)
    }

    @Test
    fun learnedRuleCanBePromotedButUserRuleCannot() {
        val learned = editorOf(rule("r1", "午饭", source = RuleSource.LEARNED, priority = 110))
        assertTrue(learned.canPromote)
        assertFalse(learned.promoteToUser, "进入编辑时默认保持原来源，提升需用户显式切换")

        val user = editorOf(rule("r2", "打车", source = RuleSource.USER))
        assertFalse(user.canPromote)

        val promoted = learned.copy(promoteToUser = true)
        assertTrue(promoted.toInput("l1").promoteToUser)
        assertEquals("r1", promoted.toInput("l1").ruleId)
    }

    @Test
    fun editorPreservesRuleFieldsWhenOpenedForEdit() {
        val editor = editorOf(
            rule("r1", "打车", source = RuleSource.USER, priority = 120, enabled = false, matchType = RuleMatchType.EQUALS),
        )
        assertEquals("打车", editor.pattern)
        assertEquals(RuleMatchType.EQUALS, editor.matchType)
        assertEquals(120, editor.priority)
        assertFalse(editor.enabled)
        assertEquals("r1", editor.ruleId)
    }

    @Test
    fun openCreateAndOpenEditDriveEditorState() {
        val created = MerchantRuleLocalState().reduce(MerchantRuleAction.OpenCreate)
        assertEquals(newRuleEditor(), created.editor)

        val opened = MerchantRuleLocalState().reduce(MerchantRuleAction.OpenEdit(rule("r1", "午饭")))
        assertEquals("r1", opened.editor?.ruleId)

        assertNull(opened.reduce(MerchantRuleAction.DismissEditor).editor)
    }

    @Test
    fun editorFieldUpdatesFlowThroughReduce() {
        val state = MerchantRuleLocalState()
            .reduce(MerchantRuleAction.OpenCreate)
            .reduce(MerchantRuleAction.UpdatePattern("午饭"))
            .reduce(MerchantRuleAction.UpdateMatchType(RuleMatchType.REGEX))
            .reduce(MerchantRuleAction.UpdateTargetField(RuleTargetField.TITLE))
            .reduce(MerchantRuleAction.UpdateCategory("c_food"))
            .reduce(MerchantRuleAction.UpdatePriority(150))
            .reduce(MerchantRuleAction.UpdateEnabled(false))

        val editor = state.editor!!
        assertEquals("午饭", editor.pattern)
        assertEquals(RuleMatchType.REGEX, editor.matchType)
        assertEquals(RuleTargetField.TITLE, editor.targetField)
        assertEquals("c_food", editor.categoryId)
        assertEquals(150, editor.priority)
        assertFalse(editor.enabled)
        assertNull(editor.error(), "填全字段后应可保存")
    }

    @Test
    fun requestDeleteThenDismissTogglesPendingDeleteOnly() {
        val learningRule = rule("r1", "午饭", source = RuleSource.LEARNED, hitCount = 3)

        val requested = MerchantRuleLocalState().reduce(MerchantRuleAction.RequestDelete(learningRule))
        assertEquals(learningRule, requested.pendingDelete, "学习规则可被请求删除")
        assertNull(requested.editor)

        assertNull(requested.reduce(MerchantRuleAction.DismissDelete).pendingDelete)
    }

    @Test
    fun toggleAndConfirmDeleteDoNotMutateLocalStateDirectly() {
        val starting = MerchantRuleLocalState(pendingDelete = rule("r1", "午饭"))
        // 启停 / 确认删除走用例副作用，reduce 本身不改本地态（删除后由 ViewModel 显式清空）。
        assertEquals(starting, starting.reduce(MerchantRuleAction.ToggleEnabled(rule("r2", "打车"))))
        assertEquals(starting, starting.reduce(MerchantRuleAction.ConfirmDelete))
        assertEquals(starting, starting.reduce(MerchantRuleAction.SaveEditor))
    }

    @Test
    fun consumeNoticeClearsNoticeAndDetail() {
        val state = MerchantRuleLocalState().withNotice(RuleNotice.FAILED, "正则表达式无效，请检查语法")
        val cleared = state.reduce(MerchantRuleAction.ConsumeNotice)
        assertNull(cleared.notice)
        assertNull(cleared.noticeDetail)
    }
}
