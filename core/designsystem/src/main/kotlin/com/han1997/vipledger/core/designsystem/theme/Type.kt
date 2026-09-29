package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private const val TABULAR_FIGURES = "tnum"

/**
 * 字体版式：金额与统计页面依赖稳定的字号层级，避免列表里数字跳动。
 *
 * V2 调整：
 * - 所有含数字的样式都开 `fontFeatureSettings = "tnum"`（等宽数字），金额列不再左右跳动；
 * - 新增 [displayHero]（首页净资产 hero）与 [displayAmount]（大额金额）两个扩展样式，
 *   不覆盖 Material 既有样式名，既有调用点不受影响。
 */
val VipLedgerTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
)

/** 首页净资产 hero：首页以外的「大额汇总」也可以用（注意别滥用）。 */
val displayHero: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 34.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = TABULAR_FIGURES,
)

/** 中等规模的金额汇总（月度收支卡等），比 headline 小一级。 */
val displayAmount: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.2).sp,
    fontFeatureSettings = TABULAR_FIGURES,
)
