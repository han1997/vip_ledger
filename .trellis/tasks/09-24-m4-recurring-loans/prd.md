# M4 周期记账与借贷分期

> 上级规划：`docs/PLAN.md` 第 5 节里程碑 M4。本文件是 M4 的任务级 PRD，完成后追加「完成情况」小节。
> 前置：M3 搜索与统计已交付并提交（`a7f8a45`）；M1 已建好 `recurring_rule` /
> `installment_plan` / `installment_period` 三张表与分期计息纯逻辑。

## 1. 背景与目标

记账主干（M2）与搜索统计（M3）让「记一笔」和「查一个月」都能用了，但**重复发生的账**
和**负债分期**还得手工一笔笔敲：房租每月要记得记，花呗分 12 期要自己算每期还多少、
还到第几期了。M4 把这两类「有规律的账」交给引擎：

1. **周期规则引擎**：每天 / 每周 / 每月 / 每年 / 每 N 天 / 每 N 月六种频率，支持
   按周几、按月中某日、按某月推算；支持永不结束 / 到某日 / 共 N 次三种结束条件；
   默认只生成**待确认草稿**，可切换自动入账；错过多个周期时可选跳过或**补记**。
2. **分期与还款**：按等额本息 / 等额本金 / 先息后本 / 自定义每期建立分期计划，
   逐期生成应还明细；每期一键还款生成还款流水并核销，还清后计划自动结清。
3. **提醒**：信用卡账单日 / 还款日与分期到期 / 逾期提醒，应用内提醒卡 + 后台每日检查通知。

M4 不做：WebDAV 备份与账单导入向导（M5）、AI 自然语言记账（M6/M7）、预算与超支提醒（v2）。

## 2. 范围

### 2.1 做

| 能力 | 说明 |
|---|---|
| 周期规则 | 名称、类型（支出/收入/转账/还款）、金额、账户（转账带转入账户）、分类、标签、标题备注 |
| 频率 | `DAILY` / `WEEKLY`（可指定周几）/ `MONTHLY`（可指定月中日，31 表示当月最后一天）/ `YEARLY`（可指定月与日）/ `EVERY_N_DAYS` / `EVERY_N_MONTHS`，步长 `interval` |
| 结束条件 | `NEVER` / `UNTIL_DATE` / `COUNT`（共生成 N 次） |
| 生成方式 | `DRAFT`（默认，生成待确认草稿）/ `AUTO`（直接入账） |
| 错过策略 | `SKIP`（默认，只补最近一期）/ `CATCH_UP`（补记全部错过的周期） |
| 规则操作 | 新建 / 编辑 / 启停 / 删除 / 「立即生成」手动触发一次引擎 |
| 待确认草稿 | 首页「待确认」入口列出草稿；确认后入账并重算余额，丢弃后从统计与列表消失 |
| 分期计划 | 负债账户 + 四种计息方式 + 本金 / 期数 / 年化利率 / 一次性与每期手续费 / 首期还款日 |
| 每期明细 | 期数、还款日、本金、利息、手续费、合计、状态（待还/已还）、关联还款流水 |
| 还款核销 | 选还款来源账户 → 生成一笔 `DEBT_REPAY` 流水（来源 → 负债账户）→ 该期标记已还；全部还清后计划 `CLOSED` |
| 提醒 | 还款日临近（默认 7 天窗口）与逾期未还的分期期数；信用卡账户账单日 / 还款日提醒；支持跳转到对应分期 / 账户 |
| 入口 | 首页新增「周期记账 / 分期 / 待确认」入口；首页提醒卡 |

### 2.2 不做（留给后续里程碑）

- WebDAV 备份、定时备份、账单导入向导（M5）；AI 解析与端侧分类（M6/M7）。
- 部分还款、提前结清与利息重算（v2）；提醒时间自定义与免打扰时段（v2）。
- 周期规则的「按工作日/仅月末」等高级日历规则（v2）。

## 3. 交付物

### 3.1 `:core:domain`（纯 JVM）

- `calculator/RecurrenceCalculator.kt`：周期推算纯函数。输入规则与起点，输出
  首次发生日、`nextOccurrence(rule, from)`、`occurrencesIn(rule, from, to)`；
  负责六种频率的步进、月末兜底（29/30/31 与 `byMonthDay = 31`）、结束条件判定。
- `calculator/ReminderCalculator.kt` + `model/Reminder.kt`：给定「今天 + 分期计划与各期 +
  账户」算出一组提醒（分期临近 / 分期逾期 / 信用卡账单日 / 信用卡还款日），带严重级别与跳转目标。
