# VipLedger 数据模型

> 配套文档：`docs/ARCHITECTURE.md`（技术架构）、`docs/PLAN.md`（范围与里程碑）。
> 数据库：Room（SQLite），`schemaVersion` 从 1 起，schema JSON 提交到版本库。
> 最后更新：2026-09-24（M0 建库）

## 1. 通用约定

- 主键：`id TEXT PRIMARY KEY`，值为 UUID v4 字符串（导入/恢复时不与本地冲突）。
- 金额：`*_cents INTEGER`，单位分，恒为正数；资金方向由 `txn.type` 决定，禁止用负数表达方向。
- 时间：`*_at INTEGER`，UTC epoch millis；「某天」语义统一用 `LocalDate`（按设备时区计算后再转 UTC 零点）。
- 布尔：`INTEGER`，0/1。
- 软删除：`deleted_at INTEGER NULL`，非空表示进入回收站；查询默认过滤。
- 账本隔离：除 `app` 级配置外，所有业务表都带 `ledger_id`，查询必须带该条件。
- 排序：列表类数据用显式 `sort_order INTEGER`，同值回退 `created_at`。
- 枚举：以 `TEXT` 存储 Kotlin 枚举名（如 `CREDIT_CARD`），禁止存序号。
- 日期列约定（按列名区分，实现见 `core/model/.../time/TimeConversions.kt`）：
  `*_date` 列存「某天」，用 **UTC 零点**的 epoch millis（`toDateColumnEpochMillis()`）；
  `*_at` 列存「时刻」，用 UTC epoch millis，按设备时区归属到 `LocalDate`。
- 金额方向的唯一例外：`ADJUST`（余额调整）允许 `amount_cents` 为负表示「调减」，
  否则无法表达把余额改小；其余类型恒为正（方向由 `type` 决定）。
- 余额口径：`account.current_balance_cents` 只累计 `draft_status = CONFIRMED` 且未软删的流水；
  草稿确认/丢弃后必须重算受影响账户的余额（`AccountBalanceSyncer`）。

## 2. 表清单

| 表 | 用途 | 里程碑 |
|---|---|---|
| `ledger` | 账本 | M1 |
| `account` | 资金账户（含借贷属性） | M1 |
| `category` | 分类（固定两级） | M1 |
| `tag` / `txn_tag` | 标签与账目关联 | M1 |
| `txn` | 账目流水（含草稿、回收站） | M1 |
| `recurring_rule` | 周期记账规则 | M1（引擎在 M4） |
| `installment_plan` / `installment_period` | 分期计划与每期 | M1（逻辑在 M4） |
| `merchant_rule` | 商户/关键字 → 分类规则 | M1（界面在 M6） |
| `attachment` | 交易附件（图片凭证）；表结构随 v1 落地，界面在 M2 | M1（表）/ M2（界面） |
| `ml_training_sample` | 端侧训练样本 | M1（训练在 M7） |
| `budget` | 预算（v2 预留，v1 不建业务逻辑） | v2 |

## 3. 表结构

### 3.1 `ledger`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | TEXT PK | |
| `name` | TEXT | 账本名，同库内唯一 |
| `icon_key` | TEXT | 内置图标键 |
| `currency_code` | TEXT | 默认 `CNY`（v1 单币种，字段预留） |
| `sort_order` | INTEGER | |
| `is_default` | INTEGER | 默认账本，全局仅一个 |
| `archived_at` | INTEGER NULL | 归档时间 |
| `created_at` / `updated_at` | INTEGER | |

