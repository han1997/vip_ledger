package com.han1997.vipledger.feature.stats.ui.common

import com.han1997.vipledger.core.domain.calculator.SearchQueryMapper
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class TxnFilterStateTest {

    private val today = LocalDate(2026, 9, 24)

    @Test
    fun emptyStateHasNoConditionsAndKeepsConfirmedOnly() {
        val filter = TxnFilterState()

        assertFalse(filter.hasConditions)
        assertEquals(DraftStatus.CONFIRMED, filter.toQuery(LEDGER).draftStatus)
    }

    @Test
    fun blankKeywordIsNotACondition() {
        assertFalse(TxnFilterState(keyword = "   ").hasConditions)
        assertNull(keywordFilterOf("   "), "只有空白的搜索词不该生成 LIKE 条件")
        assertTrue(TxnFilterState(keyword = " 午餐 ").hasConditions)
        assertEquals("午餐", keywordFilterOf(" 午餐 "), "前后空格不进查询")
    }

    @Test
    fun includeDraftsRemovesTheDraftStatusFilter() {
        assertNull(TxnFilterState(includeDrafts = true).toQuery(LEDGER).draftStatus)
    }

    @Test
    fun amountTextIsParsedOnlyWhenValidAndPositive() {
        val filter = TxnFilterState(minAmountText = "12.345", maxAmountText = "abc")

        assertEquals(Money.ofCents(1235), filter.minAmount, "超过两位小数按 HALF_UP 舍入")
        assertNull(filter.maxAmount, "解析不了的输入当作没填")
        assertTrue(filter.isAmountRangeValid)
    }

    @Test
    fun amountRangeIsDroppedWhenMinExceedsMax() {
        val filter = TxnFilterState(minAmountText = "100", maxAmountText = "50")

        assertFalse(filter.isAmountRangeValid)
        assertNull(filter.toQuery(LEDGER).minAmount, "非法区间不参与查询，避免永远空结果")
        assertNull(filter.toQuery(LEDGER).maxAmount)
    }

    @Test
    fun presetAndCustomRangeAreMutuallyExclusive() {
        val month = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))

        val withPreset = TxnFilterState(preset = DateRangePreset.THIS_MONTH).selectCustomRange(month)
        assertNull(withPreset.preset)
        assertEquals(month, withPreset.customRange)

        val withCustom = withPreset.selectPreset(DateRangePreset.LAST_MONTH)
        assertNull(withCustom.customRange)
        assertEquals(DateRangePreset.LAST_MONTH, withCustom.preset)
    }

    @Test
    fun resolvedRangePrefersCustomRangeThenPreset() {
        val month = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        val state = TxnFilterState(customRange = month)

        assertEquals(month, state.resolvedRange(today))
        assertEquals(
            DateRangePreset.LAST_7_DAYS.resolve(today),
            TxnFilterState(preset = DateRangePreset.LAST_7_DAYS).resolvedRange(today),
        )
        assertNull(TxnFilterState().resolvedRange(today))
    }

    @Test
    fun togglingMultiSelectDimensionsAddsAndRemoves() {
        val filter = TxnFilterState()
            .toggleCategory("food")
            .toggleCategory("food")
            .toggleTag("tag1")
            .toggleAccount("a1")
            .toggleType(TransactionType.EXPENSE)

        val query = filter.toQuery(LEDGER)
        assertTrue(query.categoryIds.isEmpty())
        assertEquals(setOf("tag1"), query.tagIds)
        assertEquals(setOf("a1"), query.accountIds)
        assertEquals(setOf(TransactionType.EXPENSE), query.types)
    }

    @Test
    fun defaultStatisticsFilterIsThisMonth() {
        assertEquals(DateRangePreset.THIS_MONTH, TxnFilterState.THIS_MONTH.preset)
        assertEquals(DateRangePreset.THIS_MONTH.resolve(today), TxnFilterState.THIS_MONTH.resolvedRange(today))
    }

    /** 走完整链路（界面状态 → SearchQuery → 仓库过滤条件）验证搜索词。 */
    private fun keywordFilterOf(keyword: String): String? = SearchQueryMapper
        .toFilter(TxnFilterState(keyword = keyword).toQuery(LEDGER), today)
        .keyword

    private companion object {
        const val LEDGER = "l1"
    }
}