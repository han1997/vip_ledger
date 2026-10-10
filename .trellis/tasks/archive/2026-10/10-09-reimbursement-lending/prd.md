# P3 报销与借贷

Status: implementation started 2026-10-10; decisions recorded below.

- 已有支出创建报销项目，支持多费用、对象、备注、凭证、部分/全部到账及终止。到账关联原支出，使用退款方向并标明报销用途，继承原分类及统计排除设置。退款+报销共用原消费金额上限。待报销独立展示、不重复成为应收资产。多费用分配原子提交。
- 借出/收回/借入/归还、部分结清、利息、到期提醒；本金在资金与应收应付账户间流转，利息计收支。原始/剩余本金、结清记录、关联流水。
- 验收：本月支出1000，下月到账600：本月支出1000，下月冲减600，待报销400；重复到账和超额到账拒绝。

See docs/FEATURE-ROADMAP.md for shared contracts, acceptance and delivery requirements.

## 本批实施决策（2026-10-10）

### 报销
- 不新建应收账户：报销项目独立展示「待报销」，不进入净资产，避免与应收资产重复累计。`reimbursement` + `reimbursement_item`（关联已有支出）+ `reimbursement_receipt`（关联到账退款流水）。
- 到账 = 生成一条 `REFUND` 流水，`refund_of_txn_id` 指向被报销的原支出，继承原分类与 `excludeFromStats`，同账本；因此与普通退款天然共用「原消费金额上限」校验（`RefundValidator` 汇总同一原支出的全部退款）。
- 到账按原支出逐笔分配：单笔到账金额 ≤ 该原支出可用余额；项目待报销 = 全部关联支出金额 − 已到账合计。重复到账、超额到账直接拒绝。终止后不再允许到账。
- 一个支出最多属于一个报销项目，但可同时有普通退款与报销到账；修改/删除/还原关联流水时，报销守卫在事务内校验类型、账本、方向与金额下限。

### 借贷
- 借出建 `RECEIVABLE` 账户，借入建 `PAYABLE` 账户（名称取对方），本金在资金账户与应收/应付账户间以 `TRANSFER` 流转；利息为 `INCOME`（收回）或 `EXPENSE`（付出），计入收支。
- `lending` + `lending_link`（txn_id → lending_id，kind：DISBURSE/SETTLE/INTEREST）记录原始本金、剩余本金与结清记录；剩余本金 = 原始本金 − 本金回归流水；归零自动结清，也支持手工结清/恢复。
- 到期提醒并入 `ReminderCalculator`（新增 LENDING_DUE / LENDING_OVERDUE），应用内提醒卡与后台通知同一口径。

### 通用
- Room 从 schema4 增量迁移到 5，禁止毁库；新表同步 JSON 与快照清单，快照格式版本升到 5，旧格式缺失新表按空处理。
- 跨表写入（项目+多费用、到账+关联、本金流转+账户+关联）全部单个事务，失败整体回滚。
- 门禁：test assembleDebug assembleRelease lintDebug + 迁移/快照回归 + 模拟器走查；本轮 bump 一次并更新 CHANGELOG 与 README/spec。

## 完成情况（2026-10-10）

- 版本 0.1.4 (5)：`version.properties` 单次 patch bump，`CHANGELOG.md` 已同步；签名 Release `fun.han1997.vipledger-v0.1.4-5-release.apk`。
- 门禁：`test assembleDebug assembleRelease lintDebug` 全绿（lint 与 assembleRelease 需分两次跑，避免 lint 分析单元测试时读取 Hilt release 生成源导致的文件缺失竞态）。
- 自动化回归（新增 37 项）：`ReimbursementCalculatorTest`、`LendingCalculatorTest`、`ReimbursementUseCasesTest`、`LendingUseCasesTest`、`ReminderCalculatorTest`（借贷提醒）、`ReimbursementUiStateTest`、`LendingUiStateTest`、`MigrationTest` 1–4→5、`LedgerDatabaseTest` 新表/索引、`ReimbursementLendingIntegrationTest`（跨月口径、触发器回滚、快照恢复、旧格式兼容）；`SnapshotArchiveTest` 字节字面量随 schema5 更新。
- 模拟器走查（emulator-5554，覆盖安装保留数据）：资产页出现「报销 待报销」与「借贷 剩余本金」入口；新建报销项目（支出 ¥100）→ 到账 ¥60 → 待报销 ¥40、首页本月支出 ¥100→¥40、现金 ¥1000→¥1060；借贷借出 ¥100（自动建应收账户 Bob）→ 部分结清 ¥40（剩余 ¥60、本金回流不计收支）→ 登记利息 ¥5（计入本月收入，剩余本金不变）；净资产 = 现金 + 储蓄 + 应收，待报销未重复计入。无崩溃。
- 未连接真机，真机安装/走查留待下次连接；模拟器唯一，直接 `adb -s emulator-5554 install -r` 覆盖安装。
