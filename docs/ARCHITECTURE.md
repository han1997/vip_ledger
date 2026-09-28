# VipLedger 技术架构

> 配套文档：`docs/PLAN.md`（范围与里程碑）、`docs/DATA-MODEL.md`（表结构）。
> 最后更新：2026-09-24（M0 建库）

## 1. 技术栈与版本锁定

| 项 | 选择 | 备注 |
|---|---|---|
| 语言 | Kotlin 2.2.10 | 上限受 Hilt 元数据兼容性约束，见 `docs/DEV-NOTES.md` 第 3 节 |
| 注解处理 | KSP 2.2.10-2.0.2 | 与 Kotlin 2.2.10 配套，Room 也走 KSP |
| 构建 | Gradle wrapper 8.14 | 本机已缓存 `gradle-8.14-all`，避免下载 |
| Android Gradle Plugin | 8.13.1 | 本机缓存已含 |
| JVM | Java 17 toolchain | 本机 JDK 17.0.18 |
| SDK | compileSdk 36 / targetSdk 36 / minSdk 26 | 本机已装 platform 34/35/36/37 |
| UI | Jetpack Compose + Material 3 | Compose BOM 2026.06.01（compose 1.11.4 / material3 1.4.0） |
| 导航 | navigation-compose 2.9.5 | |
| 异步 | Kotlin Coroutines 1.10.2 + Flow | |
| 持久化 | Room 2.8.4 | `exportSchema=true`，schema JSON 入库 |
| 设置存储 | DataStore Preferences | 敏感值加密后再存 |
| 后台任务 | WorkManager | 周期记账、定时备份、提醒 |
| 网络 | OkHttp 4.12.0 | WebDAV 与 AI HTTP 调用都基于它 |
| 序列化 | kotlinx-serialization-json | JSON 备份与 AI 响应解析 |
| 时间 | kotlinx-datetime 0.7.1 + java.time | 存储 UTC，展示本地时区 |
| DI | Hilt 2.57.2（KSP） | 2.58 起要求 AGP 9.0.0 以上，故锁定 2.57.2；若将来冲突则回退 Koin |
| 图表 | Compose Canvas 自绘 | 不引第三方图表库 |
| 端侧推理 | 自研线性分类器（纯 Kotlin）；ONNX Runtime Android 仅在 M7 引入 | |
| 图标 | Material Symbols / 内置矢量图 | 不引第三方图标包 |

## 2. 构建配置与依赖仓库

- 依赖仓库在 `settings.gradle.kts` 的 `pluginManagement` 与
  `dependencyResolutionManagement` 中统一配置，顺序：阿里云镜像 → 官方源兜底。
  - `https://maven.aliyun.com/repository/google`（google 镜像）
  - `https://maven.aliyun.com/repository/public`（central 镜像）
  - `https://maven.aliyun.com/repository/gradle-plugin`（插件门户镜像）
  - 兜底：`google()`、`mavenCentral()`、`gradlePluginPortal()`
- 版本统一写在 `gradle/libs.versions.toml`，模块里**禁止**硬编码版本号。
- `gradle.properties`：AndroidX、Kotlin 官方代码风格、`-Xmx`、并行构建、
  `android.nonTransitiveRClass=true`。
- 不提交 `local.properties`；CI/本地通过 `ANDROID_HOME` 或环境变量定位 SDK。

## 3. 模块结构

```
:app                     组合根：Application、导航图、首页壳、DI 装配
:core:common             结果类型、错误模型、Dispatcher 提供者、日志、扩展
:core:model              纯 Kotlin 领域模型与值对象（Money、时间范围、账目类型）
:core:database           Room 实体/DAO/迁移/类型转换器、schema JSON
:core:domain             仓库接口 + 用例 + 纯计算（分期利息、统计聚合、周期推算）
:core:data               仓库实现、导入导出、备份、DataStore 设置、AI/WebDAV 客户端
:core:designsystem       Material3 主题、颜色、字体、通用组件与图表
:feature:ledger          账本/账户/分类/标签/记账/明细/周期规则的 UI 与 ViewModel
:feature:stats           搜索与统计的 UI 与 ViewModel
:feature:ai              自然语言记账、模型管理、训练开关
:feature:sync            WebDAV 备份/恢复、导入导出、设置页
:ml:classifier           端侧特征提取、线性分类器、在线训练、ONNX 加载
```

依赖方向（单向，禁止反向）：
`feature:* → core:domain | core:data | core:designsystem → core:model | core:database | core:common`；
`feature:*` 之间不互相依赖；`core:model` 与 `core:domain` 不依赖 Android 框架（纯 Kotlin，便于单测）。

