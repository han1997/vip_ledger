package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 强调色配对测试：确保 [VipLedgerAccentColors] 在浅色/深色模式下成对给出且取值不同
 * （防止「浅色有、深色忘改」的回归）；hero 容器不得与页面背景同色；深色模式 hero
 * 退化为纯色容器（两端同色）。真实对比度由模拟器深浅色走查背书。
 */
class AccentColorsTest {

    private val lightAccents = VipLedgerThemeDefaults.accentColors(darkTheme = false)
    private val darkAccents = VipLedgerThemeDefaults.accentColors(darkTheme = true)

    @Test
    fun lightAndDarkAreDistinct() {
        assertNotEquals(lightAccents.accentMint, darkAccents.accentMint)
        assertNotEquals(lightAccents.accentCoral, darkAccents.accentCoral)
        assertNotEquals(lightAccents.accentAmber, darkAccents.accentAmber)

    }

    @Test
    fun heroOnColorsAreNotTheSameAsHeroColors() {
        assertNotEquals(lightAccents.heroGradientStart, lightAccents.onHero)
        assertNotEquals(darkAccents.heroGradientStart, darkAccents.onHero)
    }

    @Test
    fun heroContainerIsDistinctFromPageBackground() {
        assertNotEquals(lightAccents.heroGradientStart, PageLight)
        assertNotEquals(darkAccents.heroGradientStart, PageDark)
    }

    @Test
    fun darkHeroDegradesToSolidContainer() {
        // 深色模式不玩渐变：两端同色 → 实际为纯色容器（可读性优先）。
        assertEquals(darkAccents.heroGradientStart, darkAccents.heroGradientEnd)
    }

    @Test
    fun semanticTransactionColorsStayUnchanged() {
        // V2 明确：语义色保持既有取值；改动必须同步 charts 文档与统计口径走查。
        assertEquals(0xFFC24B3A, ExpenseRed.toArgb().toLong() and 0xFFFFFFFFL)
        assertEquals(0xFF1F8A4C, IncomeGreen.toArgb().toLong() and 0xFFFFFFFFL)
        assertEquals(0xFF2F6F96, TransferBlue.toArgb().toLong() and 0xFFFFFFFFL)
    }
}

