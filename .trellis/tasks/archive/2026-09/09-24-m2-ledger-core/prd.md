# M2 记账主干

> 上级规划：`docs/PLAN.md` 第 5 节里程碑 M2。本文件是 M2 的任务级 PRD，完成后追加「完成情况」小节。
> 前置：M1（数据层与领域模型）已交付并提交（`ff8913c`）。

## 1. 背景与目标

M0 有了工程骨架，M1 有了 12 张表、仓库实现与纯逻辑（分期计息 / 余额 / 统计口径 / 两级分类校验）。
但**当前 App 打开只有 M0 骨架页，用户一笔账都记不了**。M2 的目标是打通「能用的记账主干」：

1. 账本、资金账户、两级分类、标签的增删改查；
2. 记一笔（支出 / 收入 / 转账 / 还款 / 调整），支持标题、备注、日期、多标签；
3. 明细列表（按时间倒序、按天分组、显示金额与语义色）；
4. 首页概览（净资产、本月收支、今日支出、最近账目）。

M2 不做：搜索筛选页（M3）、统计图表（M3）、周期记账与借贷分期（M4）、备份与导入导出（M5）、AI 记账（M6/M7）。

## 2. 范围

### 2.1 做

| 能力 | 说明 |
|---|---|
| 账本管理 | 列表 / 新建（自动带默认现金账户 + 内置两级分类）/ 改名 / 图标 / 设为默认 / 归档 / 删除（仅限无账目） |
| 账户管理 | 列表 / 新建 / 编辑 / 归档 / 删除（仅限无流水）；支持现金、储蓄卡、信用卡、虚拟账户、应收、应付、贷款、投资；信用卡与贷款支持额度、账单日、还款日 |
| 分类管理 | 两级树展示 / 新建一级 / 新建二级 / 重命名 / 改排序可选 / 删除（有子分类时级联软删，被账目引用时拒绝） |
| 标签管理 | 列表 / 新建 / 改名 / 颜色 / 删除 |
| 记账编辑 | 支出、收入、转账、还款、调整；金额、账户（转账/还款带目标账户）、分类（按类型限定 kind）、多标签、日期时间、标题、备注 |
| 明细列表 | 按账本 + 时间倒序；按天分组带当日合计；长按/侧滑删除；点击进入编辑 |
| 首页概览 | 当前账本、净资产、本月收入/支出/结余、最近 10 笔；入口进明细、记账、管理 |
| 应用导航 | 单 Activity + Navigation Compose，路由集中在 `:app`；`feature:ledger` 只暴露 Screen 与路由常量 |

### 2.2 不做（留给后续里程碑）

- 搜索页与高级筛选（M3）；统计图表与时间段切换（M3）；周期规则（M4）；借贷分期计划（M4）；
  WebDAV/导出导入（M5）；AI 解析与草稿确认（M6）；端侧模型（M7）。

## 3. 交付物

### 3.1 `:core:domain`（纯 JVM）

新增用例（`usecase/<聚合>/`）：

- `ledger/`：`ObserveLedgersUseCase`、`CreateLedgerUseCase`、`UpdateLedgerUseCase`、`SetDefaultLedgerUseCase`、`ArchiveLedgerUseCase`、`DeleteLedgerUseCase`
- `account/`：`ObserveAccountsUseCase`、`SaveAccountUseCase`、`ArchiveAccountUseCase`、`DeleteAccountUseCase`
- `category/`：`ObserveCategoryTreeUseCase`、`SaveCategoryUseCase`、`DeleteCategoryUseCase`
- `tag/`：`ObserveTagsUseCase`、`SaveTagUseCase`、`DeleteTagUseCase`
- `home/`：`ObserveHomeOverviewUseCase`

新增模型：`CategoryNode`（两级树节点）、`HomeOverview`。

计算器补充：`BalanceCalculator.netWorthFromCache` / `totalDebtFromCache`（首页用缓存的余额求和，不必回放全部流水）。

### 3.2 `:core:data`

- `TxnDao.countByCategory` + `TxnRepository.countByCategory`（删除分类前的引用检查）
- `UseCaseModule` 补齐全部新用例的 `@Provides`

### 3.3 `:core:designsystem`

- 通用组件：`AmountText`（等宽数字 + 语义色）、`EmptyState`、`VipLedgerTopBar`、`ConfirmDialog`、`LabeledField`

### 3.4 `:feature:ledger`

