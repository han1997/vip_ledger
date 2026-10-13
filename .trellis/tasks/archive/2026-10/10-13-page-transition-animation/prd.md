# 页面切换动画

## Goal

为应用的 Compose 导航补上页面切换过渡:次级页的前推/返回要有明确的方向感,
底部 Tab 之间切换要保持平级语义(不暗示层级),同时不破坏既有的状态保持
(`saveState`/`restoreState`)、返回语义与无障碍偏好。

动效参数先在原型侧以 CSS 表达并经用户确认(见 D1),再把参数搬进 Compose 做真机验收。

## Requirements

### R1 次级页过渡(前推 / 返回)

- 前推(`enterTransition`):新页从右侧滑入 + 淡入,**300ms**,位移 `it/6`(约 16.7% 屏宽)。
- 前推时旧页(`exitTransition`):向左轻移 + 淡出,**300ms**,位移 `-it/6`。
- 返回(`popEnterTransition`):被恢复页从左侧滑回 + 淡入,**250ms**,位移 `-it/6`。
- 返回时被弹出页(`popExitTransition`):向右退回 + 淡出,**250ms**,位移 `it/6`。
- 废弃现有实现中的 `90ms / it/50`(与其余三个严重不匹配,判为调试残留值)。

### R2 Tab 过渡(平级语义)

- 四个 Tab 根之间切换(`ledger/home`、`ledger/calendar`、`ledger/assets`、
  `ledger/settings-tab`、`stats` 的 `STATS_TAB`)**只做淡入淡出,不加任何水平位移**。
- 时长取 `VipLedgerTokens.DurationMedium`(220ms)。
- 必须保持既有的 `popUpTo(findStartDestination) { saveState }` + `launchSingleTop` +
  `restoreState` 语义:**切回已访问的 Tab 时滚动位置/输入状态不丢失**。

### R3 无障碍降级

- 系统「设置 → 无障碍 → 颜色和动态效果 → 移除动画」开启时,
  页面过渡**降级为纯淡入淡出或瞬切**,不再播放水平滑动。
- 具体检测方式待 research 结论确认(见 Research References)。

### R4 动效参数走设计令牌

