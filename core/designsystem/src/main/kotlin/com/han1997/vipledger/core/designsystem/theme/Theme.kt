package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

/** 全局主题：默认跟随系统深浅色（见 docs/PLAN.md 假设项）。 */
@Composable
fun VipLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = VipLedgerTypography,
        content = content,
    )
}