新增模块的时机：里程碑需要时再在 `settings.gradle.kts` 注册，避免空模块堆积。

## 4. 分层与代码约定

- UI 层：每个屏幕一个 `XxxScreen`（Compose，无业务逻辑）+ `XxxViewModel` +
  `XxxUiState`（`data class`，不可变）+ `XxxAction`（密封接口/枚举）。
  ViewModel 只暴露 `StateFlow<XxxUiState>` 与 `onAction(action)`。
- 领域层：`UseCase` 用 `operator fun invoke(...)`，输入输出均为 `core:model` 类型；
  跨表写操作必须在单个 `@Transaction` 或 `database.withTransaction {}` 内完成。
- 数据层：`Repository` 接口定义在 `core:domain`，实现放 `core:data`；
  读用 `Flow`，写用 `suspend`。
- 时间：领域层用 `kotlinx.datetime.Instant` / `LocalDate`；数据库存 epoch millis。
- 金额：一律 `Money`（`Long` 分），禁止 `Double`/`Float` 参与金额计算。
- 命名：实体 `XxxEntity`，DAO `XxxDao`，表名与列名单词用下划线小写。
- 注释与文档使用中文；代码标识符用英文。

## 5. 数据流

```
Compose Screen ──Action──▶ ViewModel ──UseCase──▶ Repository(接口)
                                                    │
                                        Room DAO / DataStore / OkHttp(WebDAV, AI)
                                                    │
Compose Screen ◀──StateFlow<UiState>────────────────┘
```

写路径统一走 `:core:domain` 的用例，保证「改余额 + 写流水 + 记标签」这类操作原子化。

## 6. AI 管线（自然语言记账）

三级回退，按顺序尝试。注意「降级」的触发是**产出不足**而非「彻底失败」：云端在规则层产出零条目、
有字段未识别、或整体置信度非 HIGH 时补位——只按「零条目」判定，会让「昨天午饭 35 块」这类句子
永远走不到云端（规则层总能抽出金额），开关打开也看不到 AI 起作用。

1. **规则层（永远可用，离线）**：正则抽金额（`12` / `12.5` / `12块5`）、日期（今天/昨天/上周三/3月5日）、
   账户关键词；商户规则表 `merchant_rule` 命中直接定分类。
2. **端侧分类器**：把「标题 + 备注」喂给 `:ml:classifier`，输出分类候选与置信度
   （低于阈值不出建议）。
3. **云端 LLM（可选，默认关闭联网）**：调用 `POST {baseUrl}/chat/completions`，
   要求返回严格 JSON（`amountCents`、`type`、`categoryId`、`accountId`、`occurredAt`、`title`、`remark`、
   `confidence`）；用**降级阶梯**约束：基线 `response_format={"type":"json_object"}` + 提示词内嵌
   schema，strict `json_schema` 只作升级档（不少 OpenAI 兼容服务对不认识的 `response_format`
   直接 400，硬失败会打穿「云端失败可回退」这条验收）；解析失败只做**一次**提示式修复重试，
   全程 ≤2 次网络调用。
   **相对日期一律由规则层负责**：实测 5 个本地模型无一次算对「昨天/上周三」，模型给的日期只在
   规则层未识别出日期时参考并需做单位与区间校验。响应的围栏/` thinking` 包裹必须容错抽取——
   实测两个不同服务商都会加围栏。

结果统一落成**待确认草稿**（`transaction.draftStatus = pending`），用户确认或修正后入库；
每次修正写一条 `ml_training_sample`，供端侧训练与 Python 脚本导出使用。
云端调用需要的 `baseUrl` / `apiKey` / `model` 由用户在设置页填写，`apiKey` 加密存储。

## 7. 端侧分类器（`:ml:classifier`）

- 特征：字符 2–3 gram → 哈希到 1<<18 桶的 TF 向量（纯 Kotlin，无模型下载）。
- 模型：多项式逻辑回归（softmax），权重为 `FloatArray`，序列化为 JSON（含词表版本、分类标签表）。
- 推理：`classify(text): List<CategoryScore>`，取 top-3 且置信度 > 阈值的候选。
- 在线训练：`train(samples, epochs, lr)`，增量 SGD；触发条件为「自动训练」开关开启
  （**默认关闭**）且样本数 ≥ 30；训练在 `Dispatchers.Default` 上跑，完成后原子替换权重文件。
