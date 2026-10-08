# 优化界面与操作体验

## Goal

优化软件的界面和操作逻辑，让软件更好看、更易用；同时保证已有账务功能、数据安全和业务规则不退化。

## What I already know

- 用户希望同时改善视觉界面和操作体验，而非仅替换配色。
- 上一项 V2 UI 设计系统任务已归档，当前工作区起始状态干净。
- 本项目采用 Trellis 流程，主要规范层为 Android。
- 本项目是 Kotlin + Jetpack Compose + Material 3 多模块记账应用，已完成全页面设计系统迁移；本次要避免重复做一轮仅换组件的迁移。
- 已核实当前 `Color.kt`/`Theme.kt`：品牌绿、浅绿中性底色、跟随系统深浅色、收入/支出/转账语义色、共享圆角与有限的 hero 渐变均已存在。
- 历史任务曾采用“活泼品牌化”方向；本轮已获用户授权重新选择视觉方向，历史风格仅作为现状参考。

### 现状审计（Auto-Context，2026-10-07）

**应用结构**（4 个 feature 模块 + app 壳）：

- `:feature:ledger` — 首页 Home、交易列表/编辑 TxnList/TxnEdit、账本/账户/分类/标签管理 Manage×4、设置 Settings、周期记账 RecurringList/RecurringEdit、草稿 DraftList、分期 LoanList/LoanDetail/LoanEdit
- `:feature:stats` — 搜索 Search、统计 Stats（图表+排行+导出）、下钻 Drill
- `:feature:ai` — 自然语言记账 AiEntry、模型管理 ModelManage、商户规则 MerchantRuleManage
- `:feature:sync` — 备份/导入导出 Backup

**导航架构现状**：单一 NavHost，`LedgerRoutes.HOME` 为起点，无底部 Tab。首页顶栏只有「账本切换 + 设置」两个入口；统计、备份、AI、账户、分类、标签、周期、草稿、分期**全部**收在 SettingsScreen 的分组列表里（spec: `android/ui.md` 设置页节）。这意味着高频功能（查统计、记账列表）需要 2 跳才能到达。

**设计系统现状**（V2 已完成）：

- Token 层完整：`VipLedgerTokens`（Space1-8 4dp 网格、Radius、Elevation、Duration、Alpha、TouchTargetMin）
- 组件层完整：GroupCard/ActionRow/SectionHeader/StatusBadge/VipLedgerButton/ConfirmDialog/SnackbarHost/LoadingState/HeroCard/EmptyState/AmountText/TxnLine/LabeledTextField + Pie/BarChart
- 色彩：品牌绿 `#1B6B4A` + 浅绿中性底 `#F4F7F5`；强调色 mint/coral/amber 成对 light/dark；语义色支出红/收入绿/转账蓝已固化
- 约束：强调色是「有限装饰资源」，不进语义色和图表色板

**规范硬约束**（不可违反）：

- Screen/ViewModel/UiState/Action 分层；UiState 不放中文文案和 Context
- 一屏一个 LazyColumn；文案进 strings.xml；金额 `Money.toDisplayString()` 等宽右对齐
- 共享组件必须先查 designsystem，不允许 feature 里重写同类视觉模式
- 统计口径：默认本月、`TxnDayGrouper`/`StatsCalculator.isCounted` 单一实现
- 每个 Screen 配 Preview；预览不依赖 Hilt

## Assumptions (temporary)

- 沿用现有 Kotlin/Compose 技术栈、模块边界及可复用基础，不因全面重设计而重写领域与存储层。
- 保留已有数据和账务业务规则，不默认引入新业务功能。

## Confirmed Decisions

- **优化深度：全面重新设计（用户已确认）**。视觉风格、主导航、页面布局和操作方式均允许重新考虑，而非仅在 V2 样式上保守打磨。
- 全面重设计仍保留已有功能与数据；允许改变界面和使用路径，不允许因此丢失既有能力。
- 上一任务绿色品牌只作为现状参考，本次用户已授权重新选择视觉方向，不再将其视为不可变边界。

### Decision 1（ADR-lite）：视觉方向 = 活力效率

**Context**：用户确认要全面重设计；需在「极简金融」「活力效率」「内容优先」三个候选方向中做选择。

**Decision**：选择 **B - 活力效率**（2026-10-07 确认）。以品牌绿为主色调，在卡片、图标背景、快捷操作上大胆使用；保留现有 hero 渐变和 Accent Mint/Coral/Amber 强调色体系，但重新梳理使用场景——高频操作更醒目、装饰更聚焦。

**Consequences**：与已有 V2 设计系统资产衔接最多（复用 token + 组件，升级而非重写）；视觉识别度强。风险是强调色使用边界要重新定义清楚，避免「处处强调 = 处处不强调」；深色模式对比度需逐处核对。

