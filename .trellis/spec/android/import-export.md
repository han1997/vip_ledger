# 导入导出与备份

## 导出格式

- CSV：UTF-8 带 BOM，列顺序
  `日期,类型,金额,分类,子分类,标签,账户,目标账户,标题,备注,来源`；金额为两位小数字符串。
  由 `core:domain` 的 `BuildBackupCsvUseCase` 产出（`HEADER` 为该列序）。
- JSON：**整库表数据 JSON**，结构 `{"tables": {"<表名>": [{"<列名>": 值}, ...]}}`，
  表名与列名取 SQLite 实际名字，与 `docs/DATA-MODEL.md` 一致 —— **不是**领域模型的 camelCase 字段名。
  M5 的落地方式是 `core:data` 的 `SnapshotJsonCodec`，它同时供用户导出与快照 `db.json` 使用，两者字节级同构。
  「备份 JSON」与「快照」的差别只在快照多一层 manifest + 校验和 + 可选加密，见下节。
  BLOB 列按 `Base64(NO_WRAP)` 文本写出，读回时必须解码 —— 见文末坑 1。
- 导出文件默认写到应用私有目录的 `files/export/`，再通过系统分享或 SAF 让用户取走。

## 界面侧导出（SAF `CreateDocument`，M3 起）

界面导出（搜索/下钻的 CSV、统计页的 PNG）一律走 `ActivityResultContracts.CreateDocument`：
零存储权限、零 FileProvider。流程固定为「一次性状态 + 回传」，不要在 Screen 里直接调仓库或持有 `Context`：

```kotlin
// Screen：内容先搬到本地 remember，再弹选择器；回调里才能拿到内容去写盘
var pendingCsv by remember { mutableStateOf<String?>(null) }
val createDocument = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument(CSV_MIME),
) { uri ->
    val csv = pendingCsv
    pendingCsv = null
    onAction(SearchAction.ExportFinished(uri != null && csv != null && writeTextToUri(context, uri, csv)))
}

LaunchedEffect(state.pendingExport) {
    val export = state.pendingExport ?: return@LaunchedEffect
    pendingCsv = export.csv
    createDocument.launch(export.fileName)
    onAction(SearchAction.ConsumeExport)   // 立刻消费，避免重组后重复弹窗
}
```

契约要点：

- UiState 上的一次性字段：`pendingExport: CsvExport?`（`CsvExport(fileName, csv)`，见 `feature:stats/ui/common/ExportNaming.kt`）、
  统计页另有 `pendingImageExport: String?`（文件名）。VM 只产出内容与名字，**不碰 `Uri`**。
- 内容必须在 Screen 的 `remember` 里暂存一份：`pendingExport` 在 launch 之后就被消费清空了，
  而 launcher 回调（用户选完路径）可能晚很多才执行。回调里只认本地这份 + 回调传回的 uri。
- 用户取消（`uri == null`）与写盘失败一样回传 `success = false`（界面提示导出失败）。
- 结果回传 `XxxAction.ExportFinished(success: Boolean)`，失败只提示不抛异常。
- 文件名用 ASCII 前缀 + 日期：`vipledger-{prefix}-yyyyMMdd.csv|.png`
  （`csvSuggestedFileName` / `imageSuggestedFileName`）。中文文件名在部分文件管理器/Provider 上会乱码或保存失败。
- 写盘封装在 `feature:stats/ui/common/UriWriter.kt`：`writeTextToUri` / `writeImageToUri` 用
  `runCatching` 把任何异常收敛成 `false`，界面提示「导出失败」/「导出失败，可改用导出 CSV」。
  `:feature:sync` 有一份**同契约**的镜像（`feature/sync/ui/common/UriWriter.kt` + `ExportNaming.kt`），
  因为 `feature:*` 之间禁止互相依赖、下沉到 core 又会牵动 `feature:stats` 的调用点。
  **再有第三个 feature 需要它时，就该下沉到 `core:designsystem`**（见 architecture.md 的共享件规则）。

## 导出内容

- **CSV**：`BuildTxnCsvUseCase` 产出文本。规则见 `core:domain/calculator/CsvWriter.kt`：
  `\r\n` 行尾；字段含 `,` / `"` / 换行时用双引号包裹并把 `"` 转义成 `""`；
  BOM（`\uFEFF`）只在文件开头写一次，让 Excel 双击不乱码。M3 明细导出的列（`BuildTxnCsvUseCase.HEADER`）：
  `日期,时间,类型,金额,账户,目标账户,分类,标签,标题,备注,计入统计,来源,状态,已删除`
  （本文件第一节的 `日期,类型,金额,分类,...` 是 M5 备份/导入用的对外格式，两者不要混：
  M3 格式面向「给人看的明细」，M5 格式面向「能再导回来的账目」，列集与粒度都不同）。
- **统计图片**：PNG（`Bitmap.CompressFormat.PNG`），内容是统计卡片区域。Compose 侧用
  `rememberGraphicsLayer()` 录制（`Modifier.drawWithContent { layer.record { ... }; drawLayer(layer) }`，
  见 ui.md 的坑 6），导出时 `runCatching { layer.toImageBitmap() }`；失败提示「导出失败，可改用导出 CSV」，
  不阻塞主流程。

