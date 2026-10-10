# P4 分期、房贷、理财

Status: planned; not implemented.

- 复用计息引擎，本金/利息/手续费分开入账。计划区分已有负债与新放款，防止本金重复。
- 逐期核销、部分还款、提前结清；部分提前还款默认保持余期数、重算后续，已还不改。利率变更从指定未还期生效；自定义期提供本金/利息/费。
- 旧分期保留兼容结果，提供差异预览及用户显式修正，不静默重写历史。
- 理财为金额台账：投入/追加/赎回本金是转账，已实现收益为收入、损失/费用为支出；展示在投本金/累计投入/已实现收益，不接行情或自动估值。
- 验收：本金1000+利息50+费10，资金-1060，负债减1000，支出60；关联操作整体改删恢复。

See docs/FEATURE-ROADMAP.md for shared contracts, acceptance and delivery requirements.

## 实施决策（本批）

### 分账口径
- 还款/核销按「本金=TRANSFER（资金账户 → 负债账户），利息=EXPENSE，手续费=EXPENSE」三条流水入账，单期可产生 1–3 条；金额为 0 的部分不建流水。
- 统计只认 EXPENSE，故「支出 = 利息 + 手续费」；本金转账不计收支。验收：本金 1000 + 利息 50 + 费 10 → 资金 −1060、负债账户 −1000、支出 60。
- 每条还款流水经新表 installment_link（txn_id, plan_id, period_no, kind）关联计划与期次，InstallmentTxnGuard 在事务内约束改/删/还原，生产 DI 必注入。

### 计划来源
- installment_plan.origin ∈ {EXISTING, DISBURSEMENT}（已有负债 / 新放款）+ disbursement_account_id + disbursed_at。
- 新增 DISBURSEMENT 计划时同事务发一笔本金 TRANSFER（负债账户 → 放款到账账户），避免「已有负债」再重复计本金。

### 期次状态与累计
- installment_period 增 paid_principal_cents / paid_interest_cents / paid_fee_cents；状态扩为 PENDING / PARTIAL / PAID。
- 未还余额 = 计划额 − 累计已还；部分还款按「费 → 息 → 本」顺序核销，未还清置 PARTIAL。

### 提前还款与利率变更（纯函数重写后续期）
- 部分提前还款：金额 ≤ 未还本金，要求不存在部分已还的期次；从计划本金与「未还期次本金之和」同时扣减，按原计息方式重算未还期本金/利息，期数与还款日不变，已还期次原样保留。
- 利率变更：从指定未还期起生效，重算该期及之后的利息，历史期次金额冻结在行内。

### 自定义期
- CUSTOM 支持逐期直接给出本金/利息/费，校验各期本金之和恰等于计划本金、各额非负。

### 旧数据兼容
- schema6 之前已还的期次没有 installment_link、累计为 0：详情页对这类「旧口径」期次给出差异预览（旧单笔 DEBT_REPAY 金额 vs 新分账本金/利息/费），并提供用户显式修正：在同一事务内替换为三条分账流水并回填累计。
- 不做静默重写：仅当用户点击修正才改写。

### 理财台账（新表 investment + investment_link）
- investment(id, ledger_id, name, account_id, remark, closed, created_at, updated_at)；首次投入自动建立 INVESTMENT 类型账户（仿借贷）。
- investment_link(txn_id, investment_id, kind, created_at)，kind ∈ {INVEST, REDEEM, INCOME, EXPENSE}。
- 投入/追加/赎回本金 = TRANSFER（不计收支）；已实现收益 = INCOME、损失/费用 = EXPENSE（计入收支）。展示累计投入、在投本金（投入−赎回）、已实现收益（收益−损失），不接行情、不自动估值。
- InvestmentTxnGuard 约束：投入/赎回流水必须保持 TRANSFER 且方向正确；赎回不得超过在投本金；收益/损失流水类型必须匹配。

### 交付面
- schema6 迁移 + schemas/6.json + 快照格式 6（表清单/表版本/字节断言同步）。
- 资产页新增「分期房贷」「理财」入口；新增 ui/investment 页面；分期详情扩展部分还款/提前结清/部分提前还款/利率变更/差异修正。
- 门禁 test / assembleDebug / assembleRelease / lintDebug 分次；模拟器覆盖安装走查。