# Journal - han1997 (Part 1)

> AI development session journal
> Started: 2026-09-22

---



## Session 1: M0 工程骨架与总体规划落盘

**Date**: 2026-09-24
**Task**: M0 工程骨架与总体规划落盘
**Branch**: `master`

### Summary

多模块 Gradle 骨架、阿里云镜像、版本矩阵锁定（AGP 8.13.1/Kotlin 2.2.10/Hilt 2.57.2）、docs/PLAN.md 等三份总纲落盘、10 份 android spec、18 个单测通过、Compose 空壳可在 AVD nordic-ui-api34 运行

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `7ff65a8` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 2: M1 数据层与领域模型

**Date**: 2026-09-24
**Task**: M1 数据层与领域模型
**Branch**: `master`

### Summary

Room 12 表 + 12 DAO + 导出 schema；仓库接口/实现与 Hilt 装配；分期计息四种方式、余额/净资产、统计口径、两级分类校验等纯逻辑；98 个 JVM 单测全绿（gradlew test assembleDebug）

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `ff8913c` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 3: M2 记账主干

**Date**: 2026-09-24
**Task**: M2 记账主干
**Branch**: `master`

### Summary

打通可用记账主干：领域层新增账本/账户/两级分类/标签用例与首页概览，数据层用 DataStore 记当前账本并完成 Hilt 装配，designsystem 出金额/空态/顶栏/确认框/表单组件，feature:ledger 落地首页、明细、记账编辑与四类管理页。模拟器走查中修掉三个缺陷：首页最近账目未解析名称、切转账未预选转入账户导致保存必然失败（补 TxnEditUiStateTest）、管理页失败丢失领域原因。验证：gradlew test assembleDebug 全绿（164 JVM 单测 0 失败）+ 模拟器手工验收；PRD 第 7 节与 PLAN 进度看板已更新。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `dcd7228` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 4: M3 搜索与统计

**Date**: 2026-09-27
**Task**: M3 搜索与统计
**Branch**: `master`

### Summary

搜索/统计领域层（SearchQuery/StatsReport + 搜索/报表/CSV 用例）、designsystem 下沉与自绘图表、feature:stats 搜索/统计/下钻三页与导航装配；图表规范等 6 篇 android spec 落地；gradlew test assembleDebug 全绿（200 JVM 单测 0 失败），模拟器 8 项手工验收通过。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `2d8cc6f` | (see git log) |
| `8af79db` | (see git log) |
| `2796f20` | (see git log) |
| `a7f8a45` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 5: Complete M4 recurring transactions and loans

**Date**: 2026-09-28
**Task**: Complete M4 recurring transactions and loans
**Branch**: `master`

### Summary

Completed M4 recurring rules, draft generation and confirmation, installment repayment and reminders. Verified on API 34 emulator; test assembleDebug and lint passed. Committed implementation and archived task.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `b133507` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 6: M5 backup, WebDAV snapshots and import/export

**Date**: 2026-09-28
**Task**: M5 backup, WebDAV snapshots and import/export
**Branch**: `master`

### Summary

Completed M5: encrypted snapshot backup/restore (PBKDF2+AES-256-GCM), hand-written OkHttp WebDAV client, WorkManager scheduled backup with Wi-Fi-only constraint and retention policy, SAF CSV/JSON export, and Alipay/WeChat/generic CSV import with SHA-1 dedup. This session added a streaming snapshot export that stays byte-identical to the old whole-object build (peak memory bounded by one row), fixed header detection for real WeChat/Alipay bills whose 起始时间/终止时间 preamble rows hijacked the header row, and passed the quality check (412 JVM tests, lint, assembleDebug/release). Emulator acceptance walkthrough completed against a real LAN WebDAV server: backup -> wipe -> restore left all 12 tables row-identical, the uploaded file's sha256 matched latest.json, the encrypted snapshot decrypted with manifest checksum intact, a wrong passphrase created no safety copy and left the main library byte-identical, retention pruned to the configured count, and a second WeChat import inserted nothing while reporting the transfer row as a failed line.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `bc8a2a5` | (see git log) |
| `fcc9412` | (see git log) |
| `411ca61` | (see git log) |
| `beeed43` | (see git log) |
| `966b15f` | (see git log) |
| `93d24b5` | (see git log) |
| `c326351` | (see git log) |
| `634421c` | (see git log) |
| `fd94b66` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 7: M6 AI natural-language entry

**Date**: 2026-09-28
**Task**: M6 AI natural-language entry
**Branch**: `master`

### Summary

