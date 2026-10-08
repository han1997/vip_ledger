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

- 拼多多 `com.xunmeng.pinduoduo.activity.NewPageActivity` 可读取文本，但不是无条件 OCR 结果页。`isPaymentPage(text, app)` 仅为 PDD 的页面路径追加「已签收 + 实付 + 支付方式 + 订单号 + 下单时间」模板，通知与其他平台不得套用。实际 `实付:,134元` 的逗号分隔只在 PDD 归一化；实付优先于单价/优惠。
- 拼多多已签收历史订单即使金额明确、平台已验证、账户已绑定也保持 `TIME/PENDING`，下单/拼单/发货时间不写入 `paidAt`；同订单号重访不新增记录，不能因物流签收推断新支付并自动改余额。

- OPPO 支付宝真实控件文本中，屏幕负号金额可暴露为 `支出20元`，且导航「账单详情」可能在遍历末尾。支付宝详情模板同时覆盖该金额描述和可见负号格式；完整主体标签包括 `收款方全称/付款方全称`。回归使用脱敏的真实控件顺序，不能只按截图手写理想相邻文本。

- 标签必须有明确边界，`付款方` 不得匹配 `付款方式`，`商家` 不得匹配 `商家订单号`。支付宝账单详情可用「商户标题→独立负号金额→交易成功」及支付时间/付款方式标签建立支出事实；不得对通知或其他平台套用。支付宝付款方式末尾 `查看扣款顺序` 属操作文案，不进入绑定键。
- 无障碍文本以深度优先遍历保留行内标签/值顺序；同节点相同 text/contentDescription 只读一次；密码子树跳过，不可见容器仍检查可见子节点；节点数与文本长度仍有上限。
- 相同应用/窗口/容器的重复状态事件复用采集会话。展开单号时仅允许补全同会话、金额与明确支付时间均相同、仍待确认且缺单号的记录；不同可靠单号必须分离。同可靠单号的未知方向记录可被明确支出结果补全。既有已处理凭据仍阻止重放；不按同金额删除历史重复记录。

- 运行时 `lastEventAt` 是「最近处理时间」，进入处理器即可更新；`lastRecordAt` 只有 `capture` 返回非空候选时更新，null/失败保留旧值。`CaptureOutcome` 明确 PROCESSING/UPDATED/UNCHANGED/FAILED；界面不得把一次尝试描述成新增记录。金额是否提取仅保留布尔值，不保留原文。

- 微信账单独立负号金额仅在页面/OCR来源、微信应用、`当前状态 → 支付成功`、`支付时间` 和交易单号同时存在时适配；优先于原价/优惠，多个不同金额或与实付标签冲突时仍待确认。不可将该模板扩展至通知或任意平台。
- `支付时间` 支持中文年月日与严格日期校验；历史时间传入仓库的 `TIME` 门禁，不能用采集时间覆盖后自动入账。
- `PENDING` 与 `POSTED` 均可发无敏感字段的状态通知；忽略/关联或幂等返回 null 不发通知。通知权限未授予时，候选仍保存在页面队列。
- 真机采集排障优先使用不干预无障碍服务的 `screencap` / `dumpsys`。本轮观察到 `uiautomator dump` 后服务重建，工具的 null root 不能证明应用无法读取；必须以应用的读取/提交时间及候选状态交叉核验。

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
