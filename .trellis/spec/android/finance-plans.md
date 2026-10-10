# 分期、房贷与理财（P4）

## 1. Scope / Trigger
分期计划本金/利息/手续费分账、逐期核销与部分还款、提前结清与部分提前还款、利率变更、自定义逐期金额、旧口径差异预览与显式修正；理财金额台账（投入/赎回/收益/损失）；schema6 增量迁移与格式 6 完整快照。跨表写入必须原子执行。

## 2. Signatures
- `InstallmentRepository`（domain）+ `InstallmentRepositoryImpl`（data）；`InvestmentRepository` + `InvestmentRepositoryImpl`。
- `SaveInstallmentPlanUseCase(SaveInstallmentPlanInput)`：`origin ∈ {EXISTING, DISBURSEMENT}` + `disbursementAccountId`；`customAmounts`（逐期本金/利息/费）优先于旧口径 `customPeriodTotals`（每期总额）。
- `RepayInstallmentPeriodUseCase(RepayInstallmentPeriodInput): AppResult<List<Txn>>`（`amount=null` 表示还清该期）。
- `SettleInstallmentPlanUseCase(SettleInstallmentPlanInput)` / `PrepayInstallmentPlanUseCase(PrepayInstallmentPlanInput)`。
- `ChangeInstallmentRateUseCase(ChangeInstallmentRateInput)`；`PreviewLegacyInstallmentRepaymentsUseCase(planId)` / `CorrectLegacyInstallmentRepaymentUseCase(planId, periods?)`。
- `InstallmentScheduleRewrite.rewriteTail(plan, periods, tailStartIndex, principalReduction)` / `.changeRate(...)`：纯函数，返回重写后的期次。
- `InstallmentTxnGuard.validate(updated): AppError?`（`installment_link_invalid` / `installment_direction` / `installment_amount_locked` / `installment_over_paid`）。
- `ObserveInvestmentsUseCase(ledgerId): Flow<List<InvestmentProgress>>`；`CreateInvestmentUseCase` / `InvestMoreUseCase` / `RedeemInvestmentUseCase` / `RecordInvestmentResultUseCase` / `CloseInvestmentUseCase`。
- `InvestmentCalculator.summarize(links, txnsById)` / `.redeemable(links, txnsById)`；`InvestmentTxnGuard.validate(updated): AppError?`（`investment_link_invalid` / `investment_direction` / `investment_over_redeem`）。
- schema6 表：`installment_link` / `investment` / `investment_link`；`installment_plan` 增 `origin`/`disbursement_account_id`/`disbursed_at`，`installment_period` 增 `paid_principal_cents`/`paid_interest_cents`/`paid_fee_cents`，期次状态扩为 `PENDING`/`PARTIAL`/`PAID`。

## 3. Contracts
还款/核销按三条口径分账：本金 = `TRANSFER`（资金账户 → 负债账户），利息 = `EXPENSE`，手续费 = `EXPENSE`；单期可产生 1–3 条，金额为 0 的部分不建流水。统计只认 `EXPENSE`，因此「支出 = 利息 + 手续费」，本金转账不计收支。验收：本金 1000 + 利息 50 + 费 10 → 资金 −1060、负债账户 −1000、支出 60。每条还款流水经 `installment_link`（txn_id, plan_id, period_no, kind）关联计划与期次，`InstallmentTxnGuard` 在生产 DI 注入并在事务内约束改/删/还原。

计划来源：`origin=EXISTING`（已有负债）不重复计本金；`origin=DISBURSEMENT`（新放款）在保存事务内发一笔本金 `TRANSFER`（负债账户 → 放款到账账户）并记 `disbursed_at`。

期次状态与累计：未还余额 = 计划额 − 累计已还；部分还款按「费 → 息 → 本」顺序核销并置 `PARTIAL`，全部还清置 `PAID`，计划所有期次还清自动 `CLOSED`。

提前还款与利率变更只重写未还期次、已还期次冻结：部分提前还款金额须 ≤ 未还本金且要求不存在部分已还期次，从计划本金与未还期次本金之和同时扣减，保持余期数与还款日、按原计息方式重算；利率变更从指定未还期生效。自定义期逐期给出本金/利息/费，校验各期本金之和恰等于计划本金、各额非负。

旧数据兼容：schema6 之前已还期次既无 `installment_link` 也无累计。迁移只补齐「已还 = 累计」，绝不静默新建分账流水；详情页对这类「旧口径」期次做差异预览（旧单笔 `DEBT_REPAY` 金额 vs 新分账本金/利息/费），仅当用户显式修正时，才在同一事务内软删除原流水并替换为分账流水、回填累计。

