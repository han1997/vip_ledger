package com.han1997.vipledger.feature.stats.ui.common

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.designsystem.format.suggestedFileName
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class FilterOptionsTest {

    @Test
    fun categoryOptionsListTopLevelThenItsChildrenWithParentPrefix() {
        val nodes = listOf(
            CategoryNode(
                category = category("food", "餐饮"),
                children = listOf(category("lunch", "午餐", parentId = "food")),
            ),
            CategoryNode(category = category("transport", "交通")),
        )

        assertEquals(
            listOf(
                FilterOption("food", "餐饮"),
                FilterOption("lunch", "餐饮 / 午餐"),
                FilterOption("transport", "交通"),
            ),
            nodes.toCategoryOptions(),
        )
    }

    @Test
    fun accountAndTagOptionsUseTheirNames() {
        assertEquals(
            listOf(FilterOption("a1", "现金")),
            listOf(account("a1", "现金")).toAccountOptions(),
        )
        assertEquals(
            listOf(FilterOption("t1", "报销")),
            listOf(Tag(id = "t1", ledgerId = "l1", name = "报销", createdAt = 0, updatedAt = 0)).toTagOptions(),
        )
    }

    @Test
    fun exportFileNamesCarryLedgerRangeAndScope() {
        val date = LocalDate(2026, 9, 24)

        assertEquals(
            "vipledger-search-默认账本-20260901-0924-20260924.csv",
            suggestedFileName("search", "默认账本", "20260901-0924", date),
        )
        assertEquals(
            "vipledger-stats-20260924.png",
            suggestedFileName("stats", "", "", date, extension = "png"),
        )
        // 文件系统非法字符与空白折叠为连字符，空段省略。
        assertEquals(
            "vipledger-trend-My-Ledger-20260924.csv",
            suggestedFileName("trend", "My / Ledger?", "", date),
        )
    }

    private fun category(id: String, name: String, parentId: String? = null) = Category(
        id = id,
        ledgerId = "l1",
        parentId = parentId,
        name = name,
        kind = CategoryKind.EXPENSE,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun account(id: String, name: String) = Account(
        id = id,
        ledgerId = "l1",
        name = name,
        type = AccountType.CASH,
        currentBalance = Money.ZERO,
        createdAt = 0,
        updatedAt = 0,
    )
}