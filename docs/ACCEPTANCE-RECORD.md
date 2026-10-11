# 验收记录（Acceptance Record）

> 版本基线：0.1.9 (10)，2026-10-11。
> 验证方式：`test assembleDebug assembleRelease lintDebug` 全门禁 + 分层自动化测试
> （纯域 JVM / Room-Robolectric / MockWebServer）。自 2026-10-11 起**不做模拟器走查**
> （用户决定）；需要真机的验收项由用户操作后在此回填。
> 每轮应用交付 bump 一次版本并更新 `CHANGELOG.md`，安装精确匹配当前版本签名 Release。

## 阶段验收

| 阶段 | 范围 | 验证方式 | 结论 |
|---|---|---|---|
| P0 | 兼容基础（旧库 1/2→3、旧快照） | `MigrationTest` 迁移链 1→6 逐级无断链、各版本数据保全、schema 导出一致；快照旧格式恢复 | ✅ 通过 |
| P1 | 首页/预算/日历 | 领域聚合测试（`BudgetCalculator`/`TxnDayGrouper`/首页用例）、口径一致性测试（`StatsMatchesHomeOverviewTest`） | ✅ 通过（当年模拟器走查另已通过） |
| P2 | 资产/储蓄/首页配置 | `WealthIntegrationTest`（改删恢复/跨账本/回滚/校准/快照）、`HomeCardsTest`、基线迁移回归 | ✅ 通过 |
| P3 | 报销/借贷 | `ReimbursementUseCasesTest`/`LendingUseCasesTest`/`RefundAccountingTest`：支出 1000→到账 600→待报销 400、借出/部分结清/利息、退款上限 | ✅ 通过 |
| P4 | 分期/房贷/理财 | 分账引擎与还款用例 35 项回归、`MigrationTest` 5→6、`FinanceIntegrationTest`（分期 1000+50+10 → 资金 −1060/负债 −1000/支出 60；理财台账） | ✅ 通过 |
| P5 | 文字/语音/截图 | 语音：`VoiceInputState` 状态机 + `SystemSpeechTranscriber` 错误映射 + 端侧优先判定；图片：`ImageTextReader` + Photo Picker/分享链路（`ImageParseNoticeTest`）；草稿链路复用既有 AI 页回归 | ✅ 通过（模拟器/真机走查未做，见遗留项） |
| P6 | 自动记账验收（代码侧） | `AutoBookRepositoryTest`：凭据判重、忽略→重开→重放不入账、AI 同日草稿把候选挡成 DUPLICATE、`hasSimilarCandidate` 口径；平台通过条件落盘 `docs/AUTOBOOK-PLATFORMS.md` | ✅ 代码侧通过；**真机平台验收待用户操作** |
| P7 | 报表 | `AssetHistoryCalculatorTest`（基线重放/基准修正/守恒式：曲线终点 == 资产页口径）、`ChartGeometryTest` 折线几何、`AssetTrendUiStateTest`、导出命名测试 | ✅ 通过 |
| P8 | 贯通验收 | 本文 + `StatsScaleTest`/`LargeLedgerQueryTest`（十万流水）；升级/恢复/多账本/长驻/空态由上述既有测试覆盖 | ✅ 自动化部分通过；真机项见下 |

## 十万流水（P8）

- `LargeLedgerQueryTest`（core:data，Robolectric 内存库）：单事务插入 10 万条账目，
  `countByLedger`、`listFiltered`（默认全量/类型/区间变体）结果正确；时间契约设在贴近
  真实使用的**限量分页查询**（limit=100，< 10s），全量物化只验正确性（60s 兜底，
  含标签关系映射的噪声不作为回归信号）。
- `StatsScaleTest`（core:domain，纯 JVM）：10 万条 `StatsCalculator.summarize` 与
  `TxnDayGrouper.group` 口径正确（草稿/已删除/排除统计/转账不参与），同样宽松上限。
- 列表滚动与渲染是 UI 行为：留待真机观察，不在自动化范围。

## 模型与规范索引

- 数据模型：`docs/DATA-MODEL.md`（金额整数分；仅已确认、未删除、未排除的收支进统计；
  本金转账不计收支；Room 从 schema2 增量迁移至 6，禁止毁库；快照格式 1–6 兼容读）。
- 架构：`docs/ARCHITECTURE.md`（模块依赖向内：接口在 `core:domain`，实现在 `core:data`/`ml:*`）。
- 规范：`.trellis/spec/android/`（index 为入口；数据库/UI/图表/错误处理/日志/AI/自动记账/
  报销借贷/分期理财/资产储蓄/导入导出/构建环境）。
- 平台验收：`docs/AUTOBOOK-PLATFORMS.md`（五平台真实通过条件与结论）。

## 遗留真机项（不阻塞已验收阶段交付）

1. **P6 平台验收**：微信最终字段验证、淘宝取证与验收、京东精度复验——按
   `docs/AUTOBOOK-PLATFORMS.md` 的「待验证项」由用户操作真机页面与支付后回填。
2. **P8 视觉走查**：大字体、深浅主题、十万流水滚动流畅度——真机观察。
3. **连续 30 天实际使用观察**：进行中，不阻塞交付；发现的问题按常规任务修复。