Completed M6: offline rule-layer parser (span table + priority claiming, pure JVM, explicit today/DB candidates), CategoryClassifier seam with a :ml:classifier placeholder, OpenAI-compatible cloud client with a downgrade ladder (json_object baseline, tolerant extraction, one repair retry, <=2 calls), the AI entry page (multi-entry, unresolved fields shown and required, corrections writing ml_training_sample + LEARNED merchant rules at priority 110), a merchant-rule management page, and encrypted AI settings with a default-off network switch. Measured on real endpoints: all 5 local Ollama models got relative dates wrong (5/5) so the rule layer owns the date; response wrapping is model-dependent so tolerant extraction is mandatory; one gateway requires a browser User-Agent or it 403s; a 7B model needs ~65s per parse. Benchmarked gemma3:4b as the best speed/quality default. The emulator walkthrough caught two defects no unit test saw: withType/withAmountCents never cleared their unresolved markers (making type- or amount-unresolved drafts impossible to confirm), and account-unresolved unconditionally triggering the cloud (burning 15-60s on nearly every parse, defeating the learned-rule AC). Both fixed. Not verified end-to-end on-device: the account-exemption isolation (domain unit test with fail-without-fix evidence only) and the online MiniMax-M2 call (host curl + header unit test only).

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `83b31db` | (see git log) |
| `3db7506` | (see git log) |
| `e8d6820` | (see git log) |
| `c5f1c68` | (see git log) |
| `35cf09d` | (see git log) |
| `ed786a6` | (see git log) |
| `3f17907` | (see git log) |
| `39fa478` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 8: M7 on-device classifier and training toolchain

**Date**: 2026-09-28
**Task**: M7 on-device classifier and training toolchain
**Branch**: `master`

### Summary

Completed M7: a real on-device linear classifier (char 2-3 gram FNV-1a into 1<<18 buckets, softmax, incremental SGD, sparse versioned weights JSON, atomic replace), stale-category-id post-validation with a required fail-closed filter, class-imbalance suppression, a pure-stdlib Python trainer with a genuine cross-language isomorphism check, an auto-train switch (default off) and a model-management page. 700 tests green. Emulator walkthrough proved the acceptance criterion end-to-end: weights trained by Python were imported through SAF (version and sample count read back correctly) and the on-device classifier then suggested 餐饮 for 午饭 炒饭 25 块 with the network switch OFF and no category name in the sentence, on a sentence absent from the training set, so it generalised rather than memorised. The walkthrough also caught two user-visible wording defects: the model page used the auto-train threshold (30) in the sentence describing the per-class threshold (actually 3), putting two contradictory numbers on one screen; and the empty state promised an 导入样本 entry point that does not exist. ONNX import was deferred by decision (not in the acceptance criteria, a heavy dependency, and :ml:classifier is a pure-JVM module that cannot host an Android library). Left as a recorded TODO: no production code produces IMPORT samples, so that AC was downgraded.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `84a9c10` | (see git log) |
| `b6c2a18` | (see git log) |
| `f207c12` | (see git log) |
| `4c9b1a3` | (see git log) |
| `d44e7d5` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 9: Apply warm page background

**Date**: 2026-09-28
**Task**: Apply warm page background
**Branch**: `master`

### Summary

Added VipLedgerScaffold so every screen uses the warm theme background, and grouped the home overview into quieter cards. Debug compile passed.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `4ac3ad8` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 10: Configure release signing, rewrite README, update build spec

**Date**: 2026-09-28
**Task**: Configure release signing, rewrite README, update build spec
**Branch**: `master`

### Summary

Configured release APK signing via untracked keystore.properties; generated project keystore (keystore/vipledger-release.jks, alias vipledger); built and verified signed app-release.apk (apksigner CN=VipLedger); rewrote README with documentation rule, build commands, signing setup, and keytool/apksigner commands; updated android build-and-env.md spec (new signing convention, missing properties = unsigned-not-failing behavior, keystore backup rule) and index.md (README-as-you-work in pre-dev checklist and quality check). All acceptance criteria met; trellis-check passed (5 files consistent, no secret leaks, test assembleDebug green).

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `d9cf572` | (see git log) |
| `e304180` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 11: Finish VipLedger v1 mainline

**Date**: 2026-09-29
**Task**: Finish VipLedger v1 mainline
**Branch**: `master`

### Summary

Completed final quality verification, marked M6 AI natural-language entry complete in docs, updated Android AI spec contracts, committed the mainline bookkeeping changes, and archived the v1 umbrella task.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `6817ed7` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 12: V2 UI design system and full-screen migration

**Date**: 2026-09-29
**Task**: V2 UI design system and full-screen migration
**Branch**: `master`

### Summary

