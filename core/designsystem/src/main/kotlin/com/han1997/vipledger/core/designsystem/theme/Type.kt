package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.han1997.vipledger.core.designsystem.R

private const val TABULAR_FIGURES = "tnum"

/**
 * 打包的拉丁衬线字体（Noto Serif SC 的 Latin 子集，OFL 授权；任务决策 D5）。
 *
 * 覆盖范围：Basic Latin + `¥ · — …`，专用于**金额与数字展示**（hero 大额、
 * 账户余额、统计展示位）——这些内容全部由拉丁字符构成，观感全设备一致。
 * 中文标题类衬线请用 [serifTitle]（系统 `FontFamily.Serif` 回退，不同设备
 * 可能有细微差异，已确认可接受）。
 *
 * 注意：该字体无 `tnum` 特性（数字为比例宽度），与已确认原型一致；
 * **列表金额保持无衬线 + tnum**（见 `AmountText` 默认样式），只有展示位用衬线。
 */
val VipLedgerSerif = FontFamily(
    Font(R.font.noto_serif_regular, FontWeight.Normal),
    Font(R.font.noto_serif_semibold, FontWeight.SemiBold),
)

/**
 * 字体版式：金额与统计页面依赖稳定的字号层级，避免列表里数字跳动。
 *
 * V3（墨绿金基准）调整：
 * - [displayHero] / [displayAmount] 切换为衬线（原型 Display 44 / 21 serif）；
 * - 新增 [serifTitle]（页面标题，原型 Title 23 serif，中文走系统衬线回退）；
 * - 列表与正文样式保持无衬线 + `tnum` 等宽数字不变。
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

/** 首页/资产 hero 大额金额（原型 Display 44 serif；金额为拉丁字符，衬线全覆盖）。 */
val displayHero: TextStyle = TextStyle(
    fontFamily = VipLedgerSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 34.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = TABULAR_FIGURES,
)

/** 中等规模的金额汇总（月度收支卡等，原型 21 serif）。 */
val displayAmount: TextStyle = TextStyle(
    fontFamily = VipLedgerSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.2).sp,
    fontFeatureSettings = TABULAR_FIGURES,
)

/** 页面标题（原型 header h2：Title 23 serif；中文走系统衬线回退）。 */
val serifTitle: TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 23.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.2).sp,
)

/** 记一笔编辑页金额（原型 meta-amt：31 serif，按类型着色）。 */
val displayEditorAmount: TextStyle = TextStyle(
    fontFamily = VipLedgerSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 31.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.3).sp,
    fontFeatureSettings = TABULAR_FIGURES,
)

/** 金额前的货币符号（原型 meta-amt .cur：约 .55em）。 */
val editorAmountSymbol: TextStyle = TextStyle(
    fontFamily = VipLedgerSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 20.sp,
)

/** 数字键盘按键（原型 .key：21 serif medium）。 */
val keypadKey: TextStyle = TextStyle(
    fontFamily = VipLedgerSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 21.sp,
    lineHeight = 24.sp,
    fontFeatureSettings = TABULAR_FIGURES,
)

/** 分类图标方块的首字（原型 .cat-tile .ci：19 serif semibold）。 */
val serifTileInitial: TextStyle = TextStyle(
    fontFamily = VipLedgerSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 19.sp,
    lineHeight = 22.sp,
)
