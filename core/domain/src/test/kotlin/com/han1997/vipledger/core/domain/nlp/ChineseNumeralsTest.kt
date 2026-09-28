package com.han1997.vipledger.core.domain.nlp

import kotlin.test.Test
import kotlin.test.assertEquals

/** 中文数词归一（研究 A8–A19），重点是口语省位与 `零` 隔断。 */
class ChineseNumeralsTest {

    private fun convert(text: String) = ChineseNumerals.toArabicString(text)

    @Test
    fun standardNumerals() {
        assertEquals("12", convert("十二"))
        assertEquals("10", convert("十"))
        assertEquals("23", convert("二十三"))
        assertEquals("35", convert("三十五"))
        assertEquals("1050", convert("一千零五十"))
    }

    @Test
    fun colloquialPlaceOmission() {
        // 百后裸数字进十位、千后进百位、万后进千位。
        assertEquals("120", convert("一百二"))
        assertEquals("3500", convert("三千五"))
        assertEquals("15000", convert("一万五"))
        assertEquals("250", convert("两百五"))
    }

    @Test
    fun zeroClosesPlaceOmission() {
        // 出现「零」即关闭省位：一万零五 ≠ 一万五。
        assertEquals("10005", convert("一万零五"))
        assertEquals("105", convert("一百零五"))
        assertEquals("15005", convert("一万五千零五"))
    }

    @Test
    fun mixedAndLargeNumbers() {
        assertEquals("123", convert("1百23"))
        assertEquals("30000000", convert("3000万"))
        assertEquals("150000000", convert("1.5亿"))
    }

    @Test
    fun decimalsAndDigits() {
        assertEquals("1.23", convert("一点二三"))
        assertEquals("12.5", convert("12.5"))
        assertEquals("5", convert("零五"))
    }

    @Test
    fun oneHundredTwoIsNotOneHundredZeroTwo() {
        // 歧义陷阱对照（研究 §1.3）：省位 vs 零隔断。
        assertEquals("120", convert("一百二"))
        assertEquals("102", convert("一百零二"))
    }
}
