package com.han1997.vipledger.feature.stats.navigation

import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class DrillArgsTest {

    @Test
    fun blankArgumentsBecomeNull() {
        val args = drillArgsOf(
            ledgerId = "l1",
            categoryId = "",
            tagId = "",
            accountId = "",
            preset = "",
            from = "",
            to = "",
        )

        assertEquals("l1", args.ledgerId)
        assertNull(args.categoryId)
        assertNull(args.tagId)
        assertNull(args.accountId)
        assertNull(args.preset)
        assertNull(args.range)
    }

    @Test
    fun missingLedgerIdFallsBackToEmptyStringInsteadOfNull() {
        assertEquals("", drillArgsOf(null, null, null, null, null, null, null).ledgerId)
    }

    @Test
    fun knownPresetAndValidRangeAreParsed() {
        val args = drillArgsOf(
            ledgerId = "l1",
            categoryId = "food",
            tagId = null,
            accountId = null,
            preset = DateRangePreset.THIS_MONTH.name,
            from = "2026-09-01",
            to = "2026-09-30",
        )

        assertEquals(DateRangePreset.THIS_MONTH, args.preset)
        assertEquals(DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)), args.range)
    }

    @Test
    fun illegalPresetAndReversedRangeAreIgnored() {
        val args = drillArgsOf(
            ledgerId = "l1",
            categoryId = null,
            tagId = null,
            accountId = null,
            preset = "NOT_A_PRESET",
            from = "2026-09-30",
            to = "2026-09-01",
        )

        assertNull(args.preset)
        assertNull(args.range, "开始晚于结束的区间直接忽略，不让路由参数把页面搞崩")
    }

    @Test
    fun routeCarriesEveryCondition() {
        val route = StatsRoutes.drill(
            DrillArgs(
                ledgerId = "l1",
                tagId = "t1",
                preset = DateRangePreset.LAST_MONTH,
                range = DateRange(LocalDate(2026, 8, 1), LocalDate(2026, 8, 31)),
            ),
        )

        assertTrue(route.startsWith("stats/drill/l1?"))
        assertTrue(route.contains("tagId=t1"))
        assertTrue(route.contains("preset=" + DateRangePreset.LAST_MONTH.name))
        assertTrue(route.contains("from=2026-08-01"))
        assertTrue(route.contains("to=2026-08-31"))
    }
}