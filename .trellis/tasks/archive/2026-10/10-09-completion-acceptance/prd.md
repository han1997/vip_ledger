# P8 贯通验收

Status: delivered (automation-side); real-device items recorded in docs/ACCEPTANCE-RECORD.md.

新装/旧版升级/恢复、多账本、长驻、空态/取消/返回/重试、大字体深浅主题、十万流水查询与滚动。连续30天实际使用观察不阻塞已验收阶段交付。完成说明、模型、规范和验收记录。

## 本轮交付

- **十万流水回归**：`LargeLedgerQueryTest`（core:data，Robolectric 内存库单事务 10 万条：countByLedger/listFiltered 默认+类型+区间变体结果正确，单查询 < 10s 宽松上限；TXN_FILTER_SQL 的 typeCsv 包裹与 to 开区间契约被测试钉住）；`StatsScaleTest`（core:domain 纯 JVM：10 万条 summarize/day-grouping 口径正确）。
- **总验收记录**：`docs/ACCEPTANCE-RECORD.md` —— P0–P8 各阶段验收方式与结论、模型/规范索引、遗留真机项（P6 平台验收、P8 视觉走查、30 天使用观察）。
- 升级/恢复/多账本/长驻/空态由既有测试核对覆盖（MigrationTest 迁移链、SnapshotArchiveTest、Wealth/Lending/Recurring 等套件），未重写。
- 全门禁 test/assembleDebug/assembleRelease/lintDebug 通过；无模拟器走查（用户决定），真机项登记待办。

## What I already know（现状研究，2026-10-11）

### 已由测试覆盖的验收项（本批核对并记录，不重写）
- **新装/旧版升级**：`MigrationTest` — 迁移链 1→6 逐级无断链（`everyVersionGapHasAMigration`）、schema 导出与运行时一致、版本 1–5 各自升级保全数据、5→6 旧分期回填。
- **恢复**：`SnapshotArchiveTest` 旧格式恢复、流式字节契约、恢复后自动采集关闭。
- **多账本**：LedgerUseCases/WealthIntegrationTest（跨账本隔离）等。
- **长驻**：`RecurrenceCalculatorTest`、`GenerateRecurringTxnsUseCaseTest`、预算/到期提醒（BudgetMonitor/ReminderCalculator）测试。
- **空态/取消/返回/重试**：各 feature UiState 单测（loading/failed/empty + `TxnEdit` 未保存返回确认、趋势页重试等）。
- **深浅色/大字体**：designsystem token 化（charts.md/ui.md 契约），对比度验收在组件层——**真机走查项**。

### 真实缺口
1. **十万流水查询与滚动**：无任何大规模数据回归。可做：Robolectric in-memory Room 插入 10 万条 txn（单事务）→ 关键查询路径（`listFiltered` 列表、按日分组统计、计数）正确性 + 宽松时间上限；域层 `StatsCalculator`/`TxnDayGrouper` 10 万条聚合基准。滚动本身是 UI 行为，无模拟器验证后仅能由真机观察。
2. **总验收记录**：无统一文档。需落盘：各阶段验收方式与结论、模型与规范索引、遗留真机项（P6 平台验收、P8 视觉走查、30 天使用观察）。

### 交付约定变化（用户决定 2026-10-11）
- 不再做模拟器验证；真机验收由用户操作时补。

## Decisions

- **十万流水测试**（用户确认）：Room 数据层 + 域层双测试，宽松时间上限（兑住意外全表扫描级退化，不追精确基准）。

## Open Questions

- [ ] 十万流水测试的形态与严格度？