- `usecase/recurring/`：
  - `SaveRecurringRuleUseCase`（校验 + 新建/更新，日程字段变化时重置游标）
  - `SetRecurringRuleEnabledUseCase`、`DeleteRecurringRuleUseCase`、`ObserveRecurringRulesUseCase`
  - `GenerateRecurringTxnsUseCase`：对账本下启用的规则推进游标并生成流水
    （`DRAFT`/`AUTO` × `SKIP`/`CATCH_UP`），流水与游标更新在同一事务，返回生成报告
  - `ObservePendingDraftsUseCase`
- `usecase/txn/ConfirmDraftUseCase.kt` / `DiscardDraftUseCase.kt`：草稿确认 / 丢弃 + 余额重算。
- `usecase/loan/`：
  - `ObserveInstallmentPlansUseCase`、`ObserveInstallmentPlanDetailUseCase`（计划 + 各期）
  - `RepayInstallmentPeriodUseCase`：写还款流水 + 标记该期已还 + 重算余额 + 全部还清则结清计划（同一事务）
  - `DeleteInstallmentPlanUseCase`
- 仓库接口补充：`TxnRepository.updateDraftStatus`、`InstallmentRepository.listPeriodsForLedger`。

### 3.2 `:core:data` / `:core:database`

- 表结构不变（M1 已建 `recurring_rule` / `installment_plan` / `installment_period`），因此
  **不升 schema 版本、不写迁移**；只补 DAO 查询与仓库实现、Hilt 装配。

### 3.3 `:feature:ledger`（界面）

- `ui/recurring/`：周期规则列表页（含启停、删除、立即生成）+ 规则编辑页（全部频率与结束条件表单）。
- `ui/drafts/`：待确认草稿页（确认 / 丢弃 / 批量确认）。
- `ui/loan/`：分期计划列表、计划详情（每期明细 + 还款）、新建/编辑计划表单。
- `ui/home/`：提醒卡 + 新入口；`navigation/LedgerRoutes.kt` 增加对应路由。
- `strings.xml` 新增文案使用 `recurring_` / `loan_` / `draft_` / `reminder_` 前缀。

### 3.4 `:app`

- 后台任务：WorkManager 每日任务（周期流水生成 + 提醒通知），用 Hilt `@EntryPoint` 注入依赖，
  不引入 `hilt-work`；`VipLedgerApplication` 注册通知渠道与周期任务。
- 通知权限：`POST_NOTIFICATIONS`（API 33+）运行时申请，未授权时只在应用内展示提醒。

## 4. 验收标准

| # | 验收项 | 判定 |
|---|---|---|
| 1 | 六种频率推算 | 单测覆盖步进、`byWeekday` / `byMonthDay` / `byMonth`、月末与闰年边界 |
| 2 | 结束条件 | `UNTIL_DATE` / `COUNT` 到达后不再生成；`COUNT` 与 `generated_count` 一致 |
| 3 | 草稿与自动入账 | `DRAFT` 生成 `PENDING`（不进统计、不进余额），`AUTO` 直接 `CONFIRMED` |
| 4 | 错过补记 | `SKIP` 只补最近一期，`CATCH_UP` 补齐区间内全部；重复运行不重复生成（幂等） |
| 5 | 草稿确认 / 丢弃 | 确认后进明细与统计并重算余额；丢弃后不再出现 |
| 6 | 分期四种计息 | 复用 M1 `InstallmentCalculator` 单测；计划落库期数与合计正确 |
| 7 | 还款核销 | 生成 `DEBT_REPAY` 流水、期标记已还、账户余额同步、还清后计划 `CLOSED` |
| 8 | 提醒计算 | 分期临近 / 逾期 / 信用卡账单日与还款日四类提醒的窗口与排序正确 |
| 9 | 模拟器走查 | 建规则→立即生成→确认草稿→余额变化；建分期→还款→结清；首页提醒卡显示到期项 |
| 10 | 构建与质量 | `.\gradlew.bat test assembleDebug` 全绿，lint 无新增告警 |

## 5. 技术方案要点

- **纯逻辑可测**：推算、提醒、生成计划全部放 `:core:domain` 的 `calculator/`，
  只依赖 `kotlinx.datetime`；时间一律按设备时区的 `LocalDate` 计算后再转 UTC 毫秒。
- **游标推进**：规则存 `generated_count` 与 `next_occurrence_at`；生成时从游标出发推进，
  达到结束条件后置 `next_occurrence_at = null` 并停用，保证幂等。