理财台账：`investment` + `investment_link(txn_id, investment_id, kind)`，`kind ∈ {INVEST, REDEEM, INCOME, EXPENSE}`。首次投入自动建立 `INVESTMENT` 类型账户；投入/追加/赎回本金是 `TRANSFER`（不计收支），已实现收益是 `INCOME`、损失/费用是 `EXPENSE`（计入收支）。展示累计投入、在投本金（投入−赎回）、已实现收益（收益−损失），不接行情、不自动估值。

快照格式升到 6：新表缺失按空恢复（表首次出现版本 `installment_link`/`investment`/`investment_link` = 6），`installment_plan`/`installment_period` 新列按默认值（`origin=EXISTING`、累计 0）恢复。

## 4. Validation & Error Matrix
| 输入/操作 | 结果 |
|---|---|
| 还款金额 ≤ 0、超过本期未还、或该期已还清 | 拒绝，无写入 |
| 还款账户为负债账户本身 | 拒绝 |
| 提前结清/部分提前还款金额 ≤ 0；部分还款 ≥ 未还本金 | 拒绝（提示改用提前结清） |
| 存在部分已还期次时发起部分提前还款 | 拒绝 |
| 利率为负、从已还期次起改利率 | 拒绝 |
| 自定义逐期金额为负、各期本金之和不等于计划本金 | 拒绝 |
| 新放款缺少到账账户 / 到账账户为负债账户 | 拒绝，不建计划、不发本金 |
| 改分期本金流水金额/类型/目标账户、删除本金流水 | 拒绝（`installment_direction` / `installment_amount_locked`） |
| 改/删期次还款流水致该期累计超过应还 | 拒绝（`installment_over_paid`），同时按存活流水重算状态 |
| 改删部分提前还款流水 | 拒绝（金额锁定、不可删） |
| 投资/赎回流水改类型或方向；赎回超过在投本金 | 拒绝（`investment_direction` / `investment_over_redeem`） |
| 关联插入失败（如触发器） | 整单回滚，账户/计划/期次/流水/余额均不变 |
| 恢复缺新表的旧格式快照 | 新表按空、新列按默认恢复，既有账本/账户/流水保留 |

## 5. Good / Base / Bad
Good：还一期本金 1000 + 利息 50 + 费 10 → 生成 3 条关联流水，资金 −1060、负债 −1000、支出 60；部分提前还款后余期数不变、后续本金等额下调。
Base：没有分期计划/理财台账时显示空态；还款要求选择可用非负债资金账户；旧口径期次在修正前只展示差异预览。
Bad：还款只记一笔 `EXPENSE` 把本金算进支出；已有负债再发一次本金造成重复；旧数据静默重写历史；赎回超过在投本金。

## 6. Tests Required
`InstallmentScheduleRewriteTest`（部分提前还款/利率变更的纯函数边界）；`InvestmentCalculatorTest`（在投本金、可赎回上限、已实现净额）；`RepayInstallmentPeriodUseCaseTest` / `InstallmentRepaymentUseCasesTest`（分账口径、费→息→本、部分还款、结清、错误矩阵）；`InstallmentTxnGuardTest`（改删还原重算与超还拒绝）；`InvestmentUseCasesTest` / `InvestmentTxnGuardTest`（投入/赎回/收益/损失、方向与超额）；`SaveInstallmentPlanUseCaseTest`（新放款发本金、已有负债不发、自定义逐期金额与本金之和校验）；`LoanEditUiStateTest`（来源/到账账户/逐期金额解析）；`InvestmentUiStateTest`；`MigrationTest` 5→6（新表为空 + 已还期次累计回填，既有行不丢）；`LedgerDatabaseTest`（新表与索引）；`FinanceIntegrationTest`（分账验收、Guard 改删 + 重算、触发器整体回滚、新放款只计一次本金、理财流水 + 快照往返）。

## 7. Wrong vs Correct
错误：把本金当支出统计；新放款与已有负债都发本金；快照恢复不清理新表；守卫只在 UI 调用；迁移时静默拼分账流水改写历史。
正确：本金 `TRANSFER`、利息/费 `EXPENSE` 分开入账；计划来源区分已有负债与新放款；旧口径只预览、经用户显式修正才改写原流水；Guard 在事务内校验改删还原；快照按表首次出现版本容忍缺表、新列取默认。