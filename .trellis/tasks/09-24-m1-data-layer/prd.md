# M1 数据层与领域模型 PRD

> 上游：`docs/DATA-MODEL.md`（表结构权威）、`docs/ARCHITECTURE.md`（分层与依赖方向）、
> `.trellis/spec/android/database.md`（Room 约定）。本文件只写「本次做到哪、怎么验收」。

## 1. 目标

把 `docs/DATA-MODEL.md` 第 3 节的表结构落成可运行的 Room 数据库，并在 `core:domain`
建立不依赖 Android 的领域模型、仓库接口与纯逻辑（分期计息、余额、统计聚合），
让 M2 的记账主干可以直接在用例层组装，不需要再动表结构。

## 2. 交付物

### 2.1 `:core:model`（纯 JVM）

- 补齐枚举：`RecurrenceFrequency`、`RecurrenceEndType`、`GenerateMode`、`MissedPolicy`、
  `InstallmentMethod`、`InstallmentPlanStatus`、`InstallmentPeriodStatus`、
  `RuleMatchType`、`RuleTargetField`、`RuleSource`、`MlSampleSource`。
- `time/DateRangePreset`：常见时间段（本周/上周/本月/上月/近 7 天/近 30 天/本季度/上季度/
  本年/去年/自定义），周一为一周起始（对应 `docs/DATA-MODEL.md` 第 5 节）。

### 2.2 `:core:database`（Android + KSP/Room）

- 实体：`ledger`、`account`、`category`、`tag`、`txn_tag`、`txn`、`recurring_rule`、
  `installment_plan`、`installment_period`、`merchant_rule`、`attachment`、`ml_training_sample`。
  字段、索引、唯一约束严格对齐 `docs/DATA-MODEL.md` 第 3 节。
- 枚举以 TEXT 存储（`EnumConverters`）；金额 `INTEGER` 分；时间 `INTEGER` UTC epoch millis。
- DAO：每表一个，读用 `Flow`、写用 `suspend`；查询一律带 `ledger_id`，默认过滤 `deleted_at IS NULL`。
- `LedgerDatabase`：`version = 1`、`exportSchema = true`，schema JSON 提交到 `core/database/schemas/`。
- `attachment` 表按 M2 的功能排期，但**表结构随 v1 一起建**，避免 M2 为一个纯新增表做迁移。

### 2.3 `:core:domain`（纯 JVM）

- 领域模型：与实体同构但去掉 Room 注解，使用 `Money` / `LocalDate` / 枚举。
- 仓库接口：`LedgerRepository`、`AccountRepository`、`CategoryRepository`、`TagRepository`、
  `TxnRepository`、`RecurringRuleRepository`、`InstallmentRepository`、`MerchantRuleRepository`、
  `AttachmentRepository`、`MlSampleRepository`。
- 纯逻辑：
  - `InstallmentCalculator`：等额本息 / 等额本金 / 先息后本 / 自定义，末期兜底保证各期之和等于本金。
  - `BalanceCalculator`：账户余额与净资产（含负债账户方向）。
  - `StatsCalculator`：按分类（两级）/标签/账户/月的聚合口径，见 `docs/DATA-MODEL.md` 第 5 节。
  - `CategoryTree`：两级分类的校验与汇总（父分类不存在、跨账本、超两级要拦）。
- 用例：记账增删改查、账户余额重算、分期计划生成、默认账本与内置分类初始化。

### 2.4 `:core:data`（Android）

- 实体 ↔ 领域模型映射、仓库实现（异常翻译成 `AppError`）、Hilt 模块提供数据库与各 DAO。

## 3. 验收标准

1. `.\gradlew.bat test assembleDebug` 全绿。
2. `core/database/schemas/…/1.json` 已生成并提交，字段与 `docs/DATA-MODEL.md` 一致。
3. 单测覆盖：分期四种方式（含除不尽与末期兜底）、统计口径（转账/还款/调整不进收支、
   草稿不计入、标签全额重复计）、余额方向（负债账户）、两级分类校验、DAO 的 CRUD 与过滤、
   枚举存取往返、迁移测试骨架（v1 schema 校验）。
4. 空库可完成 CRUD：DAO 测试里建账本 → 建账户/分类/标签 → 记一笔 → 读回组装。

## 4. 不做（留给后续里程碑）

- 周期规则的日期推算引擎与补记（M4）、还款核销与提醒（M4）。
- 图表与界面（M2/M3）、备份序列化（M5）、AI 解析（M6）、分类器训练（M7）。
- `budget` 表（v2 预留，不建）。

## 5. 完成情况（2026-09-24）

- 12 张表全部落地（含随 v1 一起建的 `attachment`，省掉一次纯新增表的迁移）；
  `core/database/schemas/com.han1997.vipledger.core.database.LedgerDatabase/1.json` 已提交。
- 单测：98 条 JVM 单测（`:core:model` 22 / `:core:domain` 47 / `:core:database` 13 / `:core:data` 12 /
  `:core:common` 4），`.\gradlew.bat test assembleDebug` 全绿。
- 与计划的差异与原因：
  1. 迁移测试没有用 `MigrationTestHelper`——Room 2.8 在 Robolectric 下走 SupportSQLite 驱动会报
     driver/db-name 不匹配。改成更强的两条断言：导出 schema 身份哈希 == 运行时
     `room_master_table.identity_hash`、`ALL_MIGRATIONS` 必须覆盖 1..schemaVersion 的每个缺口。
  2. 余额缓存只累计已确认流水（草稿确认后需重算），已写回 `docs/DATA-MODEL.md` 第 1 节。
  3. `ADJUST` 允许负金额表示调减，这是「金额恒为正」的唯一例外，已写进 `docs/DATA-MODEL.md`。
  4. 列表/搜索的集合条件用 CSV + `INSTR`，避免 Room 对空列表生成非法 `IN ()`。
- `:app` 已依赖 `:core:data`，Hilt 图（数据库、仓库、用例）随 `assembleDebug` 一起校验通过。