### 3.2 `account`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | TEXT PK | |
| `ledger_id` | TEXT FK → ledger | |
| `name` | TEXT | 账户名，同账本内唯一 |
| `type` | TEXT | `CASH` / `BANK_DEBIT` / `CREDIT_CARD` / `VIRTUAL` / `RECEIVABLE` / `PAYABLE` / `LOAN` / `INVESTMENT` |
| `icon_key` / `color_argb` | TEXT / INTEGER | 展示用 |
| `initial_balance_cents` | INTEGER | 期初余额 |
| `current_balance_cents` | INTEGER | 余额缓存，由流水事务维护（可重算校验） |
| `credit_limit_cents` | INTEGER NULL | 信用卡/花呗额度 |
| `billing_day` | INTEGER NULL | 账单日（1–28，超范围取当月最后一天） |
| `due_day` | INTEGER NULL | 还款日（1–28） |
| `include_in_net_worth` | INTEGER | 是否计入净资产，默认 1 |
| `is_archived` | INTEGER | |
| `sort_order` | INTEGER | |
| `remark` | TEXT NULL | |
| `created_at` / `updated_at` / `deleted_at` | INTEGER | |

借贷判定：`type in (CREDIT_CARD, PAYABLE, LOAN)` 为负债类账户，余额展示为欠款。

索引：`(ledger_id, sort_order)`、`(type)`。

### 3.3 `category`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | TEXT PK | |
| `ledger_id` | TEXT FK | |
| `parent_id` | TEXT NULL FK → category.id | 为空即一级分类 |
| `name` | TEXT | 同账本同层级内唯一 |
| `kind` | TEXT | `INCOME` / `EXPENSE` / `TRANSFER` |
| `icon_key` / `color_argb` | TEXT / INTEGER | |
| `sort_order` | INTEGER | |
| `is_system` | INTEGER | 内置分类（不可删除，可隐藏） |
| `is_archived` | INTEGER | |
| `created_at` / `updated_at` / `deleted_at` | INTEGER | |

约束（领域层强校验）：`parent_id` 必须同账本、同 `kind`，且父分类自身 `parent_id IS NULL`（最多两级）。

索引：`(ledger_id, kind, parent_id, sort_order)`。

### 3.4 `tag` / `txn_tag`

`tag`：`id TEXT PK`、`ledger_id`、`name`（同账本唯一）、`color_argb`、`sort_order`、
`created_at`、`updated_at`、`deleted_at`。

`txn_tag`：`txn_id TEXT`、`tag_id TEXT`，主键 `(txn_id, tag_id)`，外键级联删除。
索引：`(tag_id)`。

### 3.5 `txn`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | TEXT PK | |
| `ledger_id` | TEXT FK | |
| `type` | TEXT | `EXPENSE` / `REFUND` / `INCOME` / `TRANSFER` / `DEBT_REPAY` / `ADJUST` |
| `amount_cents` | INTEGER | 恒 > 0；唯 `ADJUST` 允许负值表示调减（见第 1 节） |
| `account_id` | TEXT FK → account | 支出/收入的账户；转账的转出账户 |
| `to_account_id` | TEXT NULL FK → account | 转账转入账户；`DEBT_REPAY` 的还款目标账户 |
| `category_id` | TEXT NULL FK → category | 转账与调整可为空 |
| `occurred_at` | INTEGER | 账目发生时间（统计归属依据） |
| `title` / `remark` | TEXT | 标题、备注（搜索字段） |
| `source` | TEXT | `MANUAL` / `RECURRING` / `AI` / `IMPORT` / `RULE` / `AUTO` |
| `draft_status` | TEXT | `CONFIRMED` / `PENDING`（待确认草稿）/ `DISCARDED` |
| `exclude_from_stats` | INTEGER | 默认 0；为 1 时不参与统计（如账户互转） |
| `recurring_rule_id` | TEXT NULL | 由哪条周期规则生成 |
| `installment_plan_id` / `installment_period_no` | TEXT NULL / INTEGER NULL | 分期计划与期数 |
| `import_hash` | TEXT NULL | 导入去重键，唯一索引 |
| `refund_of_txn_id` | TEXT NULL | schema 2 新增，关联原消费；退款正数金额冲减发生当月支出 |
| `created_at` / `updated_at` / `deleted_at` | INTEGER | |

