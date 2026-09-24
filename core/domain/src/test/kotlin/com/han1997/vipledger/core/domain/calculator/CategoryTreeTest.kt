package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.model.CategoryKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CategoryTreeTest {

    private fun category(
        id: String,
        name: String,
        kind: CategoryKind = CategoryKind.EXPENSE,
        parentId: String? = null,
        ledgerId: String = "l1",
    ) = Category(
        id = id,
        ledgerId = ledgerId,
        parentId = parentId,
        name = name,
        kind = kind,
        createdAt = 0,
        updatedAt = 0,
    )

    private val existing = listOf(
        category("food", "餐饮"),
        category("breakfast", "早餐", parentId = "food"),
        category("salary", "工资", kind = CategoryKind.INCOME),
    )

    @Test
    fun acceptsSecondLevelUnderSameLedgerAndKind() {
        assertNull(CategoryTree.validate(category("lunch", "午餐", parentId = "food"), existing))
        assertNull(CategoryTree.validate(category("new", "新分类"), existing))
    }

    @Test
    fun rejectsBlankNameAndDuplicateSiblingName() {
        assertIs<AppError.Validation>(CategoryTree.validate(category("x", "  "), existing))
        assertIs<AppError.Validation>(CategoryTree.validate(category("x", "早餐", parentId = "food"), existing))
        assertNull(CategoryTree.validate(category("breakfast", "早餐", parentId = "food"), existing))
    }

    @Test
    fun rejectsMissingForeignOrWrongKindParent() {
        assertIs<AppError.Validation>(CategoryTree.validate(category("x", "夜宵", parentId = "nope"), existing))
        assertIs<AppError.Validation>(CategoryTree.validate(category("x", "夜宵", kind = CategoryKind.INCOME, parentId = "food"), existing))
        assertIs<AppError.Validation>(
            CategoryTree.validate(category("x", "夜宵", parentId = "food", ledgerId = "l2"), existing),
        )
    }

    @Test
    fun rejectsThirdLevelCategories() {
        assertIs<AppError.Validation>(CategoryTree.validate(category("x", "包子", parentId = "breakfast"), existing))
    }

    @Test
    fun subtreeExpandsChildrenAndTopLevelLookupFindsAncestor() {
        assertEquals(setOf("food", "breakfast"), CategoryTree.subtreeIds(listOf("food"), existing))
        assertEquals(setOf("breakfast"), CategoryTree.subtreeIds(listOf("breakfast"), existing))
        assertEquals("food", CategoryTree.topLevelOf("breakfast", existing)?.id)
        assertEquals("food", CategoryTree.topLevelOf("food", existing)?.id)
        assertEquals(listOf("breakfast"), CategoryTree.childrenOf("food", existing).map { it.id })
        assertEquals(listOf("food", "salary"), CategoryTree.topLevel(existing).map { it.id })
    }
}