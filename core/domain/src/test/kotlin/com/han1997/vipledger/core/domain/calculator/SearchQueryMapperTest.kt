package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.SearchQuery
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchQueryMapperTest {

    private val today = LocalDate(2026, 9, 24)

    @Test
    fun blankKeywordBecomesNoCondition() {
        val filter = SearchQueryMapper.toFilter(SearchQuery(ledgerId = LEDGER, keyword = "   "), today)
        assertNull(filter.keyword)
    }

    @Test
    fun keywordIsTrimmedAndKept() {
        val filter = SearchQueryMapper.toFilter(SearchQuery(ledgerId = LEDGER, keyword = " 午餐 "), today)
        assertEquals("午餐", filter.keyword)
    }

    @Test
    fun presetWinsOverCustomRange() {
        val query = SearchQuery(
            ledgerId = LEDGER,
            preset = DateRangePreset.THIS_MONTH,
            customRange = DateRange(LocalDate(2020, 1, 1), LocalDate(2020, 1, 31)),
        )
        val filter = SearchQueryMapper.toFilter(query, today)
        assertEquals(DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)), filter.range)
    }

    @Test
    fun customRangeIsUsedWhenNoPreset() {
        val range = DateRange(LocalDate(2026, 1, 1), LocalDate(2026, 3, 31))
        val filter = SearchQueryMapper.toFilter(SearchQuery(ledgerId = LEDGER, customRange = range), today)
        assertEquals(range, filter.range)
    }

    @Test
    fun noTimeConditionMeansAllTime() {
        assertNull(SearchQueryMapper.toFilter(SearchQuery(ledgerId = LEDGER), today).range)
    }

    @Test
    fun emptyTypeSelectionMeansAllTypes() {
        val filter = SearchQueryMapper.toFilter(SearchQuery(ledgerId = LEDGER), today)
        assertEquals(TransactionType.entries.toSet(), filter.types)
    }

    @Test
    fun explicitTypeSelectionIsKept() {
        val types = setOf(TransactionType.EXPENSE, TransactionType.TRANSFER)
        val filter = SearchQueryMapper.toFilter(SearchQuery(ledgerId = LEDGER, types = types), today)
        assertEquals(types, filter.types)
    }

    @Test
    fun expandedCategoriesReplaceRawSelection() {
        val query = SearchQuery(ledgerId = LEDGER, categoryIds = setOf("food"))
        val filter = SearchQueryMapper.toFilter(query, today, expandedCategoryIds = setOf("food", "lunch"))
        assertEquals(setOf("food", "lunch"), filter.categoryIds)
    }

    @Test
    fun amountsAndDraftStatusPassThrough() {
        val query = SearchQuery(
            ledgerId = LEDGER,
            minAmount = Money.ofCents(1000),
            maxAmount = Money.ofCents(5000),
            draftStatus = DraftStatus.PENDING,
        )
        val filter = SearchQueryMapper.toFilter(query, today)
        assertEquals(Money.ofCents(1000), filter.minAmount)
        assertEquals(Money.ofCents(5000), filter.maxAmount)
        assertEquals(DraftStatus.PENDING, filter.draftStatus)
    }

    @Test
    fun hasConditionsIgnoresLedgerOnlyQuery() {
        assertEquals(false, SearchQuery(ledgerId = LEDGER).hasConditions)
        assertEquals(true, SearchQuery(ledgerId = LEDGER, keyword = "午餐").hasConditions)
        assertEquals(true, SearchQuery(ledgerId = LEDGER, types = setOf(TransactionType.INCOME)).hasConditions)
    }

    private companion object {
        const val LEDGER = "l1"
    }
}