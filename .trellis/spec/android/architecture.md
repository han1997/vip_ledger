# 架构与分层

## 模块划分（依赖只能向内）

```
:app
:core:common   :core:model   :core:domain
:core:database :core:data    :core:designsystem
:feature:ledger :feature:stats :feature:ai :feature:sync
:ml:classifier
```

- 纯 Kotlin（JVM）模块：`:core:model`、`:core:common`、`:core:domain`、`:ml:classifier`。
  这几个模块禁止依赖 Android SDK，方便跑纯 JVM 单测。
- Android 模块：`:core:database`、`:core:data`、`:core:designsystem`、`:feature:*`、`:app`。
- 依赖方向：`feature:*` 与 `:app` 依赖 `core:*`；`core:data` 依赖 `core:database` 与 `core:domain`；
  `feature:*` 之间禁止互相依赖（需要共享时下沉到 `core:domain` 或 `core:designsystem`）。
- `:core:designsystem` 允许依赖 `:core:model` 与 `:core:domain`（M3 起）：共享组件要展示账目与分类树，
  必须认识领域模型。`core:*` 内部只允许出现「向内」的依赖（model <- domain <- data），禁止反向。

## 分层职责

| 层 | 位置 | 职责 |
|---|---|---|
| UI | `feature:*` 的 `ui` 包 | Compose 界面，只接收 `UiState` 并抛出 `Action` |
| 状态 | `feature:*` 的 `ui/<页面>` 包 | `XxxViewModel` 持有 `StateFlow<XxxUiState>`，与 Screen/UiState/Action 同包 |
| 用例 | `core:domain` | 业务规则与跨表编排，`operator fun invoke` 风格 |
| 仓库接口 | `core:domain` | `XxxRepository` 接口，供用例依赖 |
| 仓库实现 | `core:data` | Room/DataStore/网络的具体实现 |
| 持久化 | `core:database` | Room 实体、DAO、迁移 |
| 设计系统 | `core:designsystem` | 主题、语义色、通用组件、格式化函数（`format/`）与自绘图表（`component/chart/`） |

## 硬规则

- 跨表写操作必须在同一事务内完成（`database.withTransaction {}`），保证流水与账户余额一致。
- ViewModel 不直接依赖 DAO 或 OkHttp：只依赖 `core:domain` 的用例。
- 数据层不得把底层异常抛给上层：统一转成 `AppError`（见 error-handling.md）。
- 新依赖必须加进 `gradle/libs.versions.toml`，模块里禁止写死版本号。
- **跨模块复用的界面件下沉到 `:core:designsystem`**：格式化文案、id→名字查表、`TxnLine` 这类列表行、
  图表。同一段 UI 代码在第二个 `feature:*` 里再抄一遍就是该下沉的信号（M3 把 `Formatters` /
  `NameMaps` / `TxnLine` 从 `:feature:ledger` 移到 designsystem 就是这条规则的结果）。
- **资源名全局唯一**：Android library 模块的资源会合并，多个 `feature:*` 被 `:app` 同时依赖时，
  同名 `string`/`drawable` 会 `mergeDebugResources` 失败。`feature:*` 新增资源一律加模块前缀
  （`feature:stats` 用 `stats_` / `search_` / `drill_`），共用文案下沉到 designsystem，
  且在 `strings.xml` 顶部用注释写明前缀规则。