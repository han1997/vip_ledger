# 周期记账、分期与提醒（M4）

> 领域逻辑在 `:core:domain`，界面在 `:feature:ledger/ui/{recurring,drafts,loan}`，
> 后台任务在 `:app`。表结构见 `docs/DATA-MODEL.md` 3.6 / 3.7，M1 已建好，
> **M4 未改表、未升 schema 版本**（改动只涉及 DAO 查询与仓库实现）。

## 周期推算：锚点 + 序号，不要「上一期 + 步长」

`RecurrenceCalculator.nextOccurrenceDate(rule, index)` 与
`dueOccurrences(rule, today)` 的每一步都由「规则起点 + 第 n 期」算出来，而不是拿上一期日期加步长：

- 反例：1/31 的月规则，1/31 → 2/28（月末兜底）→ 3/28，第三期就漂了。
- 正例：始终用 `startAt` 的年月 + `n` 个月，再按 `byMonthDay` 兜底到当月最后一天（`31` = 月末）。

同理 `byMonthDay` 超出当月天数一律取当月最后一天（2 月 29/28、4 月 30 都要覆盖）。

### 游标推进与幂等

规则上存两个游标：`generated_count`（已处理期数）与 `next_occurrence_at`（下期到期日）。

- `dueOccurrences` 只返回 `today` 及之前、且未被 `generated_count` 覆盖的期数，上限 `MAX_CATCH_UP = 120`。
- `SKIP` 只生成 `due.last()`，但**游标按 `due.size` 推进**（被忽略的旧期也要跳过），否则重复运行会重复生成。
- 到达 `UNTIL_DATE` / `COUNT` 后 `next_occurrence_at` 置 null，规则停用。
- 触发一次生成 = 一个 `TransactionRunner.inTransaction {}`：N 笔流水 + 规则游标更新同进同出。

## 草稿口径

`GenerateMode.DRAFT` 生成 `DraftStatus.PENDING` 流水：**不进统计、不进余额**，
只在「待确认」入口与明细列表可见（与 `docs/DATA-MODEL.md` 第 5 节一致）。

- `ConfirmDraftUseCase`：`PENDING → CONFIRMED` + 重算相关账户余额，同一事务。
- `DiscardDraftUseCase`：`PENDING → DISCARDED` 并软删，保留审计痕迹。
- 两个用例都先校验当前状态，对非 `PENDING` 的流水返回 `AppError.Validation`。

## 分期与还款

- 每期金额由 `InstallmentCalculator` 算出（等额本息 / 等额本金 / 先息后本 / 自定义），
  自定义方式用 `scheduleCustom`；**本金之和必须恰好等于本金**，除不尽的余额落在末期。
- `RepayInstallmentPeriodUseCase` 一个事务里做四件事：写 `DEBT_REPAY` 流水（还款账户 → 负债账户）、
  标记该期 `PAID` 并记 `paid_txn_id`、重算两个账户余额、若再无 `PENDING` 期则把计划置 `CLOSED`。
- 还款来源账户不能是该负债账户本身（校验放在用例里，界面也要过滤掉）。

## 提醒口径

`ReminderCalculator.reminders(...)` 是应用内提醒卡与后台通知的**唯一**口径来源：

- 四类：`INSTALLMENT_DUE` / `INSTALLMENT_OVERDUE` / `CARD_BILLING` / `CARD_DUE`；
- 窗口默认 7 天（`DEFAULT_WINDOW_DAYS`），`daysUntilDue` 为负表示逾期天数；
- `Reminder.stableId` 用于通知去重，同目标同到期日只提醒一次；
- 界面按 `ReminderKind` 查表拼文案，领域层不拼中文。

## 后台任务与通知（`:app`）

- WorkManager 每日任务 `RecurringDailyWorker`：先对每个账本跑一遍周期生成，再按当前账本算提醒并发通知。
- **不引 `hilt-work`**：`CoroutineWorker` 由默认 WorkerFactory 反射创建，依赖在 worker 内用
  `EntryPointAccessors.fromApplication(appContext, RecurringWorkEntryPoint::class.java)` 取
  （见 `work/RecurringWorkEntryPoint.kt`）。新依赖必须先进 `gradle/libs.versions.toml`。
- 任务用 `enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP, ...)` 唯一排队，
  初始延迟到次日 09:00；`VipLedgerApplication.onCreate` 里注册渠道 + 排队。
- `POST_NOTIFICATIONS`（API 33+）在 `MainActivity` 运行时申请；未授权时
  `ReminderNotifier.notify` 直接返回，**应用内提醒卡照常可用**。
- worker 里任何异常都吞掉并返回 `Result.success()`，避免失败重试风暴。

## 资源与文案

`feature:ledger` 的 M4 文案统一前缀：`recurring_` / `draft_` / `loan_` / `reminder_` / `weekday_short_`；
`app` 模块的通知文案用 `notify_`。理由见 architecture.md 的「资源名全局唯一」。

## 测试落点

- 纯推算 / 提醒：`RecurrenceCalculatorTest`、`ReminderCalculatorTest`（`:core:domain`）。
- 用例：`RecurringUseCasesTest`、`RepayInstallmentPeriodUseCaseTest`（幂等、事务、结清）。
- 界面纯函数：`RecurringEditUiStateTest`、`LoanEditUiStateTest`（`feature:ledger/src/test`，不依赖 Android）。