索引：`(ledger_id, occurred_at DESC)`、`(ledger_id, category_id, occurred_at)`、
`(ledger_id, type, occurred_at)`、`(account_id, occurred_at)`、`(to_account_id)`、
`(recurring_rule_id)`、`(installment_plan_id, installment_period_no)`、
`(ledger_id, draft_status)`、唯一索引 `(import_hash)`（部分索引，仅非空）。

搜索实现：标题/备注用 `LIKE '%' || :kw || '%'`，配合上面的时间/分类索引先缩小范围；
数据量超过十万条且实测慢时，再评估 FTS5 或应用层倒排索引。

### 3.6 `recurring_rule`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | TEXT PK、`ledger_id` | |
| `name` | TEXT | 规则名（如「房租」） |
| `txn_type` | TEXT | 同 `txn.type`（不含 `ADJUST`） |
| `amount_cents` | INTEGER | |
| `account_id` / `to_account_id` / `category_id` | TEXT | 同 `txn` 语义 |
| `tag_ids_json` | TEXT NULL | 生成流水时展开为 `txn_tag` |
| `title` / `remark` | TEXT | |
| `freq` | TEXT | `DAILY` / `WEEKLY` / `MONTHLY` / `YEARLY` / `EVERY_N_DAYS` / `EVERY_N_MONTHS` |
| `interval` | INTEGER | 步长（每 N 天/周/月/年），默认 1 |
| `by_weekday` | INTEGER NULL | 0–6（周起始为周一） |
| `by_month_day` | INTEGER NULL | 1–31；31 表示「当月最后一天」 |
| `by_month` | INTEGER NULL | 1–12（年规则） |
| `start_at` | INTEGER | 起始日期 |
| `end_type` | TEXT | `NEVER` / `UNTIL_DATE` / `COUNT` |
| `end_date` | INTEGER NULL、`max_occurrences` INTEGER NULL | |
| `generated_count` | INTEGER | 已生成次数 |
| `next_occurrence_at` | INTEGER NULL | 下次应生成时间（引擎游标） |
| `last_generated_at` | INTEGER NULL | |
| `generate_mode` | TEXT | `DRAFT`（默认，生成待确认）/ `AUTO`（自动入账） |
| `missed_policy` | TEXT | `SKIP`（默认）/ `CATCH_UP`（补记错过周期） |
| `enabled` | INTEGER | |
| `created_at` / `updated_at` / `deleted_at` | INTEGER | |

索引：`(ledger_id, enabled, next_occurrence_at)`。

### 3.7 `installment_plan` / `installment_period`

`installment_plan`：`id`、`ledger_id`、`account_id`（负债账户）、`name`、
`method`（`EQUAL_INSTALLMENT` 等额本息 / `EQUAL_PRINCIPAL` 等额本金 / `INTEREST_FIRST` 先息后本 / `CUSTOM`）、
`principal_cents`、`periods`、`annual_rate_bp`（年化基点，如 1800 = 18%）、
`upfront_fee_cents`、`per_period_fee_cents`、`first_due_date`、`due_day`、
`status`（`ACTIVE` / `CLOSED`）、`remark`、`created_at` / `updated_at` / `deleted_at`。

`installment_period`：`id`、`plan_id` FK、`period_no`、`due_date`、`principal_cents`、
`interest_cents`、`fee_cents`、`total_cents`、`status`（`PENDING` / `PAID`）、
`paid_txn_id` NULL、`paid_at` NULL；唯一索引 `(plan_id, period_no)`。

计息约定：每期利息按剩余本金与年化利率折月计算，结果四舍五入到分；
末期用「总额 − 已分摊」兜底，保证各期之和恰好等于本金（+ 利息）；
舍入误差全部落在末期并在单测中锁定。

### 3.8 `merchant_rule`

`id`、`ledger_id`、`pattern`（关键字或正则）、`match_type`（`CONTAINS` / `REGEX` / `EQUALS`）、
`target_field`（`TITLE` / `REMARK` / `ANY`）、`category_id`、`priority`（越小越优先）、
`source`（`USER` / `BUILTIN` / `LEARNED`）、`enabled`、`hit_count`、
`created_at` / `updated_at`。索引：`(ledger_id, enabled, priority)`。

