package com.han1997.vipledger.core.model.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun parseAcceptsCommonUserInput() {
        assertEquals(Money.ofCents(1200), Money.parse("12"))
        assertEquals(Money.ofCents(1230), Money.parse("12.3"))
        assertEquals(Money.ofCents(1234), Money.parse("12.34"))
        assertEquals(Money.ofCents(123456), Money.parse("¥1,234.56"))
        assertEquals(Money.ofCents(123456), Money.parse("  1,234.56  "))
        assertEquals(Money.ofCents(500), Money.parse("+5"))
        assertEquals(Money.ofCents(-500), Money.parse("-5"))
        assertEquals(Money.ofCents(50), Money.parse(".5"))
        assertEquals(Money.ofCents(0), Money.parse("0"))
    }

    @Test
    fun parseRoundsHalfUpOnThirdDecimal() {
        assertEquals(Money.ofCents(1235), Money.parse("12.345"))
        assertEquals(Money.ofCents(1234), Money.parse("12.344"))
        assertEquals(Money.ofCents(1235), Money.parse("12.3459"))
        assertEquals(Money.ofCents(1), Money.parse("0.005"))
        assertEquals(Money.ofCents(0), Money.parse("0.004"))
    }

    @Test
    fun parseRejectsGarbageAndOverflow() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("   "))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("12元"))
        assertNull(Money.parse("99999999999999999999"))
    }

    @Test
    fun arithmeticKeepsCentPrecision() {
        val a = Money.ofCents(1000)
        val b = Money.ofCents(2345)
        assertEquals(Money.ofCents(3345), a + b)
        assertEquals(Money.ofCents(1345), b - a)
        assertEquals(Money.ofCents(3000), a * 3)
        assertEquals(Money.ofCents(-1000), -a)
        assertTrue(a < b)
        assertEquals(Money.ofCents(2345), (-b).abs())
    }

    @Test
    fun percentageRoundsHalfUp() {
        assertEquals(Money.ofCents(333), Money.ofCents(1000).percentage(33.3333))
        assertEquals(Money.ofCents(525), Money.ofCents(1050).percentage(50.0))
        assertEquals(Money.ofCents(0), Money.ofCents(1000).percentage(0.0))
    }

    @Test
    fun splitEvenlyKeepsTotal() {
        val parts = Money.ofCents(100).splitEvenly(3)
        assertEquals(listOf(Money.ofCents(34), Money.ofCents(33), Money.ofCents(33)), parts)
        assertEquals(Money.ofCents(100), Money.sum(parts))

        val negative = Money.ofCents(-100).splitEvenly(3)
        assertEquals(Money.ofCents(-100), Money.sum(negative))
        assertEquals(Money.ofCents(-34), negative.first())

        assertEquals(Money.ofCents(2400), Money.sum(Money.ofCents(2400).splitEvenly(12)))
    }

    @Test
    fun formattingProducesExpectedText() {
        assertEquals("1234.56", Money.ofCents(123456).toPlainString())
        assertEquals("-0.05", Money.ofCents(-5).toPlainString())
        assertEquals("¥1,234.56", Money.ofCents(123456).toDisplayString())
        assertEquals("1,000,000.00", Money.ofCents(100000000).toDisplayString(includeSymbol = false))
        assertEquals("-¥12.00", Money.ofCents(-1200).toDisplayString())
    }
}
