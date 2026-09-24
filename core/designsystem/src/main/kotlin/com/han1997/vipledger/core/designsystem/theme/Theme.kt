package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = SurfaceLight,
    primaryContainer = BrandGreenLight,
    onPrimaryContainer = BrandGreenDark,
    surface = SurfaceLight,
    background = SurfaceLight,
    outline = NeutralOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandGreenLight,
    onPrimary = BrandGreenDark,
    primaryContainer = BrandGreenDark,
    onPrimaryContainer = BrandGreenLight,
    surface = SurfaceDark,
    background = SurfaceDark,
    outline = NeutralOutline,
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
