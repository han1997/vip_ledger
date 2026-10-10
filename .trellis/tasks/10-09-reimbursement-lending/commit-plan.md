# 提交计划（10-09-reimbursement-lending）

## 工作提交
`feat: add reimbursement and lending with schema5 migration`

- 领域：`core/model` `LendingEnums`；`core/domain` 报销/借贷模型、仓库接口、用例、`ReimbursementTxnGuard`/`LendingTxnGuard`、`ReminderCalculator` 借贷提醒、`WriteSupport`、`Update/DeleteTxnUseCase` 守卫注入。
- 数据：`core/database` 实体、DAO、`MIGRATION_4_5`、`EnumConverters`、`LedgerDatabase`、`schemas/5.json`；`core/data` 仓库实现、Hilt 模块、`SnapshotArchive` 格式 5、`UseCaseModule`。
- UI：`feature/ledger` 报销与借贷页面/ViewModel/路由/文案、资产页入口、`TxnEditScreen` 错误映射；`app` 借贷提醒通知与文案。
- 测试：`core/domain`（计算器/用例/提醒/Fakes）、`core/database`（迁移/DAO/索引）、`core/data`（集成/快照）、`feature/ledger`（UI 状态）。
- 文档：`README.md`、`docs/FEATURE-ROADMAP.md`、`.trellis/spec/android/reimbursement-lending.md` 及 spec index。
- 版本：`version.properties` 0.1.4 (5)、`CHANGELOG.md`。
- 任务：本任务 `prd.md` / `task.json`。

## 归档提交
`chore(task): archive 10-09-reimbursement-lending`

## 日志提交
`chore: record journal`

## 未识别文件（不纳入任何提交）
- `账单_1009122349.zip`
- `iPhoneX-Mockup2.jpg`
- `Snipaste_2026-10-09_16-55-28.png`
- `Snipaste_2026-10-09_16-55-44.png`
- `Snipaste_2026-10-09_16-56-37.png`
