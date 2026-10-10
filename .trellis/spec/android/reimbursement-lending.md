# 报销与借贷（P3）

## 1. Scope / Trigger
报销项目与到账、借出/借入本金与利息、到期提醒、schema5 迁移与完整快照。跨表写入必须原子执行。

## 2. Signatures
- `ReimbursementRepository` / `LendingRepository`（domain）+ `ReimbursementRepositoryImpl` / `LendingRepositoryImpl`（data）。
- `ObserveReimbursementsUseCase(ledgerId): Flow<List<ReimbursementProgress>>`；进度从关联支出与到账退款推导。
- `SaveReimbursementUseCase(id?, ledgerId, title, counterparty, remark, itemTxnIds)`；项目与多笔费用关联整体替换。
- `ReceiveReimbursementUseCase(projectId, itemTxnId, amount, accountId, occurredAt?, title): AppResult<Txn>`。
- `CloseReimbursementUseCase(id, closed)`；`ReimbursementTxnGuard.validate(updated): AppError?`（`reimbursement_item_locked` / `reimbursement_receipt_locked`）。
- `ObserveLendingsUseCase` / `CreateLendingUseCase` / `SettleLendingUseCase` / `RecordLendingInterestUseCase` / `CloseLendingUseCase`。
- `LendingTxnGuard.validate(updated): AppError?`（`lending_link_invalid` / `lending_direction` / `lending_principal_locked` / `lending_over_settle`）。
- `ReminderCalculator.reminders(..., lendings: List<LendingProgress> = emptyList(), windowDays)`。
- schema5 表：`reimbursement` / `reimbursement_item` / `reimbursement_receipt` / `lending` / `lending_link`。

## 3. Contracts
报销不新建应收账户：待报销 = 关联原支出金额 − 已到账合计，只在报销入口展示，不进入净资产，避免与应收资产重复累计。

到账生成一条 `REFUND`，`refund_of_txn_id` 指向被报销的原支出并继承其分类与 `excludeFromStats`；因此与普通退款天然共用 `RefundValidator` 的「原消费金额上限」。单笔到账 ≤ 该原支出待报销余额。

项目与费用关联 `SaveReimbursementUseCase` 在单事务内整体替换；已产生到账的原支出不能移出项目；一个支出最多属于一个项目。终止只拒绝后续到账，保留既有账目；恢复可继续到账。

借贷本金是 `TRANSFER`（资金账户 ↔ 自动建立的应收/应付账户），不计收支；利息是 `INCOME`（借出收息）或 `EXPENSE`（借入付息），计入收支。借出建 `RECEIVABLE`、借入建 `PAYABLE`，名称取对方并在重名时追加 `-2`。

剩余本金 = 原始本金 − 全部 `SETTLE` 合计（下限 0）；归零自动「已结清」，`closed` 只表示手工终止，两者都让记录不再活动。到期提醒并入 `ReminderCalculator`，应用内与后台通知同一口径。

报销/借贷关联流水的改、删、还原必须经 Guard 在事务内校验。Room 从 schema4 增量迁移到 5，禁止毁库；快照格式升到 5，旧格式缺新表按空处理。

## 4. Validation & Error Matrix
| 输入/操作 | 结果 |
|---|---|
| 到账金额 ≤ 0、超过该原支出待报销余额、或与原退款合计超过原消费 | 拒绝，无写入 |
| 重复到账、终止后到账 | 拒绝 |
| 将草稿/已删除/非支出/属于他项目的支出加入项目 | 拒绝 |
| 移出已到账的原支出 | 拒绝 |
| 改删报销支出（类型/确认态/删除）、改到账流水类型或原支出指向 | 拒绝 |
| 结清金额 ≤ 0 或超过剩余本金、已终止后结清/记利息 | 拒绝 |
| 改借贷本金金额/方向/账本、删除本金流水、累计结清超过本金 | 拒绝 |
| 关联插入失败（如触发器） | 整单回滚，账户/记录/流水/余额均不变 |
| 恢复缺少新表的旧格式快照 | 新表按空恢复，既有账本/账户/流水保留 |

## 5. Good / Base / Bad
Good：本月支出 1000，下月到账 600 → 本月支出 1000、下月冲减 600、待报销 400，净资产不被重复计入。
Base：没有报销项目时显示空态；到账前要求选择可用非负债资金账户。
Bad：报销到账另记一笔收入；为待报销再建应收账户造成净资产虚增；把借出本金算进收支。

## 6. Tests Required
`ReimbursementCalculatorTest` / `LendingCalculatorTest`（剩余本金、结清上限、待报销）；`ReimbursementUseCasesTest` / `LendingUseCasesTest`（到账上限与重复、终止、守卫、单事务）；`ReminderCalculatorTest`（借贷到期/逾期、已结清与已终止不提醒）；`MigrationTest` 1–4→5 且新表为空；`LedgerDatabaseTest` 新表存在与索引；`ReimbursementLendingIntegrationTest`（跨月口径、触发器回滚、快照恢复、旧格式兼容）。

## 7. Wrong vs Correct
错误：到账直接改原支出金额；报销再建应收账户；守卫只在 UI 调用；快照恢复不清理新表。
正确：到账是关联原支出的 REFUND；待报销独立推导；Guard 在事务内校验改删还原；快照格式版本按表首次出现版本容忍缺表。
