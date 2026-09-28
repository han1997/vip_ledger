package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeMerchantRuleRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.domain.usecase.rule.DeleteMerchantRuleUseCase
import com.han1997.vipledger.core.domain.usecase.rule.MerchantRuleInput
import com.han1997.vipledger.core.domain.usecase.rule.SaveMerchantRuleUseCase
import com.han1997.vipledger.core.domain.usecase.rule.SetMerchantRuleEnabledUseCase
import com.han1997.vipledger.core.domain.usecase.rule.validateMerchantRuleSyntax
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.RuleMatchType
import com.han1997.vipledger.core.model.RuleSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * 商户规则管理用例（PRD Requirement 9）。
 *
 * 覆盖：非法正则保存前被拒、提升为用户规则、编辑保留来源与命中次数、启停与删除、分类归属校验。
 */
class MerchantRuleUseCasesTest {

    private fun categories() = FakeCategoryRepository(
        mutableListOf(
            category("c_food", "餐饮"),
            category("c_traffic", "交通"),
        ),
    )

    private fun saveUseCase(
        rules: FakeMerchantRuleRepository = FakeMerchantRuleRepository(),
        categories: FakeCategoryRepository = categories(),
    ) = SaveMerchantRuleUseCase(rules, categories, FakeTimeProvider(), SequentialIdGenerator())

    @Test
    fun invalidRegexIsRejectedAndNothingIsSaved() = runTest {
        val rules = FakeMerchantRuleRepository()
        val result = saveUseCase(rules).invoke(
            MerchantRuleInput(ledgerId = "l1", pattern = "([unclosed", matchType = RuleMatchType.REGEX, categoryId = "c_food"),
        )

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(result).error)
        assertTrue(rules.listByLedger("l1").isEmpty(), "非法正则必须在保存前被拒，不落库")
    }

    @Test
    fun validRegexIsAccepted() = runTest {
        val result = saveUseCase().invoke(
            MerchantRuleInput(ledgerId = "l1", pattern = "^午饭.*", matchType = RuleMatchType.REGEX, categoryId = "c_food"),
        )
        val rule = assertIs<AppResult.Success<MerchantRule>>(result).value
        assertEquals("^午饭.*", rule.pattern)
        assertEquals(RuleMatchType.REGEX, rule.matchType)
    }

    @Test
    fun syntaxValidationRejectsBlankMissingCategoryAndBadPriority() {
        assertIs<AppError.Validation>(
            validateMerchantRuleSyntax(" ", RuleMatchType.CONTAINS, "c_food", 100),
        )
        assertIs<AppError.Validation>(
            validateMerchantRuleSyntax("午饭", RuleMatchType.CONTAINS, null, 100),
        )
        assertIs<AppError.Validation>(
            validateMerchantRuleSyntax("午饭", RuleMatchType.CONTAINS, "c_food", 0),
        )
        assertNull(validateMerchantRuleSyntax("午饭", RuleMatchType.CONTAINS, "c_food", 100))
    }

    @Test
    fun newRuleIsUserSourcedWithDefaultPriority() = runTest {
        val rule = assertIs<AppResult.Success<MerchantRule>>(
            saveUseCase().invoke(MerchantRuleInput(ledgerId = "l1", pattern = "星巴克", categoryId = "c_food")),
        ).value

        assertEquals(RuleSource.USER, rule.source)
        assertEquals(100, rule.priority)
        assertTrue(rule.enabled)
        assertEquals(0, rule.hitCount)
    }

    @Test
    fun editingKeepsSourceHitCountAndCreatedAt() = runTest {
        val rules = FakeMerchantRuleRepository(
            mutableListOf(learnedRule(id = "r1", pattern = "午饭", categoryId = "c_food", hitCount = 5, createdAt = 111L)),
        )

        val rule = assertIs<AppResult.Success<MerchantRule>>(
            saveUseCase(rules).invoke(
                MerchantRuleInput(ledgerId = "l1", ruleId = "r1", pattern = "午饭外卖", categoryId = "c_traffic"),
            ),
        ).value

        assertEquals("r1", rule.id)
        assertEquals(RuleSource.LEARNED, rule.source, "未勾选提升时保留原来源")
        assertEquals(5, rule.hitCount, "编辑不得重置命中次数")
        assertEquals(111L, rule.createdAt)
        assertEquals("午饭外卖", rule.pattern)
        assertEquals("c_traffic", rule.categoryId)
    }

    @Test
    fun promotingLearnedRuleTurnsItIntoUserRule() = runTest {
        val rules = FakeMerchantRuleRepository(
            mutableListOf(learnedRule(id = "r1", pattern = "午饭", categoryId = "c_food", hitCount = 5, createdAt = 111L)),
        )

        val rule = assertIs<AppResult.Success<MerchantRule>>(
            saveUseCase(rules).invoke(
                MerchantRuleInput(ledgerId = "l1", ruleId = "r1", pattern = "午饭", categoryId = "c_food", promoteToUser = true),
            ),
        ).value

        assertEquals(RuleSource.USER, rule.source)
        assertEquals(5, rule.hitCount)
    }

    @Test
    fun categoryMustBelongToLedger() = runTest {
        val result = saveUseCase().invoke(
            MerchantRuleInput(ledgerId = "l1", pattern = "午饭", categoryId = "c_missing"),
        )
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(result).error)
    }

    @Test
    fun setEnabledTogglesWithoutLosingFields() = runTest {
        val rules = FakeMerchantRuleRepository(
            mutableListOf(learnedRule(id = "r1", pattern = "午饭", categoryId = "c_food", hitCount = 5, createdAt = 111L)),
        )
        val useCase = SetMerchantRuleEnabledUseCase(rules, FakeTimeProvider())

        assertTrue(useCase("l1", "r1", enabled = false).isSuccess)
        val updated = rules.listByLedger("l1").single()
        assertEquals(false, updated.enabled)
        assertEquals(5, updated.hitCount)
        assertEquals(111L, updated.createdAt)
    }

    @Test
    fun setEnabledRejectsUnknownRule() = runTest {
        val useCase = SetMerchantRuleEnabledUseCase(FakeMerchantRuleRepository(), FakeTimeProvider())
        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(useCase("l1", "nope", true)).error)
    }

    @Test
    fun deleteRemovesRule() = runTest {
        val rules = FakeMerchantRuleRepository(
            mutableListOf(learnedRule(id = "r1", pattern = "午饭", categoryId = "c_food")),
        )
        assertTrue(DeleteMerchantRuleUseCase(rules).invoke("r1").isSuccess)
        assertTrue(rules.listByLedger("l1").isEmpty())
    }

    private fun learnedRule(
        id: String,
        pattern: String,
        categoryId: String,
        hitCount: Int = 0,
        createdAt: Long = 0,
    ) = MerchantRule(
        id = id,
        ledgerId = "l1",
        pattern = pattern,
        categoryId = categoryId,
        priority = 110,
        source = RuleSource.LEARNED,
        hitCount = hitCount,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun category(id: String, name: String) = Category(
        id = id,
        ledgerId = "l1",
        parentId = null,
        name = name,
        kind = CategoryKind.EXPENSE,
        createdAt = 0,
        updatedAt = 0,
    )
}