## 导入

- 统一实现 `Importer` 接口，内置支付宝账单 CSV、微信支付账单 CSV 预设；
  通用 CSV 走映射向导（选列到字段、预览前 20 行、必填字段校验）。
- 其他记账 App 格式以 `Importer` 插件形式扩展，拿到真实样本再实现，禁止凭猜测写解析。
- 去重键：`sha1(发生时间分钟 + 金额分 + 账户 + 标题)`，命中已有 `import_hash` 记为重复。
- 导入报告必须展示「新增 / 重复 / 失败」计数与失败样例行号。
- 导入全程包在一个事务或可回滚的分批写入中，失败不得留下半份数据。

> **`Importer` 接口尚未落地（M5 现状）**：只有 `core:data` 的 `CsvLedgerImporter`（`object`，含
> `preview` / `parse` / `presetMapping` / `fingerprint`）。支付宝/微信预设与通用映射都由它承担；
> `preset` 参数目前**不参与解析**，表头探测与列匹配共用同一份 `HEADER_CANDIDATES`（见下）。
> 等到真要接第二种账单格式时再抽接口，
> 避免按猜测设计签名（与上一条「禁止凭猜测写解析」同源）。

M5 落地的具体契约：

- 必填字段只有三个：`TIME` / `AMOUNT` / `TITLE`（`CsvMapping.missingRequired()`）。`TITLE` 缺失时回落到备注。
- 表头行探测（`headerIndex`）：按「一行命中多少个**不同**账单字段」打分（候选词即 `HEADER_CANDIDATES`），
  取最高分行。真实微信/支付宝账单表头前的「起始时间/终止时间」元数据行只命中 1 个字段，不会顶替真表头；
  最高分低于 `MIN_HEADER_MATCHES = 2` 时回退到旧的「任一单元格含 时间/日期/date」启发式，老文件不回归。
  预览与解析必须认同一行，别只改一处。
- 去重键实现为字符串 `"${occurredAt / 60_000}|$amountCents|$accountId|$title"` 的 SHA-1 十六进制。
  分钟粒度是规格的一部分，不要图省事改成秒级。
- `TxnDao.existsImportHash` 是**全库**判定：`SELECT EXISTS(... WHERE import_hash = :hash)`，
  且**不过滤 `deleted_at`**。软删除的行仍占住 `import_hash` 唯一索引，过滤掉会导致撞索引失败。
  全库不按账本过滤是安全的：`accountId` 是全局唯一 UUID，跨账本不会误判。
- 单行解析失败（日期/金额格式错、金额为 0、转账与还款类）计入 `failedLines`，不阻断整批；
  但**整批要么全提交要么全回滚**——`BackupIoManager.importCsv` 把插入与余额同步包在
  `database.withTransaction {}` 里，异常时回滚并返回一条以失败行号为主的报告。
- 金额解析：先剥掉千分位 `,` 与 `￥`/`¥`，再 `BigDecimal(...).movePointRight(2).longValueExact()`，
  避免浮点误差；无法精确转成整数分即判该行失败。

## WebDAV 备份

- 客户端基于 OkHttp 手写 `PROPFIND` / `MKCOL` / `PUT` / `GET` / `DELETE`，Basic 认证；
  不要引入 jitpack 上的第三方 WebDAV 库。
- 远端布局：`{baseUrl}/{root}/vipledger-backup-YYYYMMDD-HHmmss.zip` 加一个 `latest.json` 指针。
- 快照 zip 内容：`manifest.json`（版本、时间、行数、校验和）、`db.json`、
  可选 `attachments/` 与 `ml_samples.jsonl`。
- 加密：口令经 PBKDF2-HMAC-SHA256（不少于 12 万次迭代）派生密钥，AES-256-GCM 流式加解密；
  允许用户选择不加密。
- 恢复前强制先导出当前库到本地；恢复后校验行数与校验和，不一致则中止并保留原库。
- 定时备份用 WorkManager，支持「仅 WiFi」；每次结果写入设置页可见的备份日志。

M5 落地的具体契约（`core:data` 的 `backup/` 包）：

**快照格式**（`SnapshotArchive`）

- `FORMAT_VERSION = 2`，读侧接受 1–2；manifest 字段固定为 `formatVersion` / `createdAt` / `counts`（表名 → 行数）/ `sha256OfDb`。
- JSON 根新增 `schemaVersion: 2`；缺少版本号按 1 读取。旧格式允许缺少 `auto_*` 表与可空退款关联列。
- 新格式包含自动记账配置、绑定、候选与凭据。恢复后清空 `verifiedApps` 并关闭 `enabled`，防止换设备后直接继续入账。
- 升级导出格式时同步更新字节字面量/参考序列化/列集合断言；旧备份恢复测试独立保留，不能删除以绕过兼容性检查。
- `db.json` = `SnapshotJsonCodec` 的整库表数据 JSON（与用户 JSON 导出同构），
  表序固定为 `SnapshotArchive.TABLES`，导出时 `ORDER BY rowid`，保证同一份数据校验和稳定。
  导出为**流式写盘**：按表序逐表、逐行把 JSON 直接写进目标文件，峰值内存受一行大小约束，
  不构造整库内存对象、不生成整份 JSON 字符串；行内仍用 `org.json` 序列化以保证字节稳定。
