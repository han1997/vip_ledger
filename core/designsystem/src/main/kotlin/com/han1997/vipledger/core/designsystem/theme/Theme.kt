package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

private val LightColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = SurfaceLight,
    primaryContainer = BrandGreenMuted,
    onPrimaryContainer = BrandGreenDark,
    secondary = BrandGreenDark,
    onSecondary = SurfaceLight,
    secondaryContainer = BrandGreenLight,
    onSecondaryContainer = BrandGreenDark,
    tertiary = TransferBlue,
    onTertiary = SurfaceLight,
    background = PageLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = BrandGreenMuted,
    onSurfaceVariant = InkMutedLight,
    outline = NeutralOutline,
    outlineVariant = Color(0xFFE1E8E3),
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = Color(0xFFF8FBFA),
    surfaceContainer = Color(0xFFEEF3F0),
    surfaceContainerHigh = Color(0xFFE7EEEA),
    surfaceContainerHighest = Color(0xFFDFE8E3),
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandGreenLight,
    onPrimary = BrandGreenDark,
    primaryContainer = BrandGreenDark,
    onPrimaryContainer = BrandGreenLight,
    secondary = BrandGreenLight,
    onSecondary = BrandGreenDark,
    secondaryContainer = Color(0xFF1C3328),
    onSecondaryContainer = BrandGreenLight,
    tertiary = Color(0xFF8EC4E4),
    onTertiary = Color(0xFF0C2A3A),
    background = PageDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = Color(0xFF24302A),
    onSurfaceVariant = InkMutedDark,
    outline = Color(0xFF3C4A43),
    outlineVariant = Color(0xFF2A342F),
    surfaceContainerLowest = Color(0xFF0E1210),
    surfaceContainerLow = Color(0xFF171C19),
    surfaceContainer = Color(0xFF1C231F),
    surfaceContainerHigh = Color(0xFF262E29),
    surfaceContainerHighest = Color(0xFF313A34),
)

/**
 * V2「活泼品牌化」强调色组：hero 渐变、快捷操作点睛、状态点缀共用。
 *
 * 强调色是**有限制**的装饰资源：不参与语义色（支出/收入/转账），也不进图表色板；
 * 浅色模式取偏深的可读值，深色模式取偏亮的可读值，两组成对出现。
 * 界面里禁止写死颜色值，一律从 [VipLedgerThemeDefaults] 或 CompositionLocal 取。
 */
@Immutable
data class VipLedgerAccentColors(
    val accentMint: Color,
    val onAccentMint: Color,
    val accentMintContainer: Color,
    val onAccentMintContainer: Color,
    val accentCoral: Color,
    val onAccentCoral: Color,
    val accentAmber: Color,
    val onAccentAmber: Color,
    /** hero 渐变亮端（浅色模式为亮绿，深色模式自动退化为纯色容器保证可读）。 */
    val heroGradientStart: Color,
    /** hero 渐变暗端（深色模式与 start 相同 → 实际无渐变）。 */
    val heroGradientEnd: Color,
    /** hero 容器上的内容色（两个模式下都保证 ≥ 4.5:1）。 */
    val onHero: Color,
    /** hero 容器上的次要内容色。 */
    val onHeroMuted: Color,
)

private val LightAccentColors = VipLedgerAccentColors(
    accentMint = AccentMintLight,
    onAccentMint = OnAccentLight,
    accentMintContainer = BrandGreenMuted,
    onAccentMintContainer = BrandGreenDark,
    accentCoral = AccentCoralLight,
    onAccentCoral = Color(0xFF3A1405),
    accentAmber = AccentAmberLight,
    onAccentAmber = Color(0xFF241A02),
    // 渐变两端都压在品牌绿的暗段：白字 5.3:1+ / 浅绿次级字 4.9:1+（WCAG AA）。
    heroGradientStart = Color(0xFF155A3E),
    heroGradientEnd = Color(0xFF1D7148),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xFFD5EFE2),
)

private val DarkAccentColors = VipLedgerAccentColors(
    accentMint = AccentMintDark,
    onAccentMint = OnAccentDark,
    accentMintContainer = Color(0xFF1C3328),
    onAccentMintContainer = BrandGreenLight,
    accentCoral = AccentCoralDark,
    onAccentCoral = Color(0xFF3A1405),
    accentAmber = AccentAmberDark,
    onAccentAmber = Color(0xFF2A1E03),
    // 深色模式不玩渐变：两端同色，hero 退化为纯色容器，保证金额可读。
    heroGradientStart = Color(0xFF1F4A36),
    heroGradientEnd = Color(0xFF1F4A36),
    onHero = InkDark,
    onHeroMuted = InkMutedDark,
)

val LocalVipLedgerAccentColors = staticCompositionLocalOf { LightAccentColors }

/**
 * V2 设计系统 shape token：组件统一从这里取圆角，不再各页面自定义。
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
    card = RoundedCornerShape(VipLedgerTokens.RadiusMedium),
    dialog = RoundedCornerShape(VipLedgerTokens.RadiusLarge),
    pill = RoundedCornerShape(VipLedgerTokens.RadiusFull),
    field = RoundedCornerShape(VipLedgerTokens.RadiusSmall),
)

val LocalVipLedgerShapes = staticCompositionLocalOf { LightVipLedgerShapes }

/** 强调色的静态访问器（非 Composable 上下文/预览用）；Composable 内优先用 CompositionLocal。 */
object VipLedgerThemeDefaults {
    fun accentColors(darkTheme: Boolean): VipLedgerAccentColors =
        if (darkTheme) DarkAccentColors else LightAccentColors

    fun shapes(): VipLedgerShapes = LightVipLedgerShapes
}

/** 强调色的便捷 Composable 访问器。 */
@Composable
fun vipLedgerAccentColors(): VipLedgerAccentColors = LocalVipLedgerAccentColors.current

/** V2 shape token 的便捷 Composable 访问器。 */
@Composable
fun vipLedgerShapes(): VipLedgerShapes = LocalVipLedgerShapes.current

/** 全局主题：默认跟随系统深浅色（见 docs/PLAN.md 假设项）。 */
@Composable
fun VipLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VipLedgerTypography,
        content = {
            CompositionLocalProvider(
                LocalVipLedgerAccentColors provides if (darkTheme) DarkAccentColors else LightAccentColors,
                LocalVipLedgerShapes provides LightVipLedgerShapes,
            ) {
                content()
            }
        },
    )
}
