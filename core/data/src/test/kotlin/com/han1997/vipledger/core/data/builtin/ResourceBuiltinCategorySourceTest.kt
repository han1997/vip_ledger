package com.han1997.vipledger.core.data.builtin

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.model.CategoryKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourceBuiltinCategorySourceTest {

    private val source = ResourceBuiltinCategorySource(ApplicationProvider.getApplicationContext())

    @Test
    fun builtinTreeIsTwoLevelsDeepAndNonEmpty() {
        val categories = source.categories()
        assertTrue(categories.isNotEmpty())
        assertTrue(categories.all { it.name.isNotBlank() })
        assertTrue(categories.all { it.children.all { child -> child.children.isEmpty() } })
        assertEquals(16, categories.size, "10 个支出一级 + 6 个收入一级")
    }

    @Test
    fun kindsAreCorrectAndNamesComeFromResources() {
        val expense = source.categories().filter { it.kind == CategoryKind.EXPENSE }
        val income = source.categories().filter { it.kind == CategoryKind.INCOME }
        assertEquals(10, expense.size)
        assertEquals(6, income.size)
        assertEquals("餐饮", expense.first().name)
        assertEquals(listOf("早餐", "午餐", "晚餐", "外卖", "零食"), expense.first().children.map { it.name })
        assertEquals("工资", income.first().name)
    }
}