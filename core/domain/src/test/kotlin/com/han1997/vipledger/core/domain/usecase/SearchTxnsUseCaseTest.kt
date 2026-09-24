package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.SearchQuery
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.search.SearchTxnsUseCase
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class SearchTxnsUseCaseTest {

    private val timeProvider = FakeTimeProvider()

    private val categories = FakeCategoryRepository(
        mutableListOf(
            category("food", name = "餐饮"),
            category("lunch", name = "午餐", parentId = "food"),
            category("snack", name = "零食", parentId = "food"),
            category("transport", name = "交通"),
        ),
    )

    private val txns = FakeTxnRepository(
        mutableListOf(
            txn("t1", categoryId = "lunch", title = "午餐", amountCents = 3000, at = sep(24, 12, 30)),
            txn("t2", categoryId = "food", title = "买菜", remark = "超市", amountCents = 5000, at = sep(20, 9, 0)),
            txn("t3", categoryId = "transport", title = "打车", amountCents = 800, at = sep(10, 22, 10)),
            txn("t4", categoryId = "lunch", title = "午餐", amountCents = 3000, at = aug(31, 12, 0)),
        ),
    )

    private fun useCase() = SearchTxnsUseCase(txns, categories, timeProvider)

    @Test
    fun selectingTopLevelCategoryAlsoMatchesItsChildren() = runTest {
        val result = useCase()(SearchQuery(ledgerId = LEDGER, categoryIds = setOf("food"))).first()
        assertEquals(listOf("t1", "t2", "t4"), result.map { it.id }, "不限时间，8 月那笔也算命中")
    }

    @Test
    fun selectingLeafCategoryMatchesOnlyThatLeaf() = runTest {
        val result = useCase()(SearchQuery(ledgerId = LEDGER, categoryIds = setOf("snack"))).first()
        assertEquals(emptyList(), result.map { it.id })
    }

    @Test
    fun keywordMatchesTitleAndRemark() = runTest {
        assertEquals(
            listOf("t1", "t4"),
            useCase()(SearchQuery(ledgerId = LEDGER, keyword = "午餐")).first().map { it.id },
        )
        assertEquals(
            listOf("t2"),
            useCase()(SearchQuery(ledgerId = LEDGER, keyword = "超市")).first().map { it.id },
        )
    }

    @Test
    fun timeRangeAndKeywordCombine() = runTest {
        val query = SearchQuery(
            ledgerId = LEDGER,
            keyword = "午餐",
            customRange = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)),
        )
        assertEquals(listOf("t1"), useCase()(query).first().map { it.id })
    }

    @Test
    fun amountRangeFiltersByAbsoluteValue() = runTest {
        val query = SearchQuery(
            ledgerId = LEDGER,
            minAmount = Money.ofCents(2000),
            maxAmount = Money.ofCents(4000),
        )
        assertEquals(listOf("t1", "t4"), useCase()(query).first().map { it.id })
    }

    @Test
    fun typeSelectionIsApplied() = runTest {
        assertEquals(4, useCase()(SearchQuery(ledgerId = LEDGER, types = emptySet())).first().size)
        assertEquals(
            emptyList(),
            useCase()(SearchQuery(ledgerId = LEDGER, types = setOf(TransactionType.INCOME))).first().map { it.id },
        )
    }

    @Test
    fun draftFilterSeparatesPendingFromConfirmed() = runTest {
        txns.insert(
            txn("t9", categoryId = "food", title = "待确认外卖", amountCents = 3000, at = sep(24, 8, 0))
                .copy(draftStatus = DraftStatus.PENDING),
        )
        assertEquals(
            listOf("t9"),
            useCase()(SearchQuery(ledgerId = LEDGER, draftStatus = DraftStatus.PENDING)).first().map { it.id },
        )
        assertEquals(
            listOf("t1", "t2", "t3", "t4"),
            useCase()(SearchQuery(ledgerId = LEDGER, draftStatus = DraftStatus.CONFIRMED)).first().map { it.id },
        )
        assertEquals(5, useCase()(SearchQuery(ledgerId = LEDGER)).first().size)
    }

    private fun category(id: String, name: String, parentId: String? = null) = Category(
        id = id,
        ledgerId = LEDGER,
        parentId = parentId,
        name = name,
        kind = CategoryKind.EXPENSE,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun txn(
        id: String,
        categoryId: String,
        title: String,
        amountCents: Long,
        at: Long,
        remark: String = "",
    ) = Txn(
        id = id,
        ledgerId = LEDGER,
        type = TransactionType.EXPENSE,
        amount = Money.ofCents(amountCents),
        accountId = "a1",
        categoryId = categoryId,
        occurredAt = at,
        title = title,
        remark = remark,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun sep(day: Int, hour: Int, minute: Int): Long = epochMillis(2026, 9, day, hour, minute)

    private fun aug(day: Int, hour: Int, minute: Int): Long = epochMillis(2026, 8, day, hour, minute)

    private fun epochMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDate(year, month, day).startOfDayEpochMillis(ZONE) + (hour * 60 + minute) * 60_000L

    private companion object {
        const val LEDGER = "l1"
        val ZONE: TimeZone = TimeZone.of("Asia/Shanghai")
    }
}