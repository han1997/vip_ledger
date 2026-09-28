package com.han1997.vipledger.core.domain.nlp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** 日期/时间抽取，全部相对显式参考日 [REF]=2026-09-28（星期一）断言，不读系统时钟。 */
class DateExtractorTest {

    private fun date(text: String): LocalDate? = DateExtractor.extract(text, SpanTable(text), REF)

    private fun time(text: String): LocalTime? = TimeExtractor.extract(text, SpanTable(text))

    @Test
    fun relativeDays() {
        assertEquals(LocalDate(2026, 9, 28), date("今天午饭"))
        assertEquals(LocalDate(2026, 9, 27), date("昨天午饭"))
        assertEquals(LocalDate(2026, 9, 26), date("前天超市"))
        assertEquals(LocalDate(2026, 9, 29), date("明天午饭"))
        assertEquals(LocalDate(2026, 9, 25), date("3天前买菜"))
    }

    @Test
    fun weekdaysResolveAgainstNaturalWeeks() {
        // 上周三 = 上一个自然周（周一为首）的周三。
        assertEquals(LocalDate(2026, 9, 23), date("上周三打车"))
        // 这周三 = 本周内不早于今天的周三（09-28 是周一 → 09-30）。
        assertEquals(LocalDate(2026, 9, 30), date("这周三聚餐"))
        // 裸周三 = 严格晚于今天的下一个周三。
        assertEquals(LocalDate(2026, 9, 30), date("周三聚餐"))
        // 下周三 = 下一自然周的周三（自然周读法，非 Duckling 的「明天」）。
        assertEquals(LocalDate(2026, 10, 7), date("下周三聚餐"))
    }

    @Test
    fun absoluteDates() {
        assertEquals(LocalDate(2026, 3, 5), date("3月5日 房租"))
        assertEquals(LocalDate(2026, 3, 5), date("3/5 买书"))
        assertEquals(LocalDate(2026, 10, 31), date("10月31 交租"))
        assertEquals(LocalDate(2025, 3, 3), date("2025年3月3号 买书"))
        // 裸「N 号」取最近不晚于参考日的该日号。
        assertEquals(LocalDate(2026, 9, 5), date("5号交话费"))
    }

    @Test
    fun timesOfDay() {
        assertEquals(LocalTime(8, 0), time("今天早上买咖啡"))
        assertEquals(LocalTime(20, 0), time("晚上打车回家"))
        assertEquals(LocalTime(15, 30), time("三点半买奶茶"))
        assertEquals(LocalTime(15, 15), time("下午三点十五开会"))
        assertEquals(LocalTime(15, 0), time("三点开会"))
        assertEquals(LocalTime(2, 0), time("凌晨两点睡觉"))
    }

    @Test
    fun dateDigitsAreClaimedSoAmountCannotStealThem() {
        val text = "3月5日 房租 3500"
        val table = SpanTable(text)
        assertEquals(LocalDate(2026, 3, 5), DateExtractor.extract(text, table, REF))
        val amounts = AmountExtractor.extract(text, table).candidates
        assertEquals(1, amounts.size)
        assertEquals(350_000, amounts.single().value.cents)
    }
}
