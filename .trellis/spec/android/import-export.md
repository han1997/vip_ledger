# 导入导出与备份

## 导出格式

- CSV：UTF-8 带 BOM，列顺序
  `日期,类型,金额,分类,子分类,标签,账户,目标账户,标题,备注,来源`；金额为两位小数字符串。
- JSON：直接序列化领域模型，用于备份与迁移，字段名与 `docs/DATA-MODEL.md` 一致。
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

## 导出内容

- **CSV**：`BuildTxnCsvUseCase` 产出文本。规则见 `core:domain/calculator/CsvWriter.kt`：
  `\r\n` 行尾；字段含 `,` / `"` / 换行时用双引号包裹并把 `"` 转义成 `""`；
  BOM（`\uFEFF`）只在文件开头写一次，让 Excel 双击不乱码。M3 明细导出的列（`BuildTxnCsvUseCase.HEADER`）：
  `日期,时间,类型,金额,账户,目标账户,分类,标签,标题,备注,计入统计,来源,状态,已删除`
  （本文件第一节的 `日期,类型,金额,分类,...` 是 M5 备份/导入用的对外格式，两者不要混）。
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