package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * 浅色 ColorScheme（「墨绿金 · 静奢记账」，原型 v2.1 基准）。
 *
 * 本轮锁定浅色（任务决策 D4）：`VipLedgerTheme` 不再跟随系统深色，
 * 暗色模式待后续单独出原型基准再恢复。
 */
private val LightColorScheme = lightColorScheme(
    primary = InkGreen,
    onPrimary = OnInkGreen,
    primaryContainer = InkGreenTint,
    onPrimaryContainer = InkGreenDeep,
    secondary = InkGreenSoft,
    onSecondary = OnInkGreen,
    secondaryContainer = GoldWash,
    onSecondaryContainer = GoldInk,
    tertiary = TransferBlue,
    onTertiary = SurfaceLight,
    tertiaryContainer = Color(0xFFE6EDF3),
    onTertiaryContainer = TransferBlue,
    error = ExpenseRed,
    onError = SurfaceLight,
    errorContainer = Color(0xFFF7E7E3),
    onErrorContainer = ExpenseRed,
    background = PageLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SoftLight,
    onSurfaceVariant = InkMutedLight,
    outline = HairlineStrong,
    outlineVariant = Hairline,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = Color(0xFFFBF9F4),
    surfaceContainer = Color(0xFFF3F1E9),
    surfaceContainerHigh = Color(0xFFECE9DF),
    surfaceContainerHighest = Color(0xFFE4E1D5),
)

/**
 * 「墨绿金」强调色组：hero 渐变、快捷操作点睛、状态点缀共用。
 *
 * 强调色是**有限制**的装饰资源：不参与语义色（支出/收入/转账），也不进图表色板；
 * 界面里禁止写死颜色值，一律从 [VipLedgerThemeDefaults] 或 CompositionLocal 取。
 * 取值对齐原型：hero 为 `linear-gradient(150deg, --accent-2, --accent-deep)`，
 * 内容色为象牙金白 `--accent-text`；金色两档分别用于浅底与深墨绿容器。
 */
@Immutable
data class VipLedgerAccentColors(
    /** 墨绿亮档（hero 渐变亮端、快捷入口选中态）。 */
    val accentMint: Color,
    val onAccentMint: Color,
    val accentMintContainer: Color,
    val onAccentMintContainer: Color,
    /** 黄铜金：待确认/提醒类「温和需要行动」的点睛（原型待确认草稿的金色线索）。 */
    val accentCoral: Color,
    val onAccentCoral: Color,
    /** 亮黄铜金：徽标、深墨绿容器上的金色细节。 */
    val accentAmber: Color,
    val onAccentAmber: Color,
    /** hero 渐变亮端（`--accent-2 #24504A`）。 */
    val heroGradientStart: Color,
    /** hero 渐变暗端（`--accent-deep #0F2A24`）。 */
    val heroGradientEnd: Color,
    /** hero 容器上的内容色（象牙金白 `--accent-text`，≥ 4.5:1）。 */
    val onHero: Color,
    /** hero 容器上的次要内容色（65% 象牙金白，原型 `#f0e7d6a6`）。 */
    val onHeroMuted: Color,
)

private val LightAccentColors = VipLedgerAccentColors(
    accentMint = InkGreenSoft,
    onAccentMint = OnInkGreen,
    accentMintContainer = InkGreenTint,
    onAccentMintContainer = InkGreenDeep,
    accentCoral = BrassGold,
    onAccentCoral = Color(0xFF241A02),
    accentAmber = BrassGoldLight,
    onAccentAmber = InkGreenDeep,
    heroGradientStart = InkGreenSoft,
    heroGradientEnd = InkGreenDeep,
    onHero = OnInkGreen,
    onHeroMuted = Color(0xA6F0E7D6.toInt()), // #f0e7d6 @ 65%
)

val LocalVipLedgerAccentColors = staticCompositionLocalOf { LightAccentColors }

/**
 * 设计系统 shape token：组件统一从这里取圆角，不再各页面自定义。
 * `MaterialTheme.shapes` 之外的补充档位（大容器 / 全圆胶囊）。
 */
@Immutable
data class VipLedgerShapes(
    val card: Shape,
    val dialog: Shape,
    val pill: Shape,
    val field: Shape,
)

private val LightVipLedgerShapes = VipLedgerShapes(
    card = RoundedCornerShape(VipLedgerTokens.RadiusCard),
    dialog = RoundedCornerShape(VipLedgerTokens.RadiusLarge),
    pill = RoundedCornerShape(VipLedgerTokens.RadiusFull),
    field = RoundedCornerShape(VipLedgerTokens.RadiusSmall),
)

val LocalVipLedgerShapes = staticCompositionLocalOf { LightVipLedgerShapes }

/** 强调色的静态访问器（非 Composable 上下文/预览用）；Composable 内优先用 CompositionLocal。 */
object VipLedgerThemeDefaults {
    /** 本轮锁定浅色（D4）：无论系统深浅，一律返回浅色强调色组。 */
    fun accentColors(): VipLedgerAccentColors = LightAccentColors

    fun shapes(): VipLedgerShapes = LightVipLedgerShapes
}

/** 强调色的便捷 Composable 访问器。 */
@Composable
fun vipLedgerAccentColors(): VipLedgerAccentColors = LocalVipLedgerAccentColors.current

/** shape token 的便捷 Composable 访问器。 */
@Composable
fun vipLedgerShapes(): VipLedgerShapes = LocalVipLedgerShapes.current

/**
 * 全局主题。本轮锁定浅色（任务决策 D4）：不跟随系统深色模式，
 * 状态栏/导航栏图标由 `MainActivity` 以浅色 scrim 强制为深色图标。
 */
@Composable
fun VipLedgerTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = VipLedgerTypography,
        content = {
            CompositionLocalProvider(
                LocalVipLedgerAccentColors provides LightAccentColors,
                LocalVipLedgerShapes provides LightVipLedgerShapes,
            ) {
                content()
            }
        },
    )
}