**Design Implications**：
- 品牌绿从「有限装饰」升级为「主色调」：更多 surface 允许绿色容器（卡片头、图标底、选中态）
- Accent 三色保留但用途收窄：coral=待行动提醒、amber=积分/徽章、mint=选中/激活态
- Hero 渐变保留并强化，可扩展到更多页面头部
- 组件库不需要推翻，重点在「使用密度」和「组合方式」的重构

### Decision 2（ADR-lite）：导航架构 = 底部导航栏（Home / 统计）+ AI 集成进记账

**Context**：用户高频操作是记账和查统计；当前所有次级功能都在 Settings 里，导致「查统计」需要 2 跳；AI 功能不需要独立 Tab，应集成进记账流程。

**Decision**：采用底部导航栏（2026-10-07 确认）：

- **3 个 Tab**：首页（Home）| 统计（Stats）| 设置/更多（More）
- AI 不占 Tab，入口在**记账页内**（TxnEditScreen 提供 AI 自然语言输入切换）
- FAB 保持「记账」核心功能，可在记账页内切换「手动填表」/「AI 输入」两种模式
- 首页「QuickEntries」区重新定义内容（见下一步待确认）

**Consequences**：首页定位从「工具总览」降为「今日概览 + 快速入口」；统计页升级为独立 Tab 可一屏到达；导航层级从 2 跳压到 1 跳。AI 集成记账意味着不需要单独记 AI 账，流程更自然。

**Navigation Map（目标态）**：

```
Bottom Nav:
  [首页] → HomeScreen（今日概览 + QuickEntries）
  [统计] → StatsScreen（筛选 + 图表 + 排行 + 导出）
  [我的] → SettingsScreen 分组入口（账户/分类/标签/账本/备份等）

FAB（首页/统计 TAB 上均显示）：
  [+] → TxnEditScreen（手动 + AI 双模式）
```

### Decision 3（ADR-lite）：首页结构 = 干净概览，去掉快捷入口卡

**Context**：用户确认周期/草稿/分期是低频操作，不配占据首页黄金位置；且「统计」升级为 Tab 后，首页不再是功能总览。

**Decision**：方案 B（2026-10-07 确认）——首页 QuickEntries 三个卡片移除。首页结构收敛为：

1. HeroCard（净资产 / 总负债 / 今日支出）
2. MonthCard（本月收支结余）
3. ReminderCard（仅在有提醒时显示；分期/草稿 badge 挪到「我的」Tab 或对应页面入口）
4. 近期账目列表（保留「查看全部」）

首页职责 = 「**看一眼现在的财务状态 + 最近记了什么**」。记账靠 FAB，查数据靠统计 Tab，管理功能靠「我的」Tab。

**Consequences**：首页更干净，信息密度降低但关键信息（净资产/本月/今日/最近账目/提醒）一屏可见。周期/草稿/分期入口**不能丢**——必须迁移到「我的」Tab 或对应功能页面（见 Decision 2 待确认 #3）。

**注意事项**：
- 草稿数 badge（pendingDraftCount）目前显示在 QuickEntry 上，移除后需要有其他呈现方式（如「我的」Tab 入口加 badge，或并入 ReminderCard）
- QuickEntry/QuickEntries 组件若不再被首页使用，需判断是否删除或保留给其他页面复用

### Decision 4（ADR-lite）：FAB 保持单入口，全局参考「一木记账」

**Context**：速度拨号（长按展开支出/收入/转账）能省一步类型选择，但增加手势学习成本；需要与同类标杆产品对照。

**Decision**：方案 A（2026-10-07 确认）——FAB 保持单入口，点击直接进 TxnEditScreen，在表单内选类型。不做速度拨号。

**Consequences**：交互路径简单无歧义；记账效率优化转向记账页本身（分类选择器、金额键盘、类型切换的布局——这是用户指定的重点打磨对象）。

**设计基准**：用户点名参考「**一木记账**」。调研产出见 [`research/yimu-ux-reference.md`](research/yimu-ux-reference.md)（网站无法抓取，以用户口述交互细节为准）。记账页（TxnEditScreen）的重设计以此为主要对标。

### Decision 5（ADR-lite）：「我的」Tab = 现设置页重组（统计移出、AI 换为配置）

**Context**：底部导航三分后，「我的」Tab 承接原 SettingsScreen；统计与 AI 的入口去向需要重新安排。此结构可从现状直接推导（原三组 + 移除项 + 新增项），无需用户另行选择。

**Decision**（2026-10-07）：