- 外部模型：支持导入 `.onnx`（M7 引入 ONNX Runtime Android），tokenizer 配置随模型文件一起导入；
  导入后可设为默认推理后端，与线性模型共存切换。
- 与 Python 脚本同构：`tools/trainer/` 用相同特征与模型结构训练，导出 ONNX + 词表 JSON。

## 8. WebDAV 备份格式

- 客户端：`OkHttp` 手写 `PROPFIND` / `MKCOL` / `PUT` / `GET` / `DELETE`（Basic 认证），
  避免引入 jitpack 依赖。
- 远端布局：`{baseUrl}/{root}/` 下存放
  `vipledger-backup-YYYYMMDD-HHmmss.zip` + `latest.json`（记录最新快照文件名、大小、校验和、版本）。
- 快照内容（zip）：
  - `manifest.json`：`schemaVersion`、`appVersion`、`createdAt`、`deviceId`、`ledgerIds`、
    `counts`（各表行数）、`sha256ofDb`。
  - `db.json`：全表导出（行数可控时优先可读性，便于跨版本迁移）。
  - `attachments/`：交易附件（默认不上传，设置里可开）。
  - `ml_samples.jsonl`：训练样本（可选）。
- 加密：口令 → PBKDF2-HMAC-SHA256（≥ 120k 迭代，随机盐）→ AES-256-GCM 分块流式加解密；
  zip 整体加密，`manifest.json` 是加密流的第一段。允许用户选择不加密。
- 保留策略：默认保留最近 10 份，可配「每日一份 + 每周一份」。
- 恢复：列出远端 → 选中 → 校验 checksum → 事务性写入（先写临时库再替换）；
  **覆盖前强制先导出当前库到本地**。
- 定时：WorkManager 周期任务，可选「仅 WiFi」；失败重试并记录到设置页的备份日志。

## 9. 导入导出

- 导出 CSV：UTF-8 带 BOM，列顺序
  `日期,类型,金额,分类,子分类,标签,账户,目标账户,标题,备注,来源`；
  金额为两位小数字符串。导出 JSON：直接序列化领域模型，供备份与迁移。
- 导入：`Importer` 接口 + 预设实现。内置支付宝账单 CSV、微信支付账单 CSV；
  通用 CSV 走映射向导（选列→字段、预览前 20 行、必填字段校验）。
- 去重键：`sha1(发生时间分钟 + 金额分 + 账户 + 标题)`，导入报告展示「新增 / 重复 / 失败」计数。
- 其他记账 App 格式：以 `Importer` 插件形式预留，拿到真实样本后再实现。

## 10. 安全与隐私

- 敏感配置（AI `apiKey`、WebDAV 口令、备份口令）用 Android Keystore 派生密钥
  AES-GCM 加密后写入 DataStore；禁止明文落盘与日志输出。
- 账目数据只存本机 Room；v1 不联网上传账目，唯一的联网出口是用户配置的 AI 端点与 WebDAV 地址。
- AI 联网开关默认关闭；开启时明确提示「账目文本会发送到该端点」。
- 通知使用权与短信权限在 v2 引入，且默认端侧解析。
- Release 构建开启 `minifyEnabled` + `shrinkResources`，保留 Room/Hilt 必要规则。

## 11. 测试策略

- JVM 单测（主力）：金额与格式化、分期四种计息算法与舍入、周期规则推算与补记、
  统计聚合口径、搜索条件拼装、导入解析与去重、备份 manifest 与加密往返、分类器训练与推理。
- Room：DAO 测试 + 每个 `Migration` 的迁移测试（`room-testing`），schema JSON 必须入库。
- 网络：`MockWebServer`（OkHttp，已缓存）模拟 WebDAV 的 PROPFIND/PUT/GET 与 AI 端点，
  覆盖鉴权失败、超时、500、非法 JSON。
- UI：Compose 测试覆盖记账编辑保存、搜索过滤、统计时间段切换。
- 手工验收：模拟器（AVD `nordic-ui-api34`）走查每个里程碑的验收清单。

## 12. 常用命令

```powershell
./gradlew assembleDebug                 # 编译调试包
./gradlew testDebugUnitTest             # JVM 单测
./gradlew connectedDebugAndroidTest     # 连接模拟器跑仪器测试
./gradlew lintDebug                     # 静态检查
```

模拟器：`emulator -avd nordic-ui-api34`（本机已存在，API 34）。
Trellis 脚本统一用 `py -3`（本机 `python` 是 3.8-32bit，`py -3` 是 3.12）。