- `ui/home/`：首页概览
- `ui/txnlist/`：明细列表
- `ui/txnedit/`：记账编辑
- `ui/manage/`：账本 / 账户 / 分类 / 标签管理
- `navigation/LedgerRoutes.kt`：路由常量 + `NavGraphBuilder.ledgerGraph()`

### 3.5 `:app`

- `VipLedgerApp.kt`：`NavHost` 装配 `ledgerGraph()`，替换 M0 骨架首页
- 依赖新增 `androidx.navigation:navigation-compose`、`androidx.hilt:hilt-navigation-compose`

## 4. 验收标准

**自动**：`.\gradlew.bat test assembleDebug` 全绿；新增用例均有单测（校验失败路径、级联/拒绝删除、概览数字）。

**手动（模拟器 AVD nordic-ui-api34）**：

1. 首启进入首页，显示默认账本与「现金」账户，净资产 ¥0.00；
2. 新建账本「旅行」→ 自动带内置分类与现金账户 → 切换到该账本；
3. 新建账户「招行储蓄卡」、一级分类「宠物」+ 二级分类「猫粮」、标签「报销」；
4. 记 20 笔（含至少 1 笔转账、1 笔收入、1 笔带 2 个标签）；
5. 明细列表 20 条齐全、按天分组、当日合计正确、支出红/收入绿/转账蓝；
6. 首页净资产 = 各账户余额之和，本月收支与明细一致；
7. 删除被引用的分类 → 提示拒绝；删除无引用分类 → 成功；
8. 杀掉进程重进 → 数据仍在。

## 5. 技术方案要点

- **不改表结构**：M1 已建 `attachment` 表并把 12 张表铺满，M2 只加 DAO 查询，不需要迁移；`LedgerDatabase` 版本保持 1。
- **ViewModel 只依赖用例**：`feature:ledger` 的 VM 注入 `:core:domain` 的用例，禁止碰 DAO / 仓库实现。
- **状态约定**：`XxxUiState`（不可变 data class）+ `XxxAction` sealed interface，Screen 无业务逻辑。
- **金额展示**：一律 `Money.toDisplayString()`；数量级用 `AmountText`，颜色走 designsystem 语义色。
- **文案**：新增用户可见文案进各模块 `strings.xml`（feature 模块自带 `res/values/strings.xml`），Compose 里不写死中文。
- **导航参数**：只传 id 等原始类型，`navArgument` 用字符串；不传对象。

## 6. 风险

| 风险 | 缓解 |
|---|---|
| `hiltViewModel()` 需要 `androidx.hilt:hilt-navigation-compose`（新依赖） | 已确认阿里云 google 镜像可解析 1.2.0；若失败，退回 `EntryPointAccessors` 手写 Factory |
| 记账编辑表单字段多，容易出状态 bug | 单一 `TxnEditUiState` + `onAction` 归约更新；金额用字符串输入、保存时 `Money.parse` |
| 首页概览订阅多个 Flow，可能抖动 | 用 `combine` 合并为单个 `StateFlow`，`WhileSubscribed(5s)` 缓存 |
| 删除账本/账户的数据安全 | 一律软删账户与分类；账本仅当无账目时物理删（主键无级联约束，物理删更干净） |

## 7. 完成情况

（2026-09-24 完成）

### 7.1 实际交付

- `:core:domain`：`ledger/`（Observe/Select/Create/Update/SetDefault/Archive/Delete 共 8 个）、
  `account/`（Observe/Save/Archive/Delete）、`category/`（ObserveTree/Save/Delete）、
  `tag/`（Observe/Save/Delete）、`home/ObserveHomeOverviewUseCase`、
  `txn/GetTxnUseCase`；模型 `CategoryNode`、`HomeOverview`、`AccountDraft`；
  端口 `CurrentLedgerStore`；计算器补 `BalanceCalculator.netWorthFromCache/totalDebtFromCache`、
  `CategoryTree.nodesOf`；`BuiltinDataFactory` 抽出「默认现金账户 + 内置分类」的共用构造。
- `:core:data`：`PreferenceCurrentLedgerStore`（DataStore）、`LedgerUseCaseModule` 装配全部新用例、
  `TxnRepository.countByCategory`（+ 对应 DAO 查询）。
- `:core:designsystem`：`AmountText`/`SignedAmountText`（等宽数字 + 语义色）、`EmptyState`、
  `VipLedgerTopBar`、`ConfirmDialog`、`LabeledTextField`、`amountColorOf`。