- **事务边界**：一次生成（N 笔流水 + 游标更新）、一次还款（流水 + 期状态 + 余额 + 计划状态）
  各自在单个 `TransactionRunner.inTransaction {}` 中完成。
- **草稿口径**：`PENDING` 流水不进统计、不进余额，只有「待确认」入口与明细列表可见
  （与 `docs/DATA-MODEL.md` 第 5 节一致）。
- **提醒复用**：应用内卡片与后台通知走同一个 `ReminderCalculator`，避免两套口径。
- **月末兜底**：目标日超出当月天数时取当月最后一天（如 1/31 的月规则在 2 月落到 2/28 或 2/29）。

## 6. 风险

| 风险 | 说明 | 缓解 |
|---|---|---|
| 通知权限 | `POST_NOTIFICATIONS` 被拒或后台限制 | 应用内提醒卡始终可用；未授权时通知静默跳过 |
| WorkManager 版本 | 本机缓存为 2.7.1，旧版本 API 差异 | 只用 `PeriodicWorkRequest` / `CoroutineWorker` 等稳定 API；必要时升版本并记录到 DEV-NOTES |
| 月末与闰年边界 | 2/29、4/31 等推算错误 | 单测锁定边界用例，推算统一走「当月最后一天」兜底函数 |
| 生成幂等 | 重复触发导致重复流水 | 游标 + `recurring_rule_id` 标记；重复运行单测覆盖 |
| Hilt 装配遗漏 | 新用例未提供实例导致 MissingBinding | 新增用例同步在 `:core:data` 的 `UseCaseModule` 里 `@Provides` |

## 7. 完成情况

### 模拟器走查记录（2026-09-27）

- 周期规则：在 API 34 模拟器新建 `m4_test`，金额 ¥1.00、每月规则、待确认草稿；立即生成显示“已生成 1 笔”，下次日期推进到 2026-10-27。首页待确认数量变为 1，确认前净资产不变；确认后待确认归零，账目列表出现 `m4_test`，净资产减少 ¥1.00。
- 分期还款：新建 `m4_loan`，本金 ¥1.00、1 期、0 利息。明细显示第 1 期应还 ¥1.00；从现金账户还款后变为 1/1 已还、剩余 ¥0.00，计划列表自动标记“已结清”。账目编辑页显示类型“还款”、来源现金、目标负债账户 `card`。
- 余额核对：还款前现金为 −¥8,001.00、`card` 负债 ¥1,100.00；还款后现金为 −¥8,002.00、`card` 负债 ¥1,099.00，净资产仍为 −¥9,101.00。初始历史账户与流水在本轮测试前已存在，未据此判断其为种子数据。
- 提醒：首页显示既有逾期分期与信用卡账单提醒；`m4_loan` 到期未还时显示“今天到期”，还款结清后从提醒卡消失。API 34 的 `POST_NOTIFICATIONS` 权限为已授权；本轮未强制触发每日后台 Worker，也未验证拒绝通知权限时的系统通知行为。
- 验证：`test assembleDebug` 与 `lint` 均通过；模拟器安装、创建规则、生成/确认草稿、创建计划、还款核销与余额复核均完成。

> 任务完成后在此记录：实际交付、与计划的偏差、验证命令与结果。

**状态**：已交付并验证（2026-09-27）。`.\gradlew.bat test assembleDebug` 全绿。

### 实际交付

- `:core:domain`（纯 JVM）：
  - `calculator/RecurrenceCalculator.kt`：六种频率推算，**锚点 + 序号式**（每期都由 `startAt` 与期号算出，
    避免月末漂移），`byWeekday` 用 0 = 周一 … 6 = 周日，`byMonthDay = 31` 表示月末，
    `dueOccurrences` 带 `MAX_CATCH_UP = 120` 上限。
  - `calculator/ReminderCalculator.kt` + `model/Reminder.kt`：四类提醒（分期临近 / 逾期 / 信用卡账单日 /
    还款日），`DEFAULT_WINDOW_DAYS = 7`，`stableId` 供通知去重。
  - `usecase/recurring/`：`SaveRecurringRuleUseCase`(+`RecurringRuleInput`)、
    `ObserveRecurringRulesUseCase` / `SetRecurringRuleEnabledUseCase` / `DeleteRecurringRuleUseCase`、
    `GenerateRecurringTxnsUseCase`(+`RecurringGenerationReport`)、`ObservePendingDraftsUseCase`。
  - `usecase/loan/InstallmentUseCases.kt`：`InstallmentPlanDetail`、`ObserveInstallmentSummariesUseCase`
    (+`InstallmentSummary`)、`GetInstallmentPlanDetailUseCase`、`DeleteInstallmentPlanUseCase`、
    `RepayInstallmentPeriodUseCase`。
  - `usecase/txn/DraftUseCases.kt`：`ConfirmDraftUseCase`、`DiscardDraftUseCase`。
  - `usecase/reminder/ObserveRemindersUseCase.kt`。
