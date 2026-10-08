# 自动记账与退款

## 1. Scope / Trigger

通知、无障碍页面、本机 OCR → 独立候选 → 用户确认或自动入账。新增平台、采集路径、退款或备份字段时必须核对本契约。

## 2. Signatures

`AutoBookRepository.capture(PaymentObservation): AppResult<AutoCandidate?>`；
`confirm(id, CandidateConfirmation): AppResult<Txn>`；`ignore(id)` / `link(id, txnId)`。
`PaymentParser.parse(observation): PaymentFacts?` 是纯 JVM 解析，不调用云端。
Room schema 2：`txn.refund_of_txn_id TEXT NULL` 与 `auto_config`、`auto_binding`、`auto_candidate`、`auto_evidence`。

## 3. Contracts

- `PaymentObservation` 包含应用、来源、事件键、观测时间和临时文本；原文与截图不落库、不进日志。
- `PaymentFacts` 只保留支付事实：正数金额分、方向、商户、付款方式、单号、可空付款时间。
- 默认 `verifiedApps` 为空，真实样本验证前只产生待确认候选；五个平台枚举不代表兼容性已验证。
- 绑定键为账本 + 应用 + 规范化支付方式。必须绑定可用账户，不以名称猜测资金账户。
- 凭据键按账本隔离；单号键包含应用与交易类型。确认/忽略/关联后再次捕获不入账，修改或删除流水不删除凭据。
- 金融写入（流水、余额、候选状态）在同一个 Room 事务。候选先持久化，金融写入失败仍能重试。
- `REFUND` 使用正数金额，增加到账账户余额，在退款发生月份冲减支出；不是收入。累计关联退款不可超过原消费。
- 手工创建、修改、删除、恢复也在事务内执行 `RefundValidator.validate`，不能绕过原消费与退款约束。
- 授权与服务连接状态分开显示。OEM 设置引导不等于真机采集验收。

## 4. Validation & Error Matrix

| 条件 | 结果 |
|---|---|
| 非支付文本、关闭采集、应用未启用 | 不产生候选 |
| 金额不唯一、外币、缺绑定、方向不明、借还款 | 待确认，不改余额 |
| 明示支付时间过旧、未来或格式非法 | `TIME` 待确认 |
| 同额同方向附近事件、缺少共同可靠单号 | `DUPLICATE` 待确认，不静默吞第二笔 |
| 退款无原消费或超过原消费 | `REFUND` 待确认 |
| 金融事务失败 | 回滚流水与余额；保留候选并标记 `STORAGE` |
| 重复确认 | 失败，不重复写流水 |

## 5. Good / Base / Bad Cases

- Good：已实测应用、金额明确、账户绑定有效 → 自动入账；关联原消费的部分退款冲减当月支出。
- Base：尚未实测的平台捕获到付款结果 → 待确认；用户修改字段后确认。
- Bad：历史页面重现、跨应用同额购买、退款申请未到账 → 不直接入账。

## 6. Tests Required

- `PaymentParserTest`：金额冲突、失败、借还款、外币、支付时间、退款原单号。
- `AutoBookRepositoryTest`：重启/撤销后重放、并发捕获、跨应用重复、余额失败回滚、部分退款、历史页面。
- `MigrationTest.versionOneUpgradesWithoutLosingExistingRows`：旧账本、流水、余额保留，新关联列为空。
- `RefundAccountingTest` 与 `StatsMatchesHomeOverviewTest`：跨月退款与首页/统计/余额口径一致。
- `SnapshotArchiveTest`：旧格式恢复、新格式流式字节契约、恢复后自动记账关闭。
- 必跑 `test assembleDebug assembleRelease lintDebug`；真实平台与 OPPO 验收结果单独记录，不能由合成样本替代。

## 7. Wrong vs Correct

错误：同金额同商户就删除第二条；凭「已授权」显示服务正常；退款记收入。

正确：可靠凭据键用于幂等，疑似重复交用户确认；连接状态独立观察；退款由共享 `StatsCalculator.sumOfType(..., EXPENSE)` 冲减支出。
