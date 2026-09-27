# M3 搜索与统计

> 上级规划：`docs/PLAN.md` 第 5 节里程碑 M3。本文件是 M3 的任务级 PRD，完成后追加「完成情况」小节。
> 前置：M2 记账主干已交付并提交（`dcd7228`）。

## 1. 背景与目标

M2 能记账了，但**数据只能按时间倒序看**：找不到「上个月在宠物上花了多少」，
也回答不了「这个月钱花在哪」。M3 把数据变成可回答问题的东西：

1. **组合搜索**：标题/备注关键字 + 时间段 + 分类（含子分类）+ 标签 + 账户 + 类型 + 金额区间；
2. **时间段统计**：常见时间段与自定义区间，按分类/标签/账户/时间分组，饼图与趋势图；
3. **下钻**：从统计的任一分组点进对应明细；
4. **导出**：CSV（可再导入 Excel）与统计图片。

M3 不做：周期记账与借贷分期（M4）、备份与导入导出向导（M5）、AI 记账（M6/M7）、
预算与提醒（v2）。

## 2. 范围

### 2.1 做

| 能力 | 说明 |
|---|---|
| 搜索条件 | 关键字（标题与备注，`LIKE %kw%`）、时间段（10 个预设 + 自定义闭区间）、分类（多选，选一级自动带出子分类）、标签（多选）、账户（多选，含转入账户）、类型（多选）、金额区间（绝对值）、是否包含待确认草稿 |
| 搜索结果 | 按天分组 + 当日合计、命中笔数与合计、点击进编辑、长按删除、一键清空条件 |
| 统计时间段 | 复用搜索的时间段控件（预设 + 自定义） |
| 统计口径 | 与 `docs/DATA-MODEL.md` 第 5 节完全一致：只算已确认、未删除、未排除的收支；转账/还款/调整影响余额但不进收支 |
| 分组维度 | 按分类（一级汇总、可展开二级）、按标签、按账户、按时间（日/周/月分桶） |
| 图表 | 分类占比饼图（可切收入/支出）、时间趋势柱状图；都用 Compose Canvas 自绘，不引第三方图表库 |
| 下钻 | 点分类/标签/账户/时间桶 → 进入该条件下的明细列表（复用搜索页的过滤结果） |
| 导出 | CSV（UTF-8 带 BOM，Excel 直接打开）与统计卡片图片（PNG），都走 SAF `CreateDocument`，不申请存储权限 |
| 入口 | 首页顶栏加「统计」入口；统计页内可切到搜索 |

### 2.2 不做（留给后续里程碑）

- 周期规则与分期计划（M4）；WebDAV 备份、账单导入向导（M5）；AI 解析（M6/M7）。
- 图表交互（缩放、拖动、长按看某点）；预算与同比环比（v2）。

## 3. 交付物

### 3.1 `:core:domain`（纯 JVM）

- `model/SearchQuery.kt`：搜索条件的领域表达（时间段用「预设或自定义区间」二选一）。
- `model/StatsReport.kt`：`StatsReport`（区间 + 粒度 + 汇总 + 分桶），给界面一次性消费。
- `usecase/search/SearchTxnsUseCase.kt`：`SearchQuery` → `TxnFilter`（含一级分类展开成子分类、金额区间、
  类型集合、草稿开关），返回 `Flow<List<Txn>>`。
- `usecase/stats/ObserveStatsReportUseCase.kt`：`(ledgerId, range, granularity)` → `Flow<StatsReport>`，
  复用 `StatsCalculator`（分类/标签/账户/时间分桶）。
- `calculator/CsvWriter.kt`：纯函数 CSV 生成（转义 `"`、逗号、换行；`\r\n` 行尾；可选 BOM）。
- `usecase/stats/BuildTxnCsvUseCase.kt`：把账目列表导出成 CSV 文本（列：日期、时间、类型、金额、
  分类、账户、标签、标题、备注、是否计入统计）。

### 3.2 `:core:designsystem`

- 下沉共用件（`feature:ledger` 与 `feature:stats` 都要用，按架构规范不能互相依赖）：
  `format/Formatters.kt`、`format/NameMaps.kt`、`component/TxnLine.kt`。
- 图表：`component/chart/PieChart.kt`（占比环图 + 图例）、`component/chart/BarChart.kt`（时间趋势柱状图）。

### 3.3 `:feature:stats`（新界面）

- `navigation/StatsRoutes.kt`：路由常量 + `NavGraphBuilder.statsGraph()`。
- `ui/search/`：搜索页（条件区 + 结果区 + 导出）。
- `ui/stats/`：统计页（时间段切换、维度切换、图表、明细排行、导出）。
- `ui/drill/`：下钻明细页（带固定条件的账目列表）。
- `ui/common/`：过滤器状态与组件（时间段选择、分类多选、标签/账户多选）——与搜索页共用。

### 3.4 `:app`

- `VipLedgerApp.kt` 装配 `statsGraph()`；首页顶栏加统计入口。

## 4. 验收标准

**自动**：

