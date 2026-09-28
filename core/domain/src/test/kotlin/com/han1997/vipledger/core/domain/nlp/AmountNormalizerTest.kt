package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 金额量词文法（研究 A1–A8）与 **`Money.parse` 归一化边界**。
 *
 * `Money.parse` 只认 `¥/￥/$`、千分位、前导 `±`，不认中文数字与 `块/毛/角`；
 * 本层先把中文金额归一成十进制串，再交给它。
 */
class AmountNormalizerTest {

    private fun normalized(text: String) = AmountNormalizer.normalize(text)

    @Test
    fun arabicForms() {
        assertEquals("35", normalized("35"))
        assertEquals("28.5", normalized("28.5"))
        assertEquals("12.50", normalized("12.50"))
        assertEquals("35.00", normalized("35块"))
        assertEquals("35.00", normalized("35 块"))
        assertEquals("35", normalized("¥35"))
    }

    @Test
    fun yuanJiaoFenGrammar() {
        assertEquals("12.50", normalized("12块5"))
        assertEquals("12.50", normalized("12块5毛"))
        assertEquals("12.05", normalized("12块5分"))
        assertEquals("12.53", normalized("12元5角3分"))
        assertEquals("12.05", normalized("12块零5"))
        assertEquals("12.50", normalized("十二块五"))
        assertEquals("10.50", normalized("十块五"))
        assertEquals("1.50", normalized("一块五"))
    }

    @Test
    fun jiaoFenOnlyForms() {
        assertEquals("0.50", normalized("五毛"))
        assertEquals("0.05", normalized("五分"))
    }

    @Test
    fun moneyParseBoundary() {
        // 归一化后交给既有 Money.parse 完成「元 → 分」换算。
        assertEquals(Money.ofCents(1250), Money.parse(normalized("12块5")!!))
        assertEquals(Money.ofCents(1200), Money.parse(normalized("十二块")!!))
        assertEquals(Money.ofCents(12_000), Money.parse(normalized("一百二")!!))
        assertEquals(Money.ofCents(1500000), Money.parse(normalized("一万五")!!))
        // 边界证明：未归一的原始中文金额 Money.parse 处理不了（返回 null）。
        assertNull(Money.parse("十二块五"))
        assertNull(Money.parse("一百二"))
    }

    @Test
    fun colloquialOmissionFlowsThrough() {
        assertEquals("120", normalized("一百二"))
        assertEquals("15000", normalized("一万五"))
        assertEquals("10005", normalized("一万零五"))
        assertEquals("3500", normalized("三千五"))
    }
}
