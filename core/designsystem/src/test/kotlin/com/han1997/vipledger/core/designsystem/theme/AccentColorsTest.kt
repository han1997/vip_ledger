package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 强调色配对测试（墨绿金基准，本轮锁定浅色 —— 任务决策 D4）。
 *
 * hero 容器不得与页面背景同色；hero 内容色不得与容器同色；
 * 语义色取值以已确认原型 v2.1 为准（支出赭红 / 收入松绿 / 转账靛蓝）。
 */
class AccentColorsTest {

    private val accents = VipLedgerThemeDefaults.accentColors()

    @Test
    fun heroOnColorsAreNotTheSameAsHeroColors() {
        assertNotEquals(accents.heroGradientStart, accents.onHero)
        assertNotEquals(accents.heroGradientEnd, accents.onHero)
        assertNotEquals(accents.onHero, accents.onHeroMuted)
    }

    @Test
    fun heroContainerIsDistinctFromPageBackground() {
        assertNotEquals(accents.heroGradientStart, PageLight)
        assertNotEquals(accents.heroGradientEnd, PageLight)
    }

    @Test
    fun heroGradientMatchesPrototypeInkGreenEndpoints() {
        // 原型 --accent-2 #24504A → --accent-deep #0F2A24（linear-gradient 150deg）。
        assertEquals(0xFF24504A, accents.heroGradientStart.toArgb().toLong() and 0xFFFFFFFFL)
        assertEquals(0xFF0F2A24, accents.heroGradientEnd.toArgb().toLong() and 0xFFFFFFFFL)
        assertEquals(0xFFF0E7D6, accents.onHero.toArgb().toLong() and 0xFFFFFFFFL)
    }

    @Test
    fun semanticTransactionColorsMatchConfirmedBaseline() {
        // 原型 v2.1 已确认基准：--expense #B23A2E / --income #2E7D5B / --transfer #3B5B7A。
        // 改动必须先改原型并重新确认，再同步这里与 charts 文档。
        assertEquals(0xFFB23A2E, ExpenseRed.toArgb().toLong() and 0xFFFFFFFFL)
        assertEquals(0xFF2E7D5B, IncomeGreen.toArgb().toLong() and 0xFFFFFFFFL)
        assertEquals(0xFF3B5B7A, TransferBlue.toArgb().toLong() and 0xFFFFFFFFL)
    }

    @Test
    fun goldAccentsStayOutOfSemanticColors() {
        // 金色是有限装饰资源：不得混入语义色（支出/收入/转账）。
        val semantics = setOf(ExpenseRed, IncomeGreen, TransferBlue)
        assertNotEquals(accents.accentCoral, ExpenseRed)
        assertNotEquals(accents.accentAmber, IncomeGreen)
        assertEquals(3, semantics.size)
    }
}
