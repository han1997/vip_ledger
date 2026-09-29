package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 设计 token 的回归测试：间距步进、圆角档位、深浅色成对取值的稳定性。
 * token 改值必须是有意为之 —— 这里的断言让漂移立刻可见。
 */
class TokensTest {

    @Test
    fun spacingFollowsFourDpGrid() {
        assertEquals(4.dp, VipLedgerTokens.spacing(1))
        assertEquals(8.dp, VipLedgerTokens.spacing(2))
        assertEquals(16.dp, VipLedgerTokens.spacing(4))
        assertEquals(32.dp, VipLedgerTokens.spacing(8))
    }

    @Test
    fun spacingScaleCoversStandardStops() {
        assertEquals(4.dp, VipLedgerTokens.Space1)
        assertEquals(8.dp, VipLedgerTokens.Space2)
        assertEquals(12.dp, VipLedgerTokens.Space3)
        assertEquals(16.dp, VipLedgerTokens.Space4)
        assertEquals(20.dp, VipLedgerTokens.Space5)
        assertEquals(24.dp, VipLedgerTokens.Space6)
        assertEquals(32.dp, VipLedgerTokens.Space8)
    }

    @Test
    fun radiiAscendMonotonically() {
        assertTrue(VipLedgerTokens.RadiusSmall < VipLedgerTokens.RadiusMedium)
        assertTrue(VipLedgerTokens.RadiusMedium < VipLedgerTokens.RadiusLarge)
        assertTrue(VipLedgerTokens.RadiusLarge < VipLedgerTokens.RadiusFull)
    }

    @Test
    fun elevationsAscendMonotonically() {
        assertTrue(VipLedgerTokens.ElevationLevel0 < VipLedgerTokens.ElevationLevel1)
        assertTrue(VipLedgerTokens.ElevationLevel1 < VipLedgerTokens.ElevationLevel2)
        assertTrue(VipLedgerTokens.ElevationLevel2 < VipLedgerTokens.ElevationLevel3)
        assertTrue(VipLedgerTokens.ElevationLevel3 < VipLedgerTokens.ElevationLevel4)
        assertTrue(VipLedgerTokens.ElevationLevel4 < VipLedgerTokens.ElevationLevel5)
    }

    @Test
    fun durationsStayWithinMicroInteractionBudget() {
        // 微动效不超过 300ms，避免「活泼」变成拖沓。
        assertTrue(VipLedgerTokens.DurationShort < VipLedgerTokens.DurationMedium)
        assertTrue(VipLedgerTokens.DurationMedium < VipLedgerTokens.DurationLong)
        assertTrue(VipLedgerTokens.DurationLong <= 300)
    }

    @Test
    fun touchTargetMeetsAccessibilityMinimum() {
        assertTrue(VipLedgerTokens.TouchTargetMin >= 48.dp)
    }
}
