# 月预算、日历与首页（P1）

## 1. Scope / Trigger
预算、日期导航、首页卡片、预算通知及schema3备份。共用 StatsCalculator / TxnDayGrouper，不建立第二套收入支出计算。

## 2. Signatures
- `ObserveLocalDayUseCase(): Flow<LocalDay(date, zone)>`：订阅立即读设备时间，30秒检查，日期及时区去重。
- `ObserveBudgetUseCase(ledgerId, month, zone): Flow<BudgetReport>`；`SaveBudgetUseCase(ledgerId, month, scopeKey, amount: Money?): AppResult<Unit>`。
- `ObserveCalendarUseCase(ledgerId, month, zone): Flow<CalendarReport>`：按日汇总及已确认流水。
- `LedgerRoutes.txnEdit(ledgerId, txnId?, date?)`：date为ISO日期，仅新增采用，编辑以已存账目为准。
- Room schema3新增budget_limit/budget_alert，见 docs/DATA-MODEL.md。

## 3. Contracts
month是月份首日，scope空串为总预算，否则为本账本支出分类。读取目标月之前最后设置；null是停用、0是零额度。历史编辑在同一事务保存下一月原额度（若无覆盖）。父级包含子分类，scope独立计算。

草稿、删除、排除、转账/还本不计预算；退款冲减发生月。真实剩余/超支不夹断，图形比例仅夹到0–1。80/100阈值使用整数安全比较；账本/月/scope/阈值联合主键认领通知，无权限不认领，页面独立展示进度。

日历周一起始、按月长补足周。SavedStateHandle保存选择，“今天”清除固定日期。首页今日/七日卡下钻传实际区间。

快照/JSON写3、读1–3；旧格式缺budget表按空表，格式3缺表拒绝。临时库验证、主库事务替换、Room刷新保持；配置及提醒历史一起恢复。

## 4. Validation & Error Matrix
| 条件 | 行为 |
|---|---|
| 负额度、非首日、跨账本/收入分类 | 拒绝，无写入 |
| null额度、删除历史分类停用 | 保存停用标记 |
| 退款大于当月消费 | 负净支出，剩余增加，图形0 |
| 零额度且正支出 | 达到阈值，不除零 |
| 读写失败 | 页面重试或保留编辑输入 |
| 非法日期 | 新增回落当前日，编辑忽略 |
| 日期/时区变化 | 重建月份查询，最多30秒 |

## 5. Good / Base / Bad Cases
Good：10月消费100、退款20，总额及父子用量均80；11月额度继承、用量重算。
Base：无预算显示设置入口，不冒充零额度。
Bad：删除当前设置让旧额度复活；父子重复求和；日历新增丢日期。

## 6. Tests Required
BudgetUseCasesTest：跨月退款、父子/账本隔离、零/大金额、历史保护、四页口径、跨日时区。
CalendarUiStateTest：闰月/周日/六周、路由、金额输入。
MigrationTest：1/2→3保留原数据；BudgetSnapshotTest：额度/停用/提醒往返和旧快照。
模拟器必须完成预算保存→重启→选日→展开分类→记账→核对，不只看空页面。

## 7. Wrong vs Correct
错误：内存防重复、历史改动覆盖未来、截断负支出。
正确：Room唯一键、保留下一月旧值、显示真实金额而仅限制进度图形。
