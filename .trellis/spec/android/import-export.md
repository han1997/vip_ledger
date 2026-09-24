# 导入导出与备份

## 导出格式

- CSV：UTF-8 带 BOM，列顺序
  `日期,类型,金额,分类,子分类,标签,账户,目标账户,标题,备注,来源`；金额为两位小数字符串。
- JSON：直接序列化领域模型，用于备份与迁移，字段名与 `docs/DATA-MODEL.md` 一致。
- 导出文件默认写到应用私有目录的 `files/export/`，再通过系统分享或 SAF 让用户取走。

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
