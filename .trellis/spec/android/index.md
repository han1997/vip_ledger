# Android 开发规范（本项目 spec 入口）

> VipLedger 的编码规范。动手前读这里，再按需读下面的具体文件。
> 产品与架构背景见 `docs/PLAN.md`、`docs/ARCHITECTURE.md`、`docs/DATA-MODEL.md`。

## Pre-Development Checklist

1. 读 `docs/PLAN.md` 进度看板，确认当前里程碑与范围边界。
2. 读 `docs/DATA-MODEL.md` 对应表结构；改表必须同时写迁移与迁移测试。
3. 读本目录下与本次改动相关的规范文件（见下表）。
4. 涉及依赖版本改动时，先核对 `docs/DEV-NOTES.md` 第 3 节的版本矩阵约束。
5. 金额用 `Money`（整数分），时间存 UTC epoch millis 并按设备时区归到 `LocalDate`。

## Quality Check（提交前必跑）

```powershell
.\gradlew.bat test assembleDebug
```

- 新增业务逻辑必须有单测；改表必须有迁移测试。
- 新增用户可见文案必须进 `strings.xml`，不在 Compose 里写死中文；
  跨 feature 共用的文案放 `core:designsystem`，feature 自有文案加模块前缀（`feature:stats` 用 `stats_`/`search_`/`drill_`）。
- 新增模块要同时改 `settings.gradle.kts`、`:app` 依赖与 Hilt 装配（缺少 `@Provides` 会在编译期报 MissingBinding）。
- 禁止提交 `local.properties`、`build/`、`.gradle/` 与任何密钥文件。

## 规范文件索引

| 文件 | 内容 |
|---|---|
| [architecture.md](./architecture.md) | 模块划分、依赖方向、分层与 DI 约定、共享件下沉与资源名前缀 |
| [directory-structure.md](./directory-structure.md) | 各模块内文件放哪儿、命名规则 |
| [database.md](./database.md) | Room 实体、DAO、迁移与测试约定 |
| [ui.md](./ui.md) | Compose 与 ViewModel 约定、主题、预览、单屏单 `LazyColumn`、统计口径 |
| [charts.md](./charts.md) | 自绘图表的几何契约、数据契约、边界与测试要求 |
| [error-handling.md](./error-handling.md) | `AppResult` 与 `AppError` 使用方式 |
| [logging-guidelines.md](./logging-guidelines.md) | 日志级别与敏感信息红线 |
| [testing.md](./testing.md) | 测试分层、命名与命令、跨模块口径一致性测试 |
| [ai-ml.md](./ai-ml.md) | AI 解析管线、端侧分类器、模型文件格式 |
| [recurring-loans.md](./recurring-loans.md) | 周期推算、草稿口径、分期还款、提醒与后台任务 |
| [import-export.md](./import-export.md) | 导入导出（含 SAF 界面导出握手）、WebDAV 备份格式约定 |
| [build-and-env.md](./build-and-env.md) | 构建配置、依赖仓库、环境坑位 |

**文档语言**：中文。