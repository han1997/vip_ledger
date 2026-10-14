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

> **格式无关接口已落地(一木 xls 起)**:接口是 `:core:domain` 的 `BillImporter`
> (`inspect(source: ByteArray, target): AppResult<BillImportPreview>` 只解析不落库;
> `commit(preview, target, onProgress): AppResult<BillImportReport>` 落库)。共享 DTO
> (`ImportTarget` / `BillImportRow` / `BillImportPreview` / `BillImportReport` / `PendingBillCategory`
> / `PendingBillAccount`)也都在 `:core:domain.usecase.importbill`,用**领域模型**而非 xls 列表达。
> 实现放 `:core:data`(`YimuLedgerImporter`),Hilt 绑定(`@Binds`)也在 `:core:data` 的 DI 模块 ——
> `feature:*`(含导入页)只 `@Inject` 接口,**不依赖 `:core:data`**(architecture.md「接口向内、实现向外」)。
> CSV 路径**保持现状不动**:`:core:data` 的 `CsvLedgerImporter`(`object`,含 `preview` / `parse` /
> `presetMapping` / `fingerprint`)不实现该接口;支付宝/微信预设与通用映射仍由它承担,
> `preset` 参数目前**不参与解析**,表头探测与列匹配共用同一份 `HEADER_CANDIDATES`(见下);
> 待重构时再把它迁进 `BillImporter`。

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


## 一木记账账单导入(.xls / .zip,`BillImporter` 首个真实实现)

来源:`core:data/importexport/yimu/`。解析层是自写最小 BIFF8 解析器(`Ole2Reader` / `Biff8Reader` /
`YimuBillParser`),映射层是纯函数(`YimuCategoryMapper` / `YimuAccountMapper`),落库层是
`YimuLedgerImporter`(实现 `:core:domain` 的 `BillImporter`)。

**入口与容器**

- 接受 OLE2/CFB 容器内的 BIFF8 `Workbook` 流(魔数 `d0cf11e0a1b11ae1`),以及 `.zip` 包裹的同名 `.xls`
  (魔数 `504b0304`,取第一个 `.xls` 成员)。两条入口结果必须一致。
- zip 条目名含 `..` 或以 `/` 开头直接拒绝(zip-slip,与 `SnapshotArchive` 同口径)。
- 坏魔数 / 截断 / 越界 sector 链抛 `YimuFormatException`(`IllegalArgumentException` 子类),**不得 OOM**。
- `.xls` 全靠**表头列名**动态定位列(`headerIndex`),不写死列序,源文件列序漂移不会读错列。

**23 列契约**(表头即列名):`日期`(字符串 `yyyy-MM-dd HH:mm`,当**本地时间**解析)、`收支类型`(`支出`/`收入`)、
`金额`、`类别`、`二级分类`、`账户`、`账本`(恒「日常账本」)、`退款`、`优惠`、`备注`(→ `title`)、`标签`(恒 `app`)、
`报销账户/报销金额/报销明细/多币种`、`地址`、`创建用户`、`其他`、`附件1..5`。后几组全空,忽略(仅 `其他` 含
「不计入收支」时置 `excludeFromStats`)。缺 `日期`/`收支类型`/`金额`/`类别` 任一列 → 抛异常。

**三个陷阱(实测踩过)**

1. **同一一级类别混两种方向**。方向**只由「收支类型」列决定**,绝不从一级名推:一木「其他」一级 = 理财盈利(收入)
   + 理财支出(支出)+ 空(支出);「奖金 / 工资 / 礼金人情」也都是收入型一级。`YimuCategoryMapper.map` 的
   `kind` 参数就是这一列。
2. **退款列是「原单金额」,不是要找的差**。源数据金额**已经是净额**,退款 / 优惠**只写进 `remark`**(如
   `退款 29.17 · 优惠 0.02 · 地址:...`),**绝不加减回金额**。金额符号由「收支类型」决定,取绝对值。
3. **优惠是信息性的**,同上,已含在净额里。