- `:feature:ledger`（原本为空模块）：`navigation/LedgerRoutes.kt` + `ledgerGraph()`；
  `ui/home`、`ui/txnlist`、`ui/txnedit`、`ui/manage`（账本/账户/分类/标签）共 4 组 Screen+ViewModel+UiState；
  `ui/common`（Formatters、TxnLine、ManageNotice、NameMaps）。
- `:app`：`VipLedgerApp.kt` 装配 NavHost；`MainActivity` 启动时跑 `EnsureInitialDataUseCase`；
  删除 M0 骨架首页。

### 7.2 与计划的偏差

| 计划 | 实际 | 原因 |
|---|---|---|
| 无 | `TxnRepository.countByCategory` 用 `@Transaction` 原生 SQL 实现 | 需要在删分类前判断「任一（含已删）账目是否引用」 |
| 无 | 新增 `NameMaps.kt` 把「分类/账户/标签 id→名字」抽成共用扩展 | 走查发现首页最近账目显示「未分类」，需要与明细列表共用查表逻辑 |
| 无 | `TxnEditUiState` 增加 `targetCandidates` / `resolvedTargetAccountId()` | 走查发现转账未预选转入账户导致保存必然失败（见 7.3） |
| 无 | 管理页失败提示改为优先显示 `AppError.message` | 原「操作失败」无法说明拒绝原因（如分类被引用） |
| PRD 写 `LabeledField` | 实现为 `LabeledTextField` | 命名对齐 Compose 习惯 |

### 7.3 走查中修掉的缺陷

1. **首页「最近账目」全部显示未分类**：`HomeUiState` 只有 `overview`，没有 id→名字的查表，
   传 `categoryName = null / accountName = null`。已补三个名字映射并在 `HomeViewModel` 里 combine。
2. **转账保存必然失败**：切到「转账」时 `toAccountId` 仍为 null，领域层 `TxnValidator` 直接拒绝
   （「转账需要选择转入账户」），而界面只提示「操作失败」。已改为切类型/换转出账户时自动预选，
   并加 `TxnEditUiStateTest`（6 例）守住；该测试还抓出「非目标账户类型应解析为 null」的漏判。
3. **管理页失败原因丢失**：`ManageNotice.FAILED` 只有通用文案。现在 4 个管理页的状态多一个
   `noticeDetail`，失败时显示领域原因（实测「分类「catfood」已被账目使用，不能删除」）。

### 7.4 验证

自动：

```powershell
.\gradlew.bat test assembleDebug   # BUILD SUCCESSFUL，164 个 JVM 测试 0 失败
```

模块分布：`:core:model` 22、`:core:common` 4、`:core:domain` 76、`:core:database` 13、
`:core:data` 12、`:feature:ledger` 6（M2 新增）；`:core:domain` 用例测试覆盖
账本/账户/分类/标签的增删改校验、级联与拒绝删除、首页概览口径。

手动（模拟器 AVD nordic-ui-api34，`nordic-ui-api34` API 34 @1080x2400）：

| 验收项 | 结果 |
|---|---|
| 首启首页显示默认账本 + 现金账户 + 净资产 ¥0.00 | 通过 |
| 新建账本 → 自动带内置两级分类与现金账户 → 切换后完全隔离 | 通过（新账本 0 账目、1 账户，无标签） |
| 新建账户（储蓄卡，期初 1000）、一级分类 + 二级分类、标签 | 通过（走查用 ASCII 名 `CMB`/`pet`/`catfood`/`baoxiao`，见下方限制） |
| 记一笔：支出、收入（带标签）、转账 | 通过 |
| 明细列表按天分组、当日合计正确、±号与语义色正确 | 通过（`收 ¥500.00 / 支 ¥12.50`） |
| 首页净资产 = 各账户余额之和；本月收支与明细一致 | 通过（1000+100+500−100−12.50 = ¥1,487.50；本月收 500 / 支 12.50） |
| 转账/还款/调整不计入收支但影响余额 | 通过（转账后净资产不变、月度数字不变） |
| 长按删除账目 → 余额自动重算 | 通过（删除 ¥8.00 后净资产 1479.50 → 1487.50） |
| 删除被账目引用的分类 → 拒绝并说明原因 | 通过（「分类「catfood」已被账目使用，不能删除」） |
| 杀进程重进数据仍在 | 通过 |

环境限制：`adb shell input text` 无法注入中文（非 ASCII 抛
`NullPointerException: Attempt to get length of null array`），因此走查中手工输入的
账本名/账户名/分类名/标签名用 ASCII；中文渲染由内置分类、默认账本等内置数据覆盖验证。