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
- 表单类 UiState 的「默认选中 / 合法性」这类规则写成纯函数放在 UiState 上（如
  `TxnEditUiState.resolvedTargetAccountId()`），并在模块 `src/test` 里覆盖；
  放 Screen 里就只能靠手工走查，M2 的转账保存失败就是这么漏掉的。

## 已踩过的坑（写代码前先看）

1. **`LaunchedEffect` 里不能调 `@Composable`**：`LaunchedEffect { stringResource(...) }` 报
   `@Composable invocations can only happen from the context of a @Composable function`。
   先把文案取到局部变量，再 `LaunchedEffect(message)`：
   ```kotlin
   val notice = state.notice
   val message = if (notice == null) null else manageNoticeMessage(notice, state.noticeDetail)
   LaunchedEffect(message) { if (message != null) { snackbarHostState.showSnackbar(message); onAction(ConsumeNotice) } }
   ```
2. **`Money` 是 `@JvmInline value class`，不能当 lambda 参数类型**：`(Long, Money, ...) -> Boolean`
   这类签名编译报 `Function5 cannot be a value class`。跨 lambda 传金额就传 `Long` 分，函数里再转。
3. **`SavedStateHandle.get<String>("arg")` 缺参时返回 `""` 而不是 null**（Nav 路径参数缺失）。
   用 `.orEmpty()` 或 `.takeIf { it.isNotBlank() }` 归一，别直接 `?:` 判断。
4. **状态里不放中文**：一次性提示用枚举（`HomeNotice` / `ManageNotice`）+ 界面层 `stringResource` 查表；
   但领域层 `AppError.message` 本身就是给用户看的说明，失败提示应优先透出它（别一律显示「操作失败」）。
5. **id → 名字的查表集中在一处**：`ui/common/NameMaps.kt` 的 `categoryNameMap()` / `accountNameMap()` /
   `tagNameMap()`。账目只存 id，任何列表都要显式补齐名字，漏了就显示成「未分类」。