- `:core:database` / `:core:data`：`TxnDao.updateDraftStatus`、`InstallmentPeriodDao.listPeriodsForLedger`；
  两个仓库实现补对应方法；`UseCaseModule` 补 15 个用例的 `@Provides`。
  **未升 schema 版本、未写迁移**（表在 M1 已建）。
- `:feature:ledger`：
  - `ui/common/EnumLabels.kt`（枚举查表）、`ui/recurring/`（列表 + 编辑表单）、
    `ui/drafts/`（待确认草稿）、`ui/loan/`（列表 / 详情 + 还款 / 新建）、首页提醒卡与三个新入口。
  - `navigation/LedgerRoutes.kt` 新增 6 条路由（周期列表/编辑、草稿、分期列表/详情/新建）。
  - `strings.xml` 新增 `recurring_` / `draft_` / `loan_` / `reminder_` / `weekday_short_` 文案。
- `:app`：
  - `work/RecurringWorkEntryPoint.kt` + `work/RecurringDailyWorker.kt`：WorkManager 每日任务
    （周期生成 + 提醒通知），**不引 `hilt-work`**，用 `@EntryPoint` 取依赖。
  - `notify/ReminderNotifier.kt`：通知渠道 + 发通知（未授权静默跳过）。
  - `VipLedgerApplication`：注册渠道 + `KEEP` 策略唯一排队（次日 09:00 起）。
  - `MainActivity` / `AndroidManifest.xml`：`POST_NOTIFICATIONS` 权限与运行时申请。
  - `gradle/libs.versions.toml` 新增 `work = "2.10.5"`（本地缓存只有 2.7.1，需联网拉取）。

### 与计划的偏差

| 项 | 计划 | 实际 |
|---|---|---|
| 编辑分期计划 | 3.3 写「新建/编辑计划表单」 | 只做新建：`InstallmentCalculator` 按「计划 + 各期」一次性生成，改本金/期数需要重算全部期数，
已生成的还款流水会失去对应关系，故把「编辑」留到 v2（与「部分还款 / 提前结清」同批） |
| 提醒跳转 | 「支持跳转到对应分期 / 账户」 | 分期提醒跳分期详情；信用卡提醒跳「账户管理」（当前没有单账户页）。 |
| 后台任务时间 | 未规定 | 固定在次日 09:00 起每日一次（`PeriodicWorkRequest` 最小周期 15 分钟，这里用 1 天）。 |
| 通知文案 | 未规定 | `:app` 模块内自带 `notify_` 前缀文案（feature 模块的提醒文案不跨模块复用，避免 `:app` 依赖 feature 资源）。 |
| 工具链 | 未规定 | 新增依赖 `androidx.work:work-runtime-ktx:2.10.5`（本机 Gradle 缓存只有 2.7.1，本次联网拉取）。 |

### 计划外新增

- `ui/common/EnumLabels.kt`：周期频率 / 结束条件 / 生成方式 / 错过策略 / 计息方式 / 星期缩写的统一查表，
  避免在四个页面里各写一遍 `when`。
- `:core:domain` 的 `TxnRepository.updateDraftStatus`、`InstallmentRepository.listPeriodsForLedger`
  两个接口方法（草稿状态流转与「列表页一次取全账本期数」所需）。
- `feature:ledger` 的两个界面纯函数测试（见下），把「能不能保存 / 显示哪些字段」从 Compose 里抽出来。

### 验证命令与结果

```powershell
.\gradlew.bat test assembleDebug
```

- BUILD SUCCESSFUL；JVM 单测 **346 个用例**（test-results XML 合计，含 Android 库模块 debug/release 两遍），
  0 失败 0 错误。
- M4 相关用例：`RecurrenceCalculatorTest`(12)、`ReminderCalculatorTest`(5)、`RecurringUseCasesTest`、
  `RepayInstallmentPeriodUseCaseTest`(4)、`RecurringEditUiStateTest`(13)、`LoanEditUiStateTest`(12)。
- 手工走查（模拟器）结果见下节表格。
