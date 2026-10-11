# P7 报表

Status: delivered (asset history trends + export naming); no emulator walkthrough (user decision 2026-10-11).

日/月/年/自定义；收支趋势、两级分类、标签账户排行、资产/负债/净资产趋势。图表排行同一数据，下钻传完整条件；多标签声明重复归属，负分组用带符号列表。资产从基线开始、改删补记重算，基准日前补记作为基准修正。CSV/PNG带账本/区间/口径，保留组合搜索。

## 本轮交付

### 资产/负债/净资产历史趋势（核心新增）
- `AssetHistoryCalculator`（core:domain 纯函数）：基线重放已确认账目逐日现算，无新表；账户在自身基线前不参与，曲线起点 = max(区间起点, 最早基线日)；无基线 → 空曲线。
- **基准修正**：基准日（含）前补记/改动的账目（`createdAt/updatedAt > capturedAt`）计入有效基线而非重放；基线前已存在的账目视为已含在快照内。
- **守恒式**：曲线终点 == 资产页口径（initial + 全部有效账目净额），单测钉住。
- 测试：基线重放/跨账户转账与资产负债拆分/基准修正/草稿与已删除不参与/无基线排除/区间裁剪/守恒式，10 例。

### 折线图组件
- `LineChart`（core:designsystem，charts.md 契约）：多序列 + 零线参考 + 选中列引导；几何纯函数入 `ChartGeometry`（lineYRange/lineX/lineYFraction/zeroLineYFraction/guideIndex）+ 6 例单测；颜色由调用方传入。

### 趋势页与入口
- 资产页「资产趋势」ActionRow → 独立二级页：近 30 天/90 天/今年/全部四档区间、点选曲线显示当日资产/负债/净资产、CSV 导出（逐日 + 首行口径注释）。
- 数据与资产页同源（`ObserveAssetsUseCase` + 全量账目），区间切换即时重算。

### 导出命名带账本/区间/口径
- `ExportNaming` 下沉 core:designsystem：`suggestedFileName(prefix, ledger, range, date, ext)` + `rangeLabelOf`；统计页 CSV/PNG、搜索、下钻四处同步。

### 验证
- 全门禁 test/assembleDebug/assembleRelease/lintDebug 通过；未做模拟器/真机走查（2026-10-11 起按用户决定不再做模拟器验证，真机验收由用户操作时补）。

See docs/FEATURE-ROADMAP.md for shared contracts, acceptance and delivery requirements.

## What I already know（现状研究）

### 已具备（P1–P6 交付，本批不重写）
- 统计页：区间预设（本月默认/今年/去年/自定义）、DAY/WEEK/MONTH 粒度、四维度（分类/标签/账户/时间）、收支口径切换、两级分类（`CategoryStat.children`，父含子）、图表与排行同源 `statsRowsOf`、下钻传完整条件（`DrillArgs`）、多标签各计一次全额（`TagStat` 契约）、负分组带符号柱（`ChartGeometry.signedBars`）、CSV + PNG 导出、组合搜索（`SearchScreen`）保留。
- 资产基线（P2 schema4）：`asset_baseline` 每账户实况快照（`capturedAt` + `balance` + `included`）；spec 明确「P7 之前不承诺历史曲线」。
- 资产口径：同一纳入未归档账户集合，正余额=资产、负余额绝对值=负债、净资产=两者之差（`AssetsReport`）。

### P7 缺口
1. **资产/负债/净资产趋势**：无历史计算、无折线图组件（designsystem 只有 BarChart/PieChart）。
   - 需要纯函数 `AssetHistoryCalculator`（core:domain）：基线 + 账户 + 有效账目 → 逐日资产/负债/净资产序列；每次现算即「改删补记重算」。
   - **基准修正**：基准日后补记基准日前的账目 → 基线有效值 = 快照余额 − 补记账目的净影响（基线是实况快照，补记改变「基线时刻应有余额」）。
   - 需要新 `LineChart` 组件（core:designsystem，按 charts.md 几何契约：纯函数几何 + 单测 + 深浅色/无障碍）。
2. **CSV/PNG 文件名带账本/区间/口径**：现状 `vipledger-<prefix>-<date>`，prefix 为固定常量，缺账本与区间标识（`ExportNaming.kt` 小改 + 调用点）。
3. 收支趋势/两级分类/排行/下钻/搜索均已交付，验收走查确认即可。

## Decisions

- **资产趋势落点**（用户确认）：资产页净资产汇总旁加「趋势」入口 → 独立二级趋势页（三条曲线 + 区间切换 + 导出）；统计页保持纯收支口径。

## Open Questions

- [ ] 资产趋势的落点与形态（统计页区 / 资产页卡 + 独立二级页）？
- [ ] 曲线内容与区间交互（三条线、粒度、是否复用统计区间选择器、CSV 导出）？
