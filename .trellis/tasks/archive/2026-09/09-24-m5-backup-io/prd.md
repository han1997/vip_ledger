# M5 备份与导入导出

> 需求走查记录：2026-09-27。依据 `docs/PLAN.md`、`docs/ARCHITECTURE.md`、`docs/DATA-MODEL.md`、`.trellis/spec/android/import-export.md` 和现有代码结构交叉核对。用户要求按里程碑继续推进；本文把仓库已有决策作为范围依据，不将其表述为本轮逐项口头确认。

## Goal

让用户可以可靠地导出/导入账单，并将本地数据库创建为可验证、可恢复的 WebDAV 快照。所有能力遵守单用户单设备、本地优先原则；恢复失败不得损坏当前主库。

## What I already know

- M0-M4 已交付，本任务依赖现有 Room 数据层、账本和账户模型、交易导出能力及 M4 完成后的主库结构。
- v1 范围包括 WebDAV 手动/定时备份与恢复、CSV/JSON 导出、支付宝/微信 CSV 及通用 CSV 导入和去重。
- WebDAV 只做单设备备份快照，不做多设备双向同步或冲突合并。
- WebDAV 传输须基于 OkHttp 手写 `PROPFIND`、`MKCOL`、`PUT`、`GET`、`DELETE` 与 Basic Auth；禁止引入 JitPack WebDAV 库。
- 快照 ZIP 至少含 `manifest.json` 与 `db.json`；manifest 保存格式版本、生成时间、行数和校验和。附件及 `ml_samples.jsonl` 可选。
- 可选口令保护使用 PBKDF2-HMAC-SHA256（至少 120,000 次迭代）和 AES-256-GCM；允许用户选择不加密。
- 恢复前先导出当前数据库；恢复数据先在临时库验证行数和校验和，验证失败不得替换或破坏原库。
- 定时备份使用 WorkManager，提供仅 Wi-Fi 约束、保留策略和可查看的备份结果日志。
- CSV 通用映射应预览前 20 行并校验必填字段；导入去重键为 `SHA1(发生时间分钟 + 金额分 + 账户 + 标题)`，报告新增、重复、失败数和失败行号，导入应原子提交或可回滚。
- M5 路线图验收关注清库后恢复数据一致，以及微信账单重复导入不重复新增。

## Requirements walk-through

| Area | Walk-through result | Evidence / disposition |
|---|---|---|
| 产品边界 | 本地单设备；WebDAV 是快照备份而非同步 | `docs/PLAN.md` D4、v1 范围 |
| 备份格式与安全 | ZIP 清单 + 数据 JSON；口令加密可选；解密/校验失败时不得触碰主库 | `.trellis/spec/android/import-export.md` WebDAV 契约 |
| WebDAV | OkHttp 实现规定方法和 Basic Auth；提供远端时间戳快照与 latest 指针 | 同上；不新增第三方 WebDAV SDK |
| 恢复 | 先本地保护当前库；临时导入并验证，再安全切换；失败保留原库 | 同上；包含数据库关闭、替换、重开及错误路径 |
| 自动备份 | WorkManager 定时执行、仅 Wi-Fi选项、保留策略、可追踪执行结果 | 同上 |
| 导入导出 | JSON/CSV 导出；支付宝/微信预设与通用列映射；预览最多 20 行、校验必填项 | 同上；保持与既有交易 CSV 格式区分 |
| 去重与错误处理 | 按既定 SHA-1 键识别重复；事务保证无半份导入；向用户报告行号与计数 | 同上 |
| UI / 环境 | 复用现有 Compose、资源字符串、单次状态回传与 SAF 文件选择器模式；具备模拟器可验收路径 | Android UI / import-export 规范；实现前再按规范索引读取具体文档 |

## Assumptions and defaults

- 沿用既有设置页/导航结构，把备份配置、立即备份/恢复、远端快照浏览、保留策略与日志放入一致的设置入口；代码勘察若发现既有设计约束，以现有模式为准。
- 备份口令、WebDAV 凭据不写入日志；凭据使用 Android 加密存储能力。若代码库已有安全存储封装则复用，否则选择 AndroidX Security Crypto 可用且与当前依赖矩阵兼容的方式。
- `latest.json` 只在快照成功上传并可校验后更新；远端删除/清理失败不得影响本地主库。
- CSV 无法解析的单行记录进入失败报告；若存在部分有效行，只有整批成功校验才提交，失败时回滚全批。
- 恢复校验、上传、下载和文件转换采用可处理大文件的流式路径；不把整份备份额外复制到内存。
- 默认保留最近 10 份远端快照（若现有产品设置/架构另有既定数值，按既定配置实现），手动触发备份不受 Wi-Fi 约束；自动备份遵守用户设置。