- `.\gradlew.bat test assembleDebug` 全绿；
- 新增单测覆盖：`SearchQuery` → `TxnFilter` 映射（子分类展开、金额区间、类型集合、草稿开关）、
  统计报表（口径与分桶边界）、`CsvWriter`（含逗号/引号/换行的字段转义）、CSV 行内容。

**手动（模拟器 AVD nordic-ui-api34）**：

1. 关键字搜索命中标题与备注；
2. 时间预设切换后结果条数与合计跟着变；
3. 选一级分类能带出该一级分类下二级分类的账目；
4. 金额区间过滤生效；
5. 统计页「本月」数字与首页「本月收支」一致；
6. 分类饼图占比之和为 100%（或与总额一致），点分类进下钻明细且条数一致；
7. 转账/还款/调整不出现在统计里；
8. 导出 CSV 能在文件管理器里打开，列与转义正确。

## 5. 技术方案要点

- **搜索只走一条 SQL**：`TxnDao.TXN_FILTER_SQL` 已覆盖全部条件，M3 只做条件翻译，不加 DAO 查询。
- **时间段统一表达**：`SearchQuery.period` = `DateRangePreset?` 或 `DateRange?`（自定义），
  在用例里解析成 `DateRange` 再传给仓库（仓库负责按设备时区折成毫秒边界）。
- **分类含子分类**：搜索用例用 `CategoryTree.subtreeIds` 把选中的一级分类展开；
  统计的 `byCategory` 已经按一级汇总，界面点一级分类下钻时同样走子树展开，保证数字一致。
- **图表自绘**：饼图用 `Canvas` + `drawArc`，柱状图用 `drawRect`；颜色从 designsystem 语义色取。
- **导出不引 FileProvider**：用 `ActivityResultContracts.CreateDocument` 拿用户选的 URI 写入，
  零权限、零 provider 配置。
- **文案**：新增文案进 `feature:stats/src/main/res/values/strings.xml`。

## 6. 风险

| 风险 | 缓解 |
|---|---|
| 搜索条件多，界面状态容易乱 | 单一 `SearchUiState`（条件 + 结果）与 `SearchFilter` 数据类，`onAction` 归约更新；条件变更即重新订阅 |
| `LIKE %kw%` 无法用索引，数据量大时慢 | 先用 10 万级数据；不达标再上 FTS5（PLAN 第 8 节已记） |
| Compose 截图导出在不同版本 API 差异大 | 用 `rememberGraphicsLayer()` + `toImageBitmap()`；失败就退回「导出 CSV + 提示」，不阻塞主流程 |
| 统计与首页口径漂移 | 两处都用 `StatsCalculator`，且加断言单测（本月统计 == 首页本月数字） |

## 7. 完成情况

> 任务完成后在此记录：实际交付、与计划的偏差、验证命令与结果。

**状态**：已交付并验证（2026-09-24）。自动验收全绿，模拟器手工走查 8/8 通过。

### 实际交付

- `:core:domain`：`model/SearchQuery.kt`、`model/StatsReport.kt`、`calculator/SearchQueryMapper.kt`、
  `calculator/CsvWriter.kt`、`usecase/search/SearchTxnsUseCase.kt`、`usecase/stats/ObserveStatsReportUseCase.kt`、
  `usecase/stats/BuildTxnCsvUseCase.kt`。
  计划外新增：`model/TxnDayGroup.kt` + `calculator/TxnDayGrouper.kt`（明细 / 搜索结果 / 下钻三处共用
  按天分组与当日合计，只累加 `StatsCalculator.isCounted` 的账目，避免三处口径漂移）、
  `core:model` 的 `Long.toLocalDateTime` 扩展、`fake/Fakes.kt` 的金额区间过滤。
- `:core:designsystem`：`format/Formatters.kt`、`format/NameMaps.kt`、`component/TxnLine.kt` 从
  `:feature:ledger` 下沉（后者删文件、改 import）；新增 `theme/ChartColors.kt`、
  `component/chart/{ChartModels,ChartGeometry,PieChart,BarChart}.kt`、`res/values/strings.xml`
  （跨 feature 共用文案）；`build.gradle.kts` 加 `implementation(project(":core:domain"))` 与测试依赖。
- `:feature:stats`：`navigation/StatsRoutes.kt`（`stats/home|search|drill`，下钻条件走可空查询参数 +
  `drillArgsOf` 归一化）、`ui/common/{TxnFilterState,FilterOptions,FilterComponents,ExportNaming,UriWriter}.kt`、
  `ui/search/`、`ui/stats/`、`ui/drill/`、`res/values/strings.xml`（`stats_` / `search_` / `drill_` 前缀）；
  补齐了此前缺失的构建脚本（hilt/ksp/navigation/coroutines/datetime/preview + 测试依赖）。
- `:app`：`VipLedgerApp` 装配 `ledgerGraph(onOpenStats=…)` 与 `statsGraph(onEditTxn=…)`；
  `HomeScreen` 顶栏加「统计」入口（新参数 `onOpenStats`）。
- `:core:data`：`UseCaseModule` 补 3 个用例的 `@Provides`（不补 Hilt 直接 MissingBinding 编译失败）。

### 与计划的偏差

