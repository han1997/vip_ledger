# V2 UI 设计系统与全局视觉升级

## Goal

V1 已完成 M0–M7，核心记账、统计、备份、AI 与端侧分类器能力可用，但当前 Compose 界面仍偏基础：各页面存在重复的卡片/分组/表单样式，信息层级和交互反馈不够统一，深浅色、无障碍和空/错/加载状态也需要系统化收敛。

本任务是 V2 第一阶段，只聚焦 UI 设计系统与全局视觉升级：在不改变现有业务口径、数据模型和导航能力的前提下，建立一致、可复用、易维护的 Compose 视觉系统，并迁移所有主要功能页，让应用更清晰、更美观、更完整。

## What I already know

- 用户已明确第一阶段优先「UI 设计系统」，暂不优先预算、桌面小组件、应用锁或通知记账。
- 项目是 Kotlin + Jetpack Compose + Material 3 多模块 Android 工程；V1 进度看板 M0–M7 已完成。
- 共享主题与组件位于 `:core:designsystem`；页面位于 `:feature:ledger`、`:feature:stats`、`:feature:ai`、`:feature:sync`。
- 当前主题已有品牌绿、暖色页面背景、深浅色 scheme、自定义 Typography、语义金额色、Scaffold、TopBar、AmountText、TxnLine、EmptyState、表单与图表组件。
- 首页已有净资产/月度收支/快捷入口/提醒/最近账目；设置页是分组导航；统计页有筛选、图表、排行、下钻和导出；AI 与备份页包含较密集的表单与操作流程。
- 现有规范要求：所有界面使用 `VipLedgerTheme`；文案进入 `strings.xml`；一屏一个 `LazyColumn`；Screen/ViewModel/UiState/Action 分层；共享组件下沉 designsystem；金额使用 `Money`；Preview 不依赖 Hilt。
- 当前主要 UI 质量缺口：共享分组/动作行/状态容器不足，页面重复实现较多；图标无障碍语义不完整；缺少 Compose UI 测试；无显式 `rememberLazyListState`/IME padding/动效；部分产品完整性缺口（附件 UI、贷款编辑等）需要单独任务，不在本 UI-only 任务中扩展。

## Assumptions

- 采用当前品牌绿色与暖中性背景作为基线，选择“活泼品牌化”方向：允许更鲜明的强调色、轻量渐变/微动效和有限的品牌装饰，但必须服务于信息层级与记账效率，不引入大面积装饰、过度动画或第三方 UI/图表库。
- 本任务不修改 Room schema、Repository/UseCase、统计计算、AI 协议、备份协议或 Gradle 版本。
- 若某个视觉需求必须新增业务数据或跨层能力，则记录为后续任务，不在本任务中隐式扩 scope。

## Requirements

### R1. 设计系统

- 整理 `core:designsystem` 的颜色语义、surface 层级、Typography、间距、圆角、边框、elevation 和交互状态 token；保证深浅色与支出/收入/转账语义色可读。
- 统一 `VipLedgerTheme`、`VipLedgerScaffold`、`VipLedgerTopBar`、金额、卡片、分组标题、列表行、按钮/Chip、表单字段、Snackbar/错误提示、空态和确认对话框。
- 新增共享组件优先无状态化并配 Preview；不在 feature 中复制已经应下沉的视觉模式。

### R2. 全页面迁移

- 迁移 `feature:ledger` 首页、设置、账本/账户/分类/标签、交易列表/编辑、周期规则、草稿、分期列表/详情/编辑。
- 迁移 `feature:stats` 搜索、统计、下钻；保持现有筛选语义、统计口径、图表几何和 CSV/PNG 导出。
- 迁移 `feature:ai` 自然语言记账、模型/商户规则管理，以及 `feature:sync` 备份/导入导出页面。
- 保持现有导航、ViewModel、UseCase 和业务行为；只补必要的 UI 状态呈现与一次性反馈。

### R3. 首页示范体验

- 优化净资产、本月收支、快捷操作、提醒、待确认草稿和最近交易的层级、卡片组合、金额对齐与点击反馈。
- 保留账本切换、设置和新增记账 FAB；遵守设置页作为工具入口的现有信息架构。
- 无账本、加载中、无交易、有提醒、待确认等状态必须有清晰、可操作的呈现。

### R4. 可用性与可测性

- 为图标按钮、返回、金额、状态色、危险操作和可点击行补齐 content description/语义；检查窄屏、字体放大、深色模式、键盘和导航栏 inset。
- 为新增纯函数/映射和关键共享组件补 JVM/Compose 测试；优先覆盖导航、首页新增记账、设置入口、表单反馈、最近交易行语义。
- 继续遵守 immutable StateFlow、Action、生命周期收集、单 LazyColumn 和资源化文案约束。

## Acceptance Criteria

