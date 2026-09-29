package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/** 品牌主色与语义色。支出/收入/转账各自独立颜色，统计图表复用。 */
internal val BrandGreen = Color(0xFF1B6B4A)
internal val BrandGreenLight = Color(0xFFB7E4CB)
internal val BrandGreenDark = Color(0xFF0E3D2C)
internal val BrandGreenMuted = Color(0xFFE7F5EE)

internal val PageLight = Color(0xFFF4F7F5)
internal val SurfaceLight = Color(0xFFFFFFFF)
internal val SurfaceDark = Color(0xFF141816)
internal val PageDark = Color(0xFF101412)

internal val InkLight = Color(0xFF1C2420)
internal val InkMutedLight = Color(0xFF5E6B64)
internal val InkDark = Color(0xFFE8F0EB)
internal val InkMutedDark = Color(0xFFA8B6AE)

internal val NeutralOutline = Color(0xFFC5D0C9)

/** 语义色：支出红、收入绿、转账蓝。V2 起保持原值不变（对比度已在 V1 验收过）。 */
val ExpenseRed = Color(0xFFC24B3A)
val IncomeGreen = Color(0xFF1F8A4C)
val TransferBlue = Color(0xFF2F6F96)

// ── 「活泼品牌化」强调色（V2 新增） ─────────────────────────────────────────
//
// 用途限定在 hero 渐变、快捷操作点睛、品牌装饰等**少量高强调位置**；
// 语义色（上方的 ExpenseRed/IncomeGreen/TransferBlue）与图表色板不变。
// 每个强调色都成对给出 light/dark 两组取值，保证两种模式下正文对比度可读：
// 「On*」是落在对应强调色上的内容色（≥ 4.5:1）。

/** 品牌亮绿：hero 渐变的亮端、快捷入口选中态。 */
internal val AccentMintLight = Color(0xFF17A672)
internal val AccentMintDark = Color(0xFF5ED5A5)

/** 珊瑚橙：待确认/提醒类「温和需要行动」的点睛。 */
internal val AccentCoralLight = Color(0xFFE8703A)
internal val AccentCoralDark = Color(0xFFFFAB7A)

/** 琥珀金：徽标、积分、提醒高亮的点缀。 */
internal val AccentAmberLight = Color(0xFFC08A1E)
internal val AccentAmberDark = Color(0xFFF2C14E)

/** 覆盖在品牌亮绿/渐变容器上的前景色（浅色模式用深墨绿、深色模式用近白）。 */
internal val OnAccentLight = Color(0xFF08301F)
internal val OnAccentDark = Color(0xFF062B1C)
