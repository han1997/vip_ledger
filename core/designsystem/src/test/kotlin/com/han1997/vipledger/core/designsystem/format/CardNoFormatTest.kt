package com.han1997.vipledger.core.designsystem.format

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 账户卡号的展示规则（原型 v1.4 / 任务 D3）：
 * 按 4 位分组展示、取尾号；非数字字符先剔除，不足分组长度不补空格。
 */
class CardNoFormatTest {

    @Test
    fun groupsDigitsInFours() {
        assertEquals("6225 8801 2345 6021", formatCardNo("6225880123456021"))
    }

    @Test
    fun stripsNonDigitsBeforeGrouping() {
        assertEquals("6225 8801 2345 6021", formatCardNo("6225-8801 2345_6021"))
    }

    @Test
    fun shortNumbersStayUngrouped() {
        assertEquals("123", formatCardNo("123"))
    }

    @Test
    fun blankOrNullBecomesEmpty() {
        assertEquals("", formatCardNo(null))
        assertEquals("", formatCardNo(""))
        assertEquals("", formatCardNo("   "))
    }

    @Test
    fun lastFourDigitsAreExtracted() {
        assertEquals("6021", cardNoLast4("6225880123456021"))
        assertEquals("123", cardNoLast4("123"))
        assertEquals("", cardNoLast4(null))
    }
}