- 加密快照是「**先写 8 字节 MAGIC `VLENC001`，再写 16 字节 salt + 12 字节 IV，之后是 GCM 密文**」的单文件流。
  读侧靠 MAGIC 判定是否加密，所以 `restore` 不需要用户先声明「这份是加密的」。
- PBKDF2-HMAC-SHA256 **150,000** 次（规格下限 12 万，留了余量），AES-256-GCM，128-bit tag。
- 口令与密钥字节用完即 `fill('\u0000')` 清零。

**恢复次序**（顺序即安全边界，不许调整）

1. 解压提取 `manifest.json` / `db.json`，条目名含 `..` 或以 `/` 开头直接拒绝（zip-slip）。
2. 校验 `formatVersion`，不认识就中止。
3. 校验 `sha256OfDb`。
4. 在**临时库**里插入并验证：`PRAGMA foreign_key_check` 无输出 + 每张表行数与 manifest 一致。
5. 生成当前库的安全副本。
6. 才在**同一事务**里清表 + 写入真实库，提交后再 `invalidationTracker.refreshAsync()`。

- 1–4 任何一步失败都必须**完全没碰过主库**。安全副本本身只读主库，不构成写入。
- 安全副本存 `filesDir/backups/`，保留最近 **5** 份（`SAFETY_COPY_LIMIT`），每次恢复只生成一份。
- 临时校验库无论成功失败都要 `close()` 并删掉 `.db` / `-wal` / `-shm`。

**远端指针与保留**

- `latest.json` 字段：`file` / `size` / `sha256` / `formatVersion`，**仅在快照 PUT 成功之后**才更新。
  上传失败时不得留下指向不存在文件的指针。
- `retain(keep)` 按文件名倒序（时间戳命名即时间序）保留前 `keep` 份，`drop(keep)` 的才删，
  所以刚上传的那份永远不会被自己删掉。
- 上传用 `asRequestBody` 流式送文件，不要 `readBytes()`。

**凭据与日志**

- WebDAV 地址/用户名/密码/口令存 `EncryptedSettingsStore`：`backup_settings` SharedPreferences 里的
  一个 AndroidKeyStore AES-GCM 加密 payload，任何情况下不落明文、不进日志。
- 备份日志 `BackupLogStore` 只保留最近 **20** 条 `{success, time, message}`；
  `message` 里只允许出现文件名或 `IOException` 文本（如 `WebDAV upload failed (500)`），
  不得拼进凭据、口令或账单内容。
- `BackupWorker` 重试上限 3 次，超过记 `failure`，不做无限重试。
- 联网权限 `INTERNET` + `ACCESS_NETWORK_STATE` 必须声明在 `:app` 的 manifest 里 ——
  `:feature:sync` / `:core:data` 作为 library 不会自动带上，漏了就是「测试全绿、真机全挂」。

## 常见坑（M5 实测踩过）

### 坑 1：BLOB 列必须在导入侧解码

导出把 `Cursor.FIELD_TYPE_BLOB` 写成 `Base64(NO_WRAP)` 文本；导入若直接把这个字符串当参数绑回去，
SQLite 会把文本原样存进 BLOB 列，`attachment` 等二进制字段**静默损坏**（不报错、校验和也对，因为
校验的是 `db.json` 而不是库内容）。导入侧必须按 `PRAGMA table_info` 的声明类型识别 BLOB 列并解码成 `ByteArray`。
新增二进制列时同步检查 `SnapshotArchiveTest` 的 BLOB 往返用例。

### 坑 2：裸 SQL 写入不会触发 Room 的失效通知

恢复走的是 `DELETE` / `INSERT` 裸 SQL，绕开 Room 的 invalidation tracker —— 事务提交了、库里数据换了，
但所有 `Flow` 观察者不会收到通知，界面继续显示**旧数据**，用户以为恢复没生效。
必须在提交后显式 `database.invalidationTracker.refreshAsync()`（`RoomDatabase.clearAllTables()` 内部也是这么做的）。

### 坑 3：`File.copyTo` 拷到自己会把自己清空

加密快照曾写成「输出到 `zipped`，最后 `zipped.copyTo(destination, overwrite = true)`」，
而加密分支里 `zipped` 就等于 `destination` —— 同一路径自我拷贝：先截断目标文件再读源文件，
**产出一份 0 字节或半截的归档**。这类 bug 在只跑非加密用例时完全不可见。
写「临时文件 → 最终文件」的搬运逻辑前，先确认两个 `File` 不是同一个路径（或干脆只传一个目的地）。

### 坑 4：`feature:*` 的资源名要加模块前缀

library 资源会被 `:app` 合并，重名即 `mergeDebugResources` 失败。
`:feature:sync` 用 `backup_` 前缀并在 `strings.xml` 顶部注释写明规则（见 architecture.md「资源名全局唯一」）。