- 过渡时长**不得在 `:app` 或 feature 里写字面量**(`ui-contract.md` 契约 #4)。
- 现有令牌:`DurationShort=120` / `DurationMedium=220` / `DurationLong=300`
  (`core/designsystem/.../theme/Tokens.kt:66-68`),注释已注明
  `DurationLong` 为「页面切换上限」,且 `TokensTest` 断言 `DurationLong <= 300`。
- 本任务新增的唯一数值是返回用的 **250ms**;需在 `VipLedgerTokens` 中新增常量,
  并同步扩展 `TokensTest` 的单调性断言,不破坏既有约束。

### R5 不引入新依赖

- 仅使用已有的 `androidx.compose.animation`(随 Compose BOM 2026.06.01)
  与 Navigation Compose 2.9.5 的内置过渡能力。

## Acceptance Criteria

- [ ] 次级页前推:新页自右滑入 + 淡入,旧页向左轻移 + 淡出,300ms,无闪跳。
- [ ] 返回:方向与前推严格相反,250ms,无闪跳。
- [ ] Tab 切换:**不出现任何横向滑动**,仅淡入淡出,220ms。
- [ ] 切回已访问 Tab,滚动位置/输入状态保持;返回栈语义不变。
- [ ] 系统「移除动画」开启时,水平位移消失(降级为淡入淡出或瞬切)。
- [ ] Android 14+ 预测性返回手势下无动画撕裂/错位。
- [ ] 明暗两套主题逐屏核对过渡无残影。
- [ ] 真机走查记录 + 截图证据入任务归档(`ui-contract.md` 契约 #5)。

## Definition of Done

- 原型侧:v2.3 动效版本已登记 `BASELINE.md`,状态经用户确认;三项源文件 SHA-256 与
  文件实际哈希一致(契约 #1 的验证方式)。
- 实现侧:编译通过;`trellis-check` 质量检查通过。
- `ui-contract.md`「交付前快速清单」逐项过一遍,偏差项在任务里写明。
- 版本按根 `version.properties` 唯一来源 bump 一次,`CHANGELOG.md` 同步。
- 真机 ADB 覆盖安装核验(保留应用数据),安装后核对结果。

## Technical Approach

### 导航装配现状

- 唯一装配点是根 `NavHost`:`app/src/main/kotlin/com/han1997/vipledger/ui/VipLedgerApp.kt:98`。
- 20 个 destination 分散在四个 feature 模块的 graph 里
  (`feature/ledger/.../navigation/LedgerRoutes.kt:106` 起,另含 stats / ai / sync),
  当前**没有任何 destination 覆写过渡**。
- 因此现有全局过渡会**同时作用于 Tab 根**,这正是 R2 要修正的点。

### 待定项(依赖 research 结论)

分派方式二选一,由 research 结论决定:

- **方案 A(倾向)**:只在根 `NavHost` 的四个过渡 lambda 里,按
  `initialState.destination.route` / `targetState.destination.route` 判定是否为 Tab 根,
  集中分派。改动点单一,20 个 destination 无需逐个改。
- **方案 B**:每个 destination 覆写(Tab 根显式覆写,其余继承 `NavHost` 默认)。
  语义最明确,但改动散落在四个 feature 模块。

## Decision (ADR-lite)

### D1:动效先落原型 v2.3,再实现 Compose(2026-10-13,用户选定)

**Context**:页面切换动效在原型 v2.2 中未定义,而契约 #1 要求 UI 基于已确认原型,
契约 #6 又允许平台行为优先于原型视觉。动效兼具「视觉观感」与「导航语义」双重属性。

**Decision**:A 案——在原型 `docs/ui-prototype/` 中实现两种过渡的 CSS 表达,
登记为 **v2.3(待用户确认)**;用户视觉确认后,再把参数搬进 Compose 并做真机验收。

**Consequences**:
- ✅ 动效参数可在浏览器中视觉评审,不必反复装机调参;与其他设计同源可回看。
- ✅ 满足契约 #5「归档材料能回答基于哪个原型版本、谁确认的」。
- ⚠️ 多一轮「原型补 → 用户确认」的往返;确认前不得写实现。
- ⚠️ 原型是手机壳内的 overlay 模拟,与 Compose 的 `NavHost` 过渡机制并非一一对应;
  原型只能表达「目标观感」,时长/位移仍需在真机上按平台手感微调。

### D2:Tab 切换仅淡入淡出(2026-10-13,用户选定)

**Context**:四个 Tab 是平级关系;横向滑动会暗示不存在的层级。

**Decision**:Tab 之间切换只做透明度过渡,时长 220ms;方向感全部交给次级页过渡。

**Consequences**:平级语义清晰;代价是 Tab 切换观感比滑动更「轻」,不强调位置关系。

### D3:返回略快于前推(2026-10-13,用户选定)

**Context**:现有实现的 `popExitTransition` 为 90ms + `it/50`(仅 2% 屏宽),
与其余三个过渡的 300ms + `it/6` 严重不匹配。

**Decision**:返回整体 **250ms**(位移仍 `it/6`),前推保持 **300ms**。
手感上「退得略快」,与 Material 3 前进/后退时长惯例一致。

**Consequences**:需在 `VipLedgerTokens` 新增 250ms 常量(见 R4);
废弃 90ms 值需在验收时说明。

### D4:无障碍跟随系统开关(2026-10-13,用户选定)

**Context**:原型侧已有 `@media(prefers-reduced-motion:reduce)` 全局兜底
(`style.css:426`),实现侧目前无对应处理;契约 #6 要求无障碍优先。

**Decision**:跟随系统「移除动画」开关,开启时页面过渡降级为纯淡入淡出或瞬切。

**Consequences**:无障碍达标;`MotionDurationScale` 相关 API 可能有实验性标注,
需在 research 中确认稳定性,避免引入 `@Experimental` 依赖。

### D5:构建改动拆出本任务(2026-10-13,用户选定)

**Context**:同工作区 `app/build.gradle.kts` 存在未提交的 ABI 拆包
(`splits.abi`)+ APK 命名改动,与页面切换动画无逻辑关联,且会改变
APK 产物命名(与「安装前按版本定位最新 Release」的约定相关)。

**Decision**:拆开独立处理,页面切换动画只改 `VipLedgerApp.kt` 与原型;
两个交付物各自 bump 版本。

**Consequences**:本任务 diff 干净、可单独回滚;构建改动需另开任务处理。

## Out of Scope

- 页面**内部**的组件级动效(列表进入、卡片展开、骨架屏等)——非页面切换。
- 手势驱动的交互式返回(interactive back)自定义拖拽进度条。
- 底部栏自身的显隐动画(`showBottomBar` 目前是硬切)。
- Hero 卡片变形、共享元素过渡(shared element transition)。
- `app/build.gradle.kts` 的 ABI 拆包与 APK 命名改动(见 D5)。

## Technical Notes

### 涉及文件

| 文件 | 角色 |
|---|---|
| `app/src/main/kotlin/com/han1997/vipledger/ui/VipLedgerApp.kt` | 根 `NavHost`,过渡装配点;含 `navigateTab()` 与 Tab 根路由常量 |
| `core/designsystem/.../theme/Tokens.kt` | 动效时长令牌,需新增 250ms 常量 |
| `core/designsystem/src/test/.../TokensTest.kt` | 时长短语的单调性断言,需同步扩展 |
| `docs/ui-prototype/style.css` | 原型动效表达(v2.3) |
| `docs/ui-prototype/app.js` | 原型页面切换逻辑 |
| `docs/ui-prototype/BASELINE.md` | v2.3 版本登记 |

### 关键约束

- **全局过渡会波及 Tab 根**:R2 必须在 destination 级覆写或在 `NavHost` 里按 route 分派;
  仅靠一套全局 lambda 无法区分 Tab 与次级页。
- **契约 #4**:`grep -rn "Color(0x" feature/` 零命中是既有验证方式;
  本任务同理由要求过渡时长不得出现字面量。
- **契约 #6**:平台要求(返回、insets、权限、无障碍)优先于原型视觉;
  D4 的无障碍降级即依此条,不受「原型未覆盖」限制。
- **版本相关**:Navigation Compose `2.9.5` + Compose BOM `2026.06.01` +
  minSdk 26 / targetSdk 36。API 可用性以 research 结论为准。

### 工作区未提交改动

`version.properties` 仍为 `0.1.14 (15)`;`VipLedgerApp.kt`(过渡)与
`app/build.gradle.kts`(ABI 拆包)两项改动均未 bump 版本。按 D5,
本任务只 bump 页面切换动画这一项。

## Research References

- [`research/navhost-transition-api.md`](research/navhost-transition-api.md) — 待补
  (`NavHost` 过渡 lambda 的 route 分派、destination 级覆写的优先级、
  `MotionDurationScale` 无障碍检测、预测性返回协同)