| 分组 | 入口 | 与现状差异 |
|---|---|---|
| **账本** | 账本管理 / 账户管理 / 分类管理 / 标签管理 | 不变 |
| **计划** | 周期记账 / 待确认草稿（挂 badge）/ 分期 | 承接首页 QuickEntries 移出的入口 |
| **AI** | 模型管理 / 商户规则 | 原独立「AI 记账」入口移除（已集成进记账页），改为 AI 配置两组 |
| **数据** | 备份 / 导入导出 | 原「工具」组去掉统计后的剩余项 |

- 「统计」不再出现在「我的」里（独立 Tab）
- 无账本时依赖账本的入口保持禁用（现有 ledgerReady 逻辑不变）
- SettingsScreen 升级为 Tab 级页面后，不再需要返回箭头（改为标题栏）

### Decision 6（ADR-lite）：AI 入口 = 金额框左侧图标切换

**Context**：记账页内 AI 输入模式需要入口，但不想增加额外的 ModeToggle 行占用垂直空间。

**Decision**：方案 B（2026-10-07 确认）——在金额输入框左侧放置一个 AI 图标按钮（灯泡/魔杖），点击切换 AI 输入模式。普通点击进入手动模式。

**AI 模式行为**：
- 全屏文字输入框，键盘弹出，用户输入自然语言描述
- 解析后下方显示预览卡片（类型+金额+分类+账户），用户确认后保存
- 有歧义时显示修正提示

**技术路径**：TxnEditViewModel 新增 `aiMode: Boolean` 状态；复用 `core.domain.nlp.NlEntryPipeline` + `ConfirmAiEntryUseCase`（已在 `core:domain`，无需跨 feature 依赖）。

### Decision 7（ADR-lite）：记账页（TxnEditScreen）效率重设计

**Context**：以一木记账为基准；其记账效率核心在于：打开 app → 1 下到达记账页 → 输入金额 → 选分类（类型→一级→二级）。

**Decision**（2026-10-07）——记账页优化方向：

1. **类型顺序优化**：类型 FilterChip 行按使用频率重排（支出→收入→转账→债务→调整），减少默认高频操作的眼动距离；5 个类型 Chip 等宽排列（`weight(1f)`），避免换行参差
2. **金额输入优先且突出**：金额输入框字号加大、加品牌绿边框，键盘 decimal only，进入记账页光标默认在金额框；金额框左侧放 AI 切换图标（Decision 6）
3. **分类层级视觉分层**：父分类和二级分类之间加 `SectionLabel` 分隔行，父分类点选后才展开子分类（`animateVisibility`），避免两层级同时平铺造成视觉堆叠
4. **记账键盘体验**（参考一木记账的「打开即记账」）：金额框获得焦点时键盘直接弹出，不需要先点一下；日期默认当天，不需要每次选
5. **账户选择默认上次账户**：TxnEditViewModel 记住上次选中的 accountId，新建账目时自动回填，减少重复选择

**预期效果**：完成一笔普通支出从打开到保存，从约 8–10 步压到 4–5 步。

## Open Questions

- ~~全面重设计的视觉方向~~ ✅ Decision 1
- ~~全应用信息架构与主要操作路径~~ ✅ Decision 2/3/5
- ~~AI 集成进记账页的具体形态~~ ✅ Decision 6
- ~~记账页效率优化方向~~ ✅ Decision 7
- 统计 Tab 与首页顶栏（spec 规定只留账本切换+设置，需重写该节）——Phase 3.3 spec update 时处理

## Requirements (evolving)

- 全面重设计覆盖现有全应用视觉与信息架构，并重新设计高频操作（记账页为重点），不缩减为只改首页或主题 token。
- 视觉方向「活力效率」：品牌绿升级为主色调、强调色用途收窄、hero 渐变强化（Decision 1）。
- 导航改为底部 3 Tab（首页/统计/我的），FAB 单入口，AI 集成进记账页（Decision 2）。
- 首页收敛为「看一眼财务状态 + 最近账目」，移除快捷入口卡（Decision 3）。
- 审计现有界面、导航和高频业务操作，识别实际体验问题。
- 统一信息层级和视觉细节，改善常用功能的可发现性、操作步骤及反馈。

## Research References

- [`research/yimu-ux-reference.md`](research/yimu-ux-reference.md) — 一木记账交互参考（用户口述 + 官网，核心：入口 1 下达、分类按需展开、类型置顶）

## Acceptance Criteria (evolving)

- [ ] 底部导航栏 3 Tab（首页/统计/我的）可用；FAB 在首页与统计 Tab 均可记账；原 Settings 全部入口在「我的」Tab 内可达，无功能丢失
- [ ] 首页为「净资产 hero + 本月收支 + 提醒（有才显示）+ 近期账目」，周期/草稿/分期入口已迁移到「我的」Tab 且可正常打开
- [ ] 记账页完成效率重设计（Decision 7 五项），AI 模式可从金额框左侧图标进入并完成「输入→解析→预览→保存」闭环
- [ ] 视觉按「活力效率」方向统一：品牌绿主色调贯穿页面，强调色用途符合 Decision 1 边界，深浅两模式文字对比度 ≥4.5:1
- [ ] 主要操作的加载、空态、错误和成功反馈可理解且一致
- [ ] 已有核心功能与账务数据语义不退化（统计口径、备份导入、周期/分期规则不变）
- [ ] `./gradlew.bat test assembleDebug` 与 `./gradlew.bat lintDebug` 通过；API 34 模拟器逐页走查无阻断性回归

