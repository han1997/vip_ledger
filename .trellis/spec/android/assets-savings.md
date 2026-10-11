# 资产、储蓄与首页布局（P2）

## 1. Scope / Trigger
资产汇总、充值账户、储蓄目标和转账关联、首页布局、schema4迁移及完整快照。跨表写入必须原子执行。

## 2. Signatures
- `ObserveAssetsUseCase(ledgerId): Flow<AssetsReport>`；同一纳入账户集合分别求正余额、负余额绝对值及净值。
- `SaveSavingsGoalUseCase(id?, ledgerId, accountId, name, target, deadline)`；`archive(id, archived)`。
- `MoveSavingsUseCase(goalId, otherAccountId, amount, deposit, title): AppResult<Txn>`。
- `SavingsTxnGuard.validate(updated): AppError?`：UpdateTxnUseCase及DeleteTxnUseCase（含restore）在事务内调用，生产DI不可遗漏。
- `HomeCardUseCases.observe/save`及HomeCardLayout.normalize/move；schema4表savings_goal/savings_link/home_card/asset_baseline。

## 3. Contracts
账户PREPAID是非负债类型。汇总仅纳入includeInNetWorth且isUsable的账户，不能只按负债类型取负数，否则现金透支与信用卡正余额会使资产减负债不等于净资产。

目标绑定本账本可用非负债账户，目标额度>0；达成从saved>=target推导。存取各生成一条普通TRANSFER，savings_link只关联txn_id（唯一），不重复改余额。一个目标有过关联后不能换目标账户；归档保留所有关联。已存进度仅取关联、已确认、未删除、同账本且方向有效的转账。

取回及通用账目改删恢复必须保持非负进度、原转账方向和目标账户。事务内重读检查，防并发超取。MoveSavingsUseCase将任何写入Failure变成异常退出外层事务，再恢复AppResult，不能返回Failure而让已创建转账提交。

目标使用Room Upsert原位更新；账目编辑使用UPDATE，避免REPLACE删除有引用的记录。账本父表采用事务内UPDATE/不存在时INSERT；不能REPLACE重命名导致子表级联删除，也不能吞同名唯一冲突。

TxnDao.writeWithTags把流水更新及标签替换放入DAO事务，SQL异常先回滚再由仓库转Failure；不能在外层用例事务内吞异常后提交已改金额。WealthIntegrationTest用fail_tag触发器覆盖这一中途失败路径。

布局是按账本、用户要求完整备份的持久配置，故home_card入Room（普通临时界面偏好仍用DataStore）。五个固定卡片，默认预算/今日/储蓄/七日，待处理隐藏；未知key忽略、缺失key补默认，顺序归一。

asset_baseline只记录可验证的实际余额快照：迁移记录旧账户，首次资产观察补缺失；已有记录不覆盖。P7 起提供资产历史曲线（不落新表）：`AssetHistoryCalculator`（core:domain 纯函数）从基线重放已确认账目逐日算资产/负债/净资产，曲线终点必须等于资产页口径（initial + 全部有效账目净额）；基准日前补记/改动的账目（`createdAt/updatedAt > capturedAt`）作为**基准修正**计入基线而不是重放；基线前已存在（创建与改动均早于采集）的账目视为已含在快照内；账户在自身基线建立前不参与曲线，曲线起点 = max(区间起点, 最早基线日)。无基线 → 空曲线，不伪造。格式4读1–4，按表首次出现版本容忍旧格式缺表；临时库校验目标/账户/流水同账本及非负进度，不只校验外键。

## 4. Validation & Error Matrix
| 输入/操作 | 结果 |
|---|---|
| 跨账本、同源目标账户、负数/零转账 | 拒绝，无写入 |
| 取回超过已存；删存入导致净额<0 | 拒绝，原流水/余额不变 |
| 关联插入失败 | 整体回滚转账、余额 |
| 改目标名称/额度、归档/恢复 | 保留关联与余额 |
| 改关联账目类型/方向/脱离目标 | 拒绝 |
| 校准到相同余额 | 不生成空调整记录 |
| 恢复跨账本关联快照 | 临时库拒绝，主库不动 |

## 5. Good / Base / Bad
Good：现金1000转入储蓄200，现金800、储蓄200、目标200、净资产1000，收支不增加。
Base：没有目标时显示创建入口，只有一个账户时明确提示存取需要两个账户。
Bad：为了存钱再加一笔收入；目标改名丢关联；两笔并发取回都按旧余额放行。

## 6. Tests Required
WealthCalculatorTest、GoalEditorTest；WealthIntegrationTest覆盖改删恢复/原位改名、跨账本、并发、失败回滚、校准、完整快照及旧格式恢复。MigrationTest覆盖1–3→4及基线原余额。HomeCardsTest实际重排/全隐藏/恢复默认，CategorySectionTest继续通过。

## 7. Wrong vs Correct
错误：父表REPLACE；在事务块里返回写入Failure但不回滚；目标另记余额。
正确：原位更新、异常退出原子事务、进度从关联转账推导。