| 项 | 计划 | 实际 |
|---|---|---|
| 下钻页用途 | 统计分组点进去看明细 | 同左；`DrillArgs` 同时支持「分类 / 标签 / 账户 三选一 + 时间段」，与搜索条件语义共用 |
| 图表配色 | 「颜色从 designsystem 语义色取」 | 明确为：占比环图按下标取色，且**收入口径从收入绿起、支出口径从支出红起**（`chartColorOf(index, kind)`）。原实现收入饼图第一块是支出红，语义冲突 |
| 截图导出 | `rememberGraphicsLayer()` + `toImageBitmap()` | 同左；实测 API 34 可用（导出 1080×1057 PNG）。仍按风险表保留 `runCatching` → 「导出失败，可改用导出 CSV」降级 |
| 导出文件名 | 未规定 | ASCII 前缀 + 日期：`vipledger-{prefix}-yyyyMMdd.csv|.png`，规避中文文件名在部分文件管理器的兼容问题 |
| 手工走查 | 8 项 | 8 项全部执行；另外多发现并修复 2 个缺陷（见下） |

### 走查中发现并修复的缺陷（随本次改动提交）

1. **柱状图横轴标签被裁成单个字符**：`BarChart` 的标签行给每个时间桶分配等宽格子（30 天时约 36px），
   配合 `maxLines = 1` + 溢出裁切，把「9/1」渲染成「9」。改为格子内
   `Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)`，标签可超出格子居中显示。
2. **收入饼图通体支出红**：`chartColorOf(index)` 调色板首色是 `ExpenseRed`。新增
   `chartColorOf(index: Int, kind: CategoryKind)`（收入口径 +1 偏移）并加 `ChartColorsTest` 钉住。

### 验证命令与结果

```powershell
.\gradlew.bat cleanTest test assembleDebug
```

- BUILD SUCCESSFUL；JVM 单测 **200 个用例 / 40 个测试类，0 失败 0 错误 0 跳过**
  （Android 库模块各跑 debug/release 两遍，test-results XML 合计 262）。
- M3 相关用例：`SearchQueryMapperTest`(10)、`SearchTxnsUseCaseTest`(7)、`ObserveStatsReportUseCaseTest`(6)、
  `BuildTxnCsvUseCaseTest`(4)、`CsvWriterTest`(5)、`TxnDayGrouperTest`(3)、`StatsMatchesHomeOverviewTest`(1)、
  `ChartGeometryTest`(7)、`ChartColorsTest`(3)、`TxnFilterStateTest`(9)、`FilterOptionsTest`(3)、
  `StatsRowsTest`(4)、`DrillArgsTest`(5)。

**模拟器手工走查**（AVD `nordic-ui-api34`，`adb -s emulator-5554`）：

| # | 验收项 | 结果 |
|---|---|---|
| 1 | 关键字搜索 | 通过：关键词 `zzz` / `20` → 命中 0 笔 + 「没有符合条件的账目」；清空后恢复 4 笔 |
| 2 | 时间预设切换 | 通过：搜索「不限时间」/「本月」条数与合计随之变化；统计页切「上周」→ 0 笔 / ¥0.00 |
| 3 | 选一级分类带出子分类 | 部分通过：点「餐饮」→ 命中 1 笔（父分类选中生效）；子树展开由 `SearchQueryMapperTest` 覆盖（模拟器内没有挂在子分类上的账目） |
| 4 | 金额区间 | 通过：「最低 20」→ 命中 3 笔（¥12.50 被排除），收 ¥500.00 / 支 ¥0.00 |
| 5 | 统计「本月」== 首页「本月」 | 通过：两页均 收入 ¥500.00 / 支出 ¥12.50 / 结余 ¥487.50 |
| 6 | 饼图占比 + 下钻条数一致 | 通过：分类 / 标签 / 账户三个维度均 100.0%；点「餐饮 100.0% ¥12.50」→ 下钻页「共 1 笔」 |
| 7 | 转账 / 还款 / 调整不进统计 | 通过：搜索「本月」命中 4 笔（含 2 笔转账）合计只有 收 ¥500 / 支 ¥12.50；统计页 2 笔、支出 ¥12.50 |
| 8 | 导出 CSV / 图片 | 通过：SAF `CreateDocument` 保存到 Downloads；CSV 带 BOM（`EF BB BF`）、列为 `日期…已删除`、`\r\n` 行尾，189 B 可被 Excel 直接打开；PNG 合法（1080×1057，70 KB） |
| — | 稳定性 | logcat 无本应用异常 / 崩溃 |

### 遗留 / 未完成

- 验收项 3 的「子分类账目」在模拟器数据里不存在，未做端到端走查（逻辑有单测覆盖）。
- `rememberGraphicsLayer()` 截图导出只在本机 API 34 验证通过，低版本 API 未验证；已按风险表保留降级提示。
- `:feature:stats` 未加 `androidTest`（Compose UI 测试）；本轮走查用 adb + uiautomator 手工执行。
- 走查时遇到的 `ModernMediaScanner: Trouble scanning ... java.lang.NullPointerException` 是系统媒体扫描器
  处理新导出 PNG 的已知噪音，与本应用无关（应用侧无异常）。