package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.han1997.vipledger.core.model.CategoryKind

/**
 * 图表配色。
 *
 * 前三个是语义色（支出红 / 收入绿 / 转账蓝），后面是区分度足够的辅助色；
 * 屏幕里禁止写死颜色，占比环图统一按下标取色（`chartColorOf(index)`）。
 */
private val ChartPalette: List<Color> = listOf(
    ExpenseRed,
    IncomeGreen,
    TransferBlue,
    Color(0xFFB7791F),
    Color(0xFF805AD5),
    Color(0xFF2C7A7B),
    Color(0xFFB83280),
    Color(0xFF4A5568),
)

fun chartColorOf(index: Int): Color {
    val size = ChartPalette.size
    return ChartPalette[((index % size) + size) % size]
}

/**
 * 占比环图取色：收入口径从收入绿起、支出口径从支出红起。
 *
 * 否则「收入」饼图的第一块是支出红，与 ui.md 的语义色约定（支出红 / 收入绿）冲突，
 * 用户会误读成支出。
 */
fun chartColorOf(index: Int, kind: CategoryKind): Color =
    chartColorOf(index + if (kind == CategoryKind.INCOME) 1 else 0)