**解析层契约(BIFF8 最小解析器 —— 全任务最易错处,改这里前先读)**

公开入口:`YimuBillParser.parse(bytes): BiffSheet`(唯一对外入口)。

- `Ole2Reader.readStream(bytes, "Workbook"): ByteArray` 解 OLE2/CFB 容器;BIFF8 的流名是
  **`Workbook`(不是 `Book`)**,小流(≥ `miniCutoff` 4096 走 FAT,否则走 miniFAT)。
- `Biff8Reader.readSheet(stream): BiffSheet` 线性扫 `[u16 id][u16 len][payload]`,**在拼接后的
  记录流上解析,不按 8224 字节切**;只认 BOF/EOF/BOUNDSHEET/SST+CONTINUE/LABELSST/NUMBER/RK/MULRK/
  ROW/BLANK/MULBLANK,其余按 payload 长度跳过。
- 🔴 **SST 跨 CONTINUE 续接**:记录 payload 上限 8224,切开处会**重写一个 options 字节**;
  必须维护 `(chunkIndex, offset)` 游标 —— 越界时跳到下一块**偏移 0 先读一个字节**当新 options
  (只有 bit0 生效),**绝不能把 payload 简单 concat**(会把标志字节当内容)。rich-text 段数组
  (`4*nrun`)与 Far-East `cbext` 块**各自独立对齐到下一块偏移 0、不重读 options**;
  跳过顺序固定为 字符 → rich-text → Far-East。宽字符且旧块末尾只剩 1 字节时,该字节**静默丢弃**。
- 字符串头 = `cch`(u16 字符数)+ 1 字节标志:bit0 = `fHighByte`(1 ⇒ UTF-16LE 每字符 2 字节;
  0 ⇒ CP1252 每字符 1 字节);bit3 = rich-text;bit2 = Far-East。
- 🔴 **单元格行号取自记录自身**:LABELSST/NUMBER/RK/MULRK/BLANK/MULBLANK 自带 `row`/`col`,
  **以它为准**;`ROW` 记录**只用于元数据**(本文件是每非空行一条,但**不得**用「最近一条 ROW」推导行号 ——
  早期研究据此得出的「row 字段不可靠」结论已作废)。`DIMENSIONS` 仅用于上界。
- RK 解码(与 `xlrd.unpack_RK` 逐位一致):bit0 ⇒ 有符号 30 位整数(`>> 2` 后符号扩展);
  否则 `Double.fromBits((rk & 0xFFFFFFFC) << 32)`;bit1 ⇒ 再 ÷100。**不要**用 `toInt()/100.0` 之类的近似写法。
- 错误一律抛 `YimuFormatException`(`IllegalArgumentException` 子类):坏魔数 / 截断 / 越界 sector
  链 / SST 索引越界 / 行列乘积超 `MAX_CELLS` —— **不得 OOM**,也不得抛不可捕获的 `OutOfMemoryError`。

**落库口径**

- `amountCents = abs(金额).movePointRight(2).longValueExact()`;**0 元行照常写 0**,直接构造 `TxnEntity` +
  `txnDao.upsert`,**不走** `TxnValidator`(它要求金额 > 0)。
- 固定字段:`source = TxnSource.IMPORT`、`draftStatus = DraftStatus.CONFIRMED`、`deletedAt = null`、`toAccountId = null`。
- 分类:目标 `kind` = 该行方向;二级先在目标父级下按 `(parentId, kind, name)` 查重,命中复用,缺失新建
  (`iconKey = null`、`isSystem = false`);二级为空时给一级 id。内置分类每账本随机 UUID,只能按名称查。
  **D9 同义复用**:语义相同就落内置二级(手机数码→数码、个护美妆→美妆、话费宽带→话费、医院→门诊、电影唱歌→电影…),
  一木特有的一律在映射父级下新建(全量样本约 30 个二级)。
  ⚠️ **「话费」不要跨父级复用**:内置「话费」挂在「通讯」下,而一木 `居家生活/话费宽带` 的映射父级是「居住」——
  同名但父级不同**不算复用**,会在「居住」下新建一个「话费」二级(符合 `CategoryTree` 的「同级同名唯一」)。