Built V2 design system in core:designsystem (VipLedgerTokens 4dp grid, accent colors with WCAG-verified pairing, tnum typography with displayHero/displayAmount, 9 new stateless components with previews, token/accent unit tests). Migrated all 21 feature screens across ledger/stats/ai/sync: HeroCard gradient net-worth card, GroupCard/ActionRow/SectionHeader unification, VipLedgerConfirmDialog(danger) for all irreversible actions, VipLedgerSnackbarHost, LoadingState, StatusBadge; tokenized spacing and added rememberLazyListState. Behavior, ViewModels, navigation, stats aggregation, charts geometry and SAF flows unchanged. trellis-check passed 41 items with 19 self-fixes (18 stale imports, 1 missing a11y semantics for stats expand). Full test+lintDebug+assembleDebug green. Captured V2 design-system conventions into .trellis/spec/android/ui.md. Note: API 34 emulator page-by-page walkthrough remains as manual acceptance; sub-agent instability required main-session implementation for batches 2-4.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `183b6f4` | (see git log) |
| `258bb0f` | (see git log) |
| `68a9957` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 13: UI/UX polish: bottom nav, home/stats/Me tabs, AI txn entry

**Date**: 2026-10-08
**Task**: UI/UX polish: bottom nav, home/stats/Me tabs, AI txn entry
**Branch**: `master`

### Summary

Resumed in-progress ui-ux-polish task; verified build green (test assembleDebug lintDebug all passed), then committed 30 modified + 4 new files across 8 logical work commits: bottom navigation bar (Home/Stats/Me tabs), home screen redesign, settings migration to Me tab, txn-edit AI entry integration with last-account recall, stats entry FAB, AI pipeline wiring, designsystem LabeledTextField extension, and spec updates for bottom-nav conventions. Emulator walkthrough was documented in the PRD continuation record (all 10 Me-tab entries open and return, AI parse preview works, dark mode readable). AI persistence write not manually verified to avoid seeding test data.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `5d01f05` | (see git log) |
| `3a6a92c` | (see git log) |
| `dedc0a5` | (see git log) |
| `cff3c18` | (see git log) |
| `615a73a` | (see git log) |
| `720513b` | (see git log) |
| `724a2fc` | (see git log) |
| `0674853` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 14: Automatic bookkeeping implementation and verification

**Date**: 2026-10-08
**Task**: Automatic bookkeeping implementation and verification
**Branch**: `master`

### Summary

Completed offline payment capture, account bindings, review queue, atomic posting and deduplication, refund accounting, schema 1-to-2 migration and legacy backup compatibility. Fixed Kotlin paidAt smart-cast compilation and updated versioned JSON assertions; added migration, historical-payment and cross-month refund regressions. Full test/assembleDebug/assembleRelease/lintDebug passed; final test/lintDebug passed with 538 tests and no failures or skips. Release signature verified and API 34 emulator entry/settings/dark-mode checks passed. Archived at the user's explicit finish-work request. OPPO X8 Ultra/Android 17/ColorOS 17 and real payment/refund samples from all five platforms remain unverified; archive does not imply device acceptance.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `6f3b58d` | (see git log) |
| `d5a2546` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 15: OPPO 真机测试收尾与支付采集修复

**Date**: 2026-10-09
**Task**: OPPO 真机测试收尾与支付采集修复
**Branch**: `master`

### Summary

按用户要求结束并归档本轮测试，不再继续支付识别调优。支付宝选定样本识别、确认入账与重放防重复通过；拼多多已签收订单待确认识别通过；京东准确率仍不足，用户暂时接受；微信最终复测、淘宝及其余全应用范围未完成，不视为全量验收通过。修复采集入口、字段边界、实付金额、幂等补全、OCR行顺序与限频重试，补交设置页/确认表单/密码遮蔽及编码构建收尾。build-13全量test/Debug/Release/lint与签名校验通过，最新Release已安装OPPO，数据和设置保留。完整结论见.trellis/tasks/archive/2026-10/10-08-oppo-full-acceptance/report.md。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `68a3099` | (see git log) |
| `09180e8` | (see git log) |
| `a40e48b` | (see git log) |
| `0fcbb23` | (see git log) |
| `2800512` | (see git log) |
| `04e02d5` | (see git log) |
| `3a03854` | (see git log) |
| `d248518` | (see git log) |
| `c149368` | (see git log) |
| `cb98cf8` | (see git log) |
| `b70f14a` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 16: 记账页交互与视觉优化

**Date**: 2026-10-09
**Task**: 记账页交互与视觉优化
**Branch**: `master`

### Summary

记账编辑页交互与视觉优化：类型自适应换行；一级分类网格且二级区插入被点父级所在行下、一次只展开一个；标签页内搜索与新建、同账本同名复用、失败保留输入；时间精确到秒（改日期保留时分秒/毫秒、手动秒级、今天/昨天实时算、AI 识别时刻优先）；单 LazyColumn 与底部单一保存、保存防重、未保存退出确认。流水显示秒并按 occurredAt/createdAt/id 稳定排序。新增 TxnEditUiStateTest/TxnEditTimeTest 与 LedgerDatabaseTest 同刻排序回归，test+assembleDebug+assembleRelease+lintDebug 全绿；README 与 android/ui、android/database spec 同步更新。真机 Compose 走查（网格插入位置/长名称/键盘布局）因无真机连接未做。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `d67169d` | (see git log) |
| `3f314f0` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
