package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 「墨绿金 · 静奢记账」配色（原型 v2.1 已确认基准，docs/ui-prototype/style.css `:root`）。
 *
 * 本轮锁定浅色（任务决策 D4）：不提供深色变体；暗色模式待后续单独出原型再实现。
 * 语义色取值以基准确认后的原型为准（支出赭红 / 收入松绿 / 转账靛蓝），不做 alpha 变体。
 */

// ── 墨绿主色 ───────────────────────────────────────────────────────────────

/** 墨绿主色 `--accent #1A3C34`：primary、标题、选中态。 */
internal val InkGreen = Color(0xFF1A3C34)

/** 深墨绿 `--accent-deep #0F2A24`：hero 渐变暗端、on 色的深档。 */
internal val InkGreenDeep = Color(0xFF0F2A24)

/** 亮一档的墨绿 `--accent-2 #24504A`：hero 渐变亮端、次级容器。 */
internal val InkGreenSoft = Color(0xFF24504A)

/** 墨绿容器上的内容色 `--accent-text #F0E7D6`（象牙金白）。 */
internal val OnInkGreen = Color(0xFFF0E7D6)

/** 墨绿的极浅底 `--tint #E8EEE9`：primaryContainer、选中浅底。 */
internal val InkGreenTint = Color(0xFFE8EEE9)

// ── 黄铜金点缀 ─────────────────────────────────────────────────────────────

/** 黄铜金 `--gold #B08D57`：点睛、待确认草稿的金色线索。 */
internal val BrassGold = Color(0xFFB08D57)

/** 亮黄铜金 `--gold-2 #C9A96E`：深墨绿容器上的金色细节。 */
internal val BrassGoldLight = Color(0xFFC9A96E)

/** 金色浅底 `--gold-bg #F0E7D6`：金色图标底、徽标底。 */
internal val GoldWash = Color(0xFFF0E7D6)

/** 金色上的深文字 `--gold-ink #8A6B34`。 */
internal val GoldInk = Color(0xFF8A6B34)

// ── 纸感底与文字 ───────────────────────────────────────────────────────────

/** 纸感页面底 `--bg #F7F5F0`。 */
internal val PageLight = Color(0xFFF7F5F0)

/** 卡片面 `--surface #FFFFFF`。 */
internal val SurfaceLight = Color(0xFFFFFFFF)

/** 暖灰纸底 `--soft #F0EDE4`：分段控件底、次级容器。 */
internal val SoftLight = Color(0xFFF0EDE4)

/** 主文字 `--text #16211D`。 */
internal val InkLight = Color(0xFF16211D)

/** 次要文字 `--muted #7C7767`。 */
internal val InkMutedLight = Color(0xFF7C7767)

/** 弱化文字 `--faint #A9A499`：eyeline、辅助说明。 */
internal val InkFaintLight = Color(0xFFA9A499)

/** 发丝线 `--line #E6E1D6`：卡片 1px 边框、分隔线。 */
internal val Hairline = Color(0xFFE6E1D6)

/** 深一档发丝线 `--line-2 #D9D2C2`：chip 描边、outline。 */
internal val HairlineStrong = Color(0xFFD9D2C2)

// ── 语义色（原型 v2.1 基准取值） ────────────────────────────────────────────

/** 支出赭红 `--expense #B23A2E`。 */
val ExpenseRed = Color(0xFFB23A2E)

/** 收入松绿 `--income #2E7D5B`。 */
val IncomeGreen = Color(0xFF2E7D5B)

/** 转账靛蓝 `--transfer #3B5B7A`。 */
val TransferBlue = Color(0xFF3B5B7A)

/** 警示金 `--warn #B8860B`：预算超支等提醒。 */
val WarnGold = Color(0xFFB8860B)

// ── hero 深底上的分栏数值色（原型 assetsView 内联色） ─────────────────────

/** 墨绿 hero 上「总资产」的浅松绿 `#8FD9B4`。 */
val HeroAssetTint = Color(0xFF8FD9B4)

/** 墨绿 hero 上「总负债」的浅赭红 `#F0A99B`。 */
val HeroDebtTint = Color(0xFFF0A99B)
