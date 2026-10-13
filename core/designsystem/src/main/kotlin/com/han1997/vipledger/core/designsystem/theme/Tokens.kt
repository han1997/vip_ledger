package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * VipLedger 设计 token：间距、圆角、边框、elevation、图标尺寸与动效时长。
 *
 * 这是「设计系统」的最底层原子值：组件一律引用这里的 token，不写散落的魔法数字。
 * 间距只允许 4 的倍数（4/8/12/16/20/24/32），保证全应用节奏一致。
 * 深浅色不改变这些几何值；颜色语义见 [VipLedgerThemeDefaults] 与 ColorScheme。
 */
@Immutable
object VipLedgerTokens {

    // ── 间距（4dp 步进，组件内 padding / 元素间距统一从这里取） ──────────────

    val Space1: Dp = 4.dp    // 紧凑元素内的小间隙
    val Space2: Dp = 8.dp    // 相邻元素最小间距
    val Space3: Dp = 12.dp   // 默认元素间距
    val Space4: Dp = 16.dp   // 标准屏幕边距 / 卡片内边距
    val Space5: Dp = 20.dp   // hero 卡片内边距
    val Space6: Dp = 24.dp   // 分区间距
    val Space8: Dp = 32.dp   // 大区块间距

    /** 间距按 4dp 步进扩展（仅当标准档位确实不够时使用）。 */
    fun spacing(step: Int): Dp = (step * 4).dp

    // ── 圆角 ────────────────────────────────────────────────────────────────

    val RadiusSmall: Dp = 8.dp     // 小 chip / badge / 输入框
    val RadiusMedium: Dp = 12.dp   // 按钮 / 分段控件
    val RadiusCard: Dp = 16.dp     // 卡片 / hero / 大容器（原型 --radius 16）
    val RadiusLarge: Dp = 20.dp    // 对话框 / 超大容器
    val RadiusFull: Dp = 999.dp    // 胶囊 / 圆形头像

    // ── 边框宽度 ─────────────────────────────────────────────────────────────

    val BorderThin: Dp = 1.dp       // 常规 outline / 分隔
    val BorderMedium: Dp = 1.5.dp   // 强调边框 / 聚焦环

    // ── Elevation 层级 ───────────────────────────────────────────────────────

    val ElevationLevel0: Dp = 0.dp    // 平铺在 surface 上
    val ElevationLevel1: Dp = 1.dp    // 静置卡片
    val ElevationLevel2: Dp = 3.dp    // 抬起卡片 / 菜单
    val ElevationLevel3: Dp = 6.dp    // FAB / snackbar
    val ElevationLevel4: Dp = 8.dp    // 底部弹层
    val ElevationLevel5: Dp = 12.dp   // 对话框

    // ── 图标尺寸 ─────────────────────────────────────────────────────────────

    val IconSmall: Dp = 16.dp     // 行内小图标
    val IconMedium: Dp = 24.dp    // 工具栏动作标准尺寸
    val IconLarge: Dp = 32.dp     // 空态图标
    val IconHero: Dp = 40.dp      // hero / 快捷入口大图标

    // ── 触摸目标 ─────────────────────────────────────────────────────────────

    /** 无障碍最小触摸目标（Android 指南 48dp）。 */
    val TouchTargetMin: Dp = 48.dp

    // ── 动效时长（毫秒；微动效一律从这里取，不超 300ms） ────────────────────

    const val DurationShort: Int = 120      // 按压 / chip 切换反馈
    const val DurationMedium: Int = 220     // 展开 / 收起
    const val DurationLong: Int = 300       // 页面切换上限

    // ── 透明度（「活泼品牌化」的轻量装饰统一走语义 alpha） ──────────────────

    const val AlphaDisabled: Float = 0.38f      // 禁用内容
    const val AlphaSubtle: Float = 0.72f        // 次要文字（在彩色容器上）
    const val AlphaDecoration: Float = 0.12f    // 图标底 / 渐变叠层
    const val AlphaHairline: Float = 0.35f      // 空态灰环等极浅描边
}