## Definition of Done

- 按影响范围补充或更新测试（导航接线、UiState 纯函数、AI 模式切换）。
- 相关编译、lint 和测试通过；如验证受环境限制，明确记录未验证项。
- 行为变化、规范与必要的任务文档同步更新（`.trellis/spec/android/ui.md` 的「设置页」节需随底部导航重写）。
- 对涉及敏感数据或不可逆操作的改动说明保护与回退方式。

## Continuation Record (2026-10-08)

- 修正了记账页 AI 模式入口与解析预览，支持单笔确认入账，并走 `ConfirmAiEntryUseCase` 处理确认与修正；多笔输入会提示拆分输入，确认失败重试会复用待确认记录避免重复创建。
- 将模型管理和商户规则接入「我的」Tab；统计 Tab 提供记账 FAB；上次账户按账本写入 DataStore。
- 已同步 `.trellis/spec/android/ui.md` 与 `architecture.md`。
- `./gradlew.bat test assembleDebug lintDebug` 通过，`git diff --check` 通过。
- 已在 `nordic-ui-api34` 模拟器走查：首页/统计/我的 Tab 切换；统计 FAB；「我的」中账本、账户、分类、标签、周期记账、待确认、分期、模型管理、商户规则、备份 10 个入口均可打开并返回；记账页金额键盘自动弹出、类型切换、AI 输入解析预览，解析完成后键盘收起。首页、统计页、「我的」深色模式显示可读。
- 未点击 AI 确认保存，避免在模拟器种子账本中新增测试账目；因此 AI 持久化写入、统计数字等数据行为未由本轮手工操作验证。手工检查期间没有观察到 `AndroidRuntime` 崩溃。

## Technical Approach

1. **导航层**（`:app` + 各 feature navigation）：`VipLedgerApp` 加底部 NavigationBar（3 Tab），Home/Stats/Settings（我的）三条主路由互不覆盖返回栈；FAB 挂在 Home 与 Stats 两个 Tab 的 Scaffold 上
2. **首页瘦身**：`HomeScreen` 删 QuickEntries；草稿 badge 逻辑挪至「我的」Tab 入口；`LedgerSwitcher` + 设置图标去留随 Tab 化调整（「我的」变成 Tab 后首页顶栏可只留账本切换）
3. **记账页重设计**：`TxnEditScreen` 按 Decision 6/7 重构（类型等宽 Chip、金额突出 + AI 图标、分类按需展开、键盘自动弹出、上次账户回填）；`TxnEditViewModel` 注入 AI 管线（`NlEntryPipeline`/`AiSettingsRepository`/`ConfirmAiEntryUseCase`）与「上次账户」记忆
4. **「我的」页**：`SettingsScreen` 改造为 Tab 级页面（去返回箭头），按 Decision 5 重组分组；草稿入口挂 badge
5. **视觉统一**：`VipLedgerTokens`/`Theme` 按活力效率方向微调 token 用法（不推翻组件库）；逐页核对强调色边界与深色对比度
6. **每批次可构建回退**：导航改造 → 首页/我的 → 记账页 → 全局视觉，四批推进，每批跑 `test assembleDebug lintDebug`

## Out of Scope (provisional)

- 未经确认的新业务功能、云同步、后端服务和数据库重构。
- `:feature:ai` 独立 AiEntryScreen 的保留与否以实现期实测为准：若 AI 已完全集成记账页，该页面可下线，但模型管理/商户规则页面保留（「我的」Tab 继续使用）。

## Technical Notes

- 当前任务：`.trellis/tasks/09-29-ui-ux-polish/`。
- NLP 管线位于 `core:domain`（`NlEntryPipeline`/`LlmParserClient`/`ConfirmAiEntryUseCase`），`:feature:ledger` 已依赖 `core:domain`，AI 集成无新模块依赖。
- AiEntryViewModel 的 AI 装配方式（pipeline 构建在 VM 内、`ConfirmAiEntryUseCase` 同事务处理修正）可直接参考迁移。
- spec 约束提醒：UiState 不放中文文案；一屏一个 LazyColumn；金额 Long 分跨 lambda；导航参数缺失用 `.orEmpty()` 归一（见 `.trellis/spec/android/ui.md` 已踩过的坑）。