## Open Questions

- 无阻塞问题。格式版本、具体保留数量、调度频率或设置页位置若未被上游文档规定，按本 PRD 的兼容性默认值实现，并记录为可配置项/实现决策，不扩大为同步或多用户功能。

## Requirements

1. 提供可复用的数据库快照导出/导入服务，ZIP 中有版本化 manifest 和完整数据库数据；校验覆盖稳定序列化后的数据内容与记录数。
2. 支持 WebDAV 地址、路径、用户名、密码配置及连通性/错误反馈；实现规定 HTTP 方法和 Basic Auth，正确处理目录创建、HTTP 错误、超时和远端列表。
3. 支持手动创建备份、浏览/下载远端快照、恢复、latest 指针和保留策略；加密快照可选，错误口令/篡改数据/不支持版本均安全失败。
4. 恢复之前自动生成当前数据库安全副本；导入临时数据库并校验后再替换；失败后主库内容保持不变且可继续打开。
5. 支持 WorkManager 定时备份，用户可启用/停用、配置仅 Wi-Fi与保留数量；展示最近执行状态、时间和错误信息。
6. 提供数据库交易 CSV/JSON 导出，使用系统文件创建器，不申请宽泛存储权限；CSV 符合 Excel 兼容编码与转义约定。
7. 提供支付宝、微信 CSV 导入预设和通用 CSV 列映射向导；显示前 20 行预览，校验必填字段、账户映射和金额/时间格式。
8. 按标准导入指纹去重；重复不写入；整批具有事务原子性；报告新增/重复/失败统计和源文件行号。
9. 备份、导入、导出均有加载/成功/失败反馈；敏感凭据、口令、账单内容不得写入日志。

## Acceptance Criteria

- [ ] `./gradlew.bat test assembleDebug` 通过，相关单元/数据库/UI 测试覆盖快照校验、加密、导入去重、事务回滚及失败恢复。
- [ ] `./gradlew.bat lint` 通过，或记录并确认与本任务无关的既有问题。
- [ ] 在 Android 模拟器完成：手动备份 → 清除/替换测试数据 → 恢复 → 校验账目、数量与校验和一致。
- [ ] 在 Android 模拟器导入微信账单两次，第二次全部判定为重复且无额外记录；检查失败行号和导入报告。
- [ ] WebDAV mock/server 集成测试覆盖目录创建、上传/下载、列表、删除、Basic Auth、失败状态及 latest 更新次序。
- [ ] 加密备份正确口令可恢复；错误口令、篡改文件、未知格式版本均不能覆盖原库。
- [ ] 自动备份遵守仅 Wi-Fi 和保留策略，日志可见且不含凭据/口令。
- [ ] CSV/JSON 可经系统文件选择器导出，通用映射可预览 20 行并校验必填字段。
- [ ] 更新 `docs/PLAN.md`、相关 Android spec、journal；完成工作代码提交后再归档任务。

## Definition of Done

- 功能与测试覆盖验收标准，build、test、lint 通过；模拟器完成核心数据路径验证。
- 文档/spec 与实际数据格式、恢复安全边界及用户操作一致。
- 工作代码提交、Trellis 任务归档、journal 记录按工作流完成；已有未跟踪临时文件保持原状，不纳入提交。

## Out of Scope

- 多设备双向同步、冲突合并、服务端账号体系或服务端实现。
- 其他记账应用格式猜测解析、xlsx、外币换算。
- 云端账单托管、默认上传附件内容、自动覆盖式无确认恢复。

## Technical Notes

- 权威范围：`docs/PLAN.md`、`docs/ARCHITECTURE.md`、`docs/DATA-MODEL.md`。
- 实现约束：`.trellis/spec/android/import-export.md`；另需遵守 `.trellis/spec/android/database.md`、`ui.md`、`testing.md`、`architecture.md`、`build-and-env.md`。
- 走查阶段确认 `feature/sync` 目前仅为 Gradle 骨架；实现前需核实 Room 导出/导入 API、Room 文件切换机制、SAF 现有复用点及模拟器/WebDAV 测试条件。
- 本 PRD 不代表用户逐项确认未载明的 UI 细节；上述默认均以不破坏数据与本地优先为优先级。