- [ ] 所有主要用户可见页面使用统一 `VipLedgerTheme` 与 shared design language，不新增硬编码颜色或中文文案。
- [ ] 浅色/深色主题下文字与语义色可读；金额等宽且稳定对齐；间距、圆角、surface、elevation、禁用态一致。
- [ ] 首页、设置/管理、交易、周期/草稿/分期、搜索/统计/下钻、AI、备份/导入导出页面均完成视觉迁移并保留原有功能。
- [ ] 加载、空、错误、成功、危险操作和导出/保存反馈清晰；图标按钮和可点击行具备可访问语义。
- [ ] 不改变数据库、领域计算、统计口径、AI/备份协议或 feature 间依赖方向。
- [ ] 关键新增共享组件有 Preview；关键 UI 交互/纯逻辑有测试或明确记录无法自动化的手工验收项。
- [ ] `./gradlew.bat test assembleDebug` 与 `./gradlew.bat lintDebug` 通过；API 34 模拟器逐页走查无阻断性回归。
- [ ] 若任务改变使用说明或里程碑记录，同一变更同步更新 README/docs。

## Definition of Done

- 设计系统先完成，再按页面批次迁移；每批次可独立构建和回退。
- 完成相关单测/Compose 测试、Preview、lint 与 Debug 构建。
- 在模拟器检查浅色/深色、长文案、空/错/加载态、键盘/滚动、返回导航和导出选择器。
- 通过 Trellis quality check；有新的可复用 UI 约定时更新 `.trellis/spec/android/`。
- 工作树清洁后提交并归档任务，记录本次会话 journal。

## Out of Scope

- 预算、通知栏抓取、短信/账单文件解析、桌面小组件、应用锁、XLSX、多币种、账本共享。
- 附件选择/预览/恢复、贷款编辑/提前结清、数据健康诊断等需要领域或数据层扩展的功能；发现的缺口另开 V2 子任务。
- Room schema/migration、Repository/UseCase、统计定义、AI/备份协议、端侧模型或工具链升级。
- 替换 Compose/Material 3，或引入第三方图标/图表/UI 框架。

## Decision (ADR-lite)

**Context**：现有 V1 已有品牌绿和暖中性基础，但用户希望 V2 更有视觉吸引力；若继续仅做克制的默认 Material 卡片，容易与现有界面差异不足。

**Decision**：采用“活泼品牌化”作为 V2 首期视觉方向。在既有品牌色与 Material 3 语义角色上增加有限的高对比强调色、轻量渐变/微动效、醒目的首页 hero 与快捷操作层级；装饰必须可撤销、可访问、不能削弱金额/状态信息的可读性。共享组件仍是唯一视觉入口。

**Consequences**：视觉识别度和首页吸引力提升，但需要额外检查深色模式对比度、动画 reduced-motion 兼容性、中文长文案和低端设备性能；避免在每个页面分别创造不同的“活泼”效果。

## Technical Approach

1. 读取并遵守 `.trellis/spec/android/{index,architecture,directory-structure,ui,testing}.md`，必要时补充 charts/import-export 相关约束。
2. 修改 `core:designsystem` 的 theme/token 和共享组件，建立一套可 Preview、可复用的页面基础。
3. 依次迁移首页 → 设置/管理 → 交易/周期/草稿/分期 → 搜索/统计 → AI/Sync，避免只美化首页。
4. 对状态展示、无障碍、insets、列表滚动和资源文案做统一检查；只在 UI 层完成改动。
5. 运行测试/构建/lint，使用 API 34 模拟器走查全导航，最后更新 spec（如有新约定）、提交并 finish-work。

## Technical Notes

- 关键主题文件：`core/designsystem/src/main/kotlin/com/han1997/vipledger/core/designsystem/theme/{Color,Theme,Type,TransactionColors,ChartColors}.kt`。
- 关键共享组件：`core/designsystem/src/main/kotlin/com/han1997/vipledger/core/designsystem/component/{VipLedgerScaffold,VipLedgerTopBar,AmountText,EmptyState,TxnLine,LabeledTextField,ConfirmDialog}.kt`。
- 关键页面：`feature/ledger/.../ui/home/HomeScreen.kt`、`ui/settings/SettingsScreen.kt`、`ui/manage/*Screen.kt`、`ui/txnedit/*`、`ui/txnlist/*`、`ui/recurring/*`、`ui/drafts/*`、`ui/loan/*`；`feature/stats/.../ui/{search,stats,drill}`；`feature/ai/.../ui`；`feature/sync/.../ui/BackupScreen.kt`。
- 现有 UI 规范明确禁止 screen 内硬编码颜色/文案、嵌套滚动容器和跨 feature 依赖；数值聚合与图表几何不得因视觉重构重复实现。
- 视觉方向已确定为“活泼品牌化”：在品牌绿/暖背景上使用有限的鲜明强调色、轻量渐变/微动效和品牌装饰，优先服务信息层级与记账效率，并保留对比度、可访问性和性能约束。

## Research References

- 本任务不需要外部技术选型研究；实现沿用现有 Compose/Material 3 与项目内 `core:designsystem` 约定。
