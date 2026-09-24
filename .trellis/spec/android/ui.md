# UI（Compose）

## 界面与状态

- 一个屏幕三件套：`XxxScreen`（无业务逻辑）、`XxxViewModel`、`XxxUiState`（不可变 `data class`）。
- 交互统一走 `XxxAction`：Screen 只调用 `viewModel.onAction(action)`，不直接调用仓库。
- 状态用 `StateFlow<XxxUiState>` 暴露，`collectAsStateWithLifecycle()` 收集。
- 一次性事件（弹提示、导航）用 `Channel` 或状态字段 + 消费后置空，不要用 LiveData。

## 样式

- 主题只用 `core:designsystem` 的 `VipLedgerTheme`；禁止在屏幕里写死颜色值。
  语义色（支出红、收入绿、转账蓝）从 designsystem 取。
- 金额展示统一 `Money.toDisplayString()`；列表里的金额右对齐、等宽数字，避免跳动。
- 文案全部进 `strings.xml`；带参数的用占位符，不在 Kotlin 里拼中文。
- 列表用 `LazyColumn` + `key`，滚动状态用 `rememberLazyListState` 保存。

## 预览与可测性

- 每个 Screen 配一个 `@Preview`，用假数据渲染；预览不得依赖 Hilt。
- 组件保持无状态优先：状态从参数传入，便于 Compose 测试直接渲染。