- 账户:按 `YimuAccountMapper.normalize`(去首尾空白、全角转半角、**各类括号一律压成 `(`**、间隔符转空格)
  在目标账本已有账户里判重,**命中不改名**;类型按名推断(信用卡→`CREDIT_CARD`;花呗/白条/月付→`PAYABLE`;
  微信/支付宝/零钱通/钱包→`VIRTUAL`;现金→`CASH`;公积金→`INVESTMENT`;其余→`BANK_DEBIT`);
  **`initialBalance` 一律 0**(`balanceOf = initialBalance + Σdelta`,给非 0 初值会让迁移余额凭空翻倍)。
  「账户」列为空的行落到「现金」账户。
- 幂等:`import_hash = sha1("${occurredAt / 60_000}|$amountCents|$accountId|$title")`,**分钟粒度**;文件内第 2 次
  出现的同键加 `|#n` 后缀(全量保留文件内重复行 + 二次导入不产生双份)。`existsImportHash` 是**全库**判定、
  不过滤 `deleted_at`。
- 事务:分类 / 账户 / 标签 / 流水写在**同一个** `database.withTransaction {}` 内,任意一行失败整体回滚,
  不留半份数据;末尾 `balanceSyncer.syncBalances(changedAccounts, now)`,`changedAccounts` 收全所有被写入行的 accountId。
- 报告:`inserted` / `duplicates` / `failedRows` / `skippedRows` + `newCategoryCount` / `newAccountCount`。

**测试**

- 脱敏 40 行最小夹具入库 `core/data/src/test/resources/yimu_fixture_40.xls`(覆盖全部 23 列 + 至少一条跨 CONTINUE
  的长备注);原始文件**不入库**。
- 全量样本(6008 行)回归用环境变量门控,只在本地跑、不提交。

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

- `FORMAT_VERSION = 4`，读侧接受1–4；manifest字段为formatVersion/createdAt/counts/sha256OfDb。
- JSON根写schemaVersion 4；缺版本按1。表首次出现版本由SnapshotArchive.tableVersion统一定义：auto为2，budget为3，savings_goal/savings_link/home_card/asset_baseline为4。旧格式可缺后来表；当前格式要求完整清单。配置、余额基线及所有关联完整往返，恢复前校验跨账本关系和储蓄进度。
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
`:feature:sync` 用 `backup_` 前缀并在 `strings.xml` 顶部注释写明规则（见 architecture.md「资源名全局唯一」）。 本任务另加一例:一木导入页在 `:feature:ledger`,
资源名统一用 `billimport_` 前缀(不要用裸 `import_`)。

### 坑 5:BIFF8 单元格行号别用「最近一条 ROW 记录」推导

**症状**:某份 BIFF8 样本的文本单元格「行号全错」,按行聚合后与 `xlrd` 对不上。

**真因**:这是**探针写法**造成的假象 —— 探针把「单元格自带 `row`」的字典与「按 ROW 记录跟踪的
当前行」的字典做键集比较,两者键本就不同源,差集被误读成「row 字段不可靠」。
实测:LABELSST/NUMBER 自带的 `row`/`col` 与本文件**逐格双射**
(132,199 个文本格恰铺满 132,199 个互不重叠的格子,对 `xlrd` 报出的文本格**零缺失**),
`ROW` 记录在本文件是**每个非空行一条**(6009 条 ↔ 6009 行),并非「每 32 行一个块」。

**修法**:解析器按**单元格自带的 `row`/`col`** 建矩阵;`ROW`/`DIMENSIONS` 仅作元数据。
(`.trellis/tasks/10-13-yimu-bill-import/research/fixture-generation.md` 有更正的前后对照。)

**预防**:拿「不同来源的键」做差集前,先确认两者**语义同源**;比较两个字典的 `keys()` 差异,
不等于比较同一组坐标上的值。