### 3.9 `attachment`

`id`、`txn_id` FK、`file_name`、`mime_type`、`size_bytes`、`relative_path`
（应用私有目录 `files/attachments/<ledgerId>/` 下的相对路径）、
`created_at`、`deleted_at`。索引：`(txn_id)`。

### 3.10 `ml_training_sample`

`id`、`ledger_id`、`text`（标题 + 备注拼接）、`category_id`、`source`
（`USER_CORRECTION` / `IMPORT`）、`trained_version` NULL（已用于训练的模型版本）、
`created_at`。索引：`(ledger_id, created_at)`、`(trained_version)`。

### 3.11 `budget`（v2 预留）

`id`、`ledger_id`、`period_type`（`MONTH` / `YEAR`）、`period_start`、
`category_id` NULL（为空表示总预算）、`amount_cents`、`rollover`、`created_at` / `updated_at`。

## 4. 迁移策略

- 起始 `version = 1`；`exportSchema = true`，schema JSON 落在 `core/database/schemas/`。
- 每次改表必须：升版本号 + 写 `Migration(n, n+1)` + 加迁移测试（用 Room 的 `MigrationTestHelper`
  跑 `createDatabase(1)` 再 `runMigrationsAndValidate`）。
- 禁止 `fallbackToDestructiveMigration`（发布版会丢用户数据）；
  调试构建可开启，但必须在代码中显式标注并随发布版一起关掉。
- 新增可空列或带默认值的列优先；重命名走「建新表 → 拷数据 → 删旧表」的标准流程。

## 5. 统计口径

- 参与收支统计：`type in (EXPENSE, REFUND, INCOME)` 且 `deleted_at IS NULL` 且 `draft_status = 'CONFIRMED'`
  且 `exclude_from_stats = 0`。
- 不计入收支：`TRANSFER`、`DEBT_REPAY`、`ADJUST`（但都影响账户余额）。
- `结余 = 收入合计 − 支出合计`（同一时间区间）。
- `支出合计 = 消费 − 退款`，按各自发生时间归属；退款增加到账账户余额，不计收入。
- 时间归属：按 `occurred_at` 换算到设备时区的 `LocalDate` 归属；跨时区后重算会导致归属变化，v1 不做历史重算。
- 分类统计：默认按一级分类汇总，可展开到二级；未分类账目归入「未分类」。
- 标签统计：一笔账的多标签各计一次全额（不做金额分摊），界面需标注口径。
- 账户统计：账户净额 = 期初 + 收入 − 支出 ± 转账 ± 还款；净资产 = 计入净资产账户的净额之和。
- 常见时间段：本周/上周/本月/上月/近 7 天/近 30 天/本季度/上季度/本年/去年/自定义（周一为一周起始）。
- 草稿（`PENDING`）不计入任何统计，仅在「待确认」入口与明细列表出现。

## 6. 导入去重与一致性

- 导入去重键：`import_hash = sha1(occurred_at 分钟 + amount_cents + account_id + title)`，
  命中已有 `import_hash` 即判定为重复（导入报告单列统计）。
- 账户余额缓存：任何影响流水的写操作都在同一事务内更新 `account.current_balance_cents`；
  设置页提供「重算余额」工具，按流水全量重算并报告差异。
- 恢复备份时：先校验 `manifest.counts` 与 `db.json` 行数一致，再写入；不一致则中止并提示。


## 自动记账（schema 2）
新增 REFUND 类型（正数金额、增加到账账户余额、发生当月冲减支出）与 AUTO 来源。
`txn.refund_of_txn_id` 可空，关联原消费；不改变原消费历史发生时间。
`auto_config` 保存固定账本与开关；`auto_binding` 保存支付方式到账户绑定；
`auto_candidate` 保存独立待确认/已入账/忽略/关联候选；`auto_evidence` 保存幂等事件凭据。
候选与凭据按账本隔离，原始通知和截图不落库。流水与候选确认、余额更新必须处于同一事务。
