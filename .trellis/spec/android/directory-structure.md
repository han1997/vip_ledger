# 目录与命名

## Android 模块内部（以 `:feature:stats` 为例）

```
feature/stats/src/main/kotlin/com/han1997/vipledger/feature/stats/
  navigation/    路由常量 + NavGraphBuilder.xxxGraph()（`:app` 只调用这个扩展函数）
  ui/common/     本 feature 内共用的过滤状态与组件（跨 feature 共用要下沉 designsystem）
  ui/search/     XxxScreen.kt + XxxViewModel.kt + XxxUiState.kt + XxxAction（同一包，含 @Preview）
  ui/stats/      统计页；ui/drill/ 下钻页
```

- `XxxViewModel` / `XxxUiState` / `XxxAction` 与对应 `XxxScreen` **同包**，不另建 `viewmodel/` 目录。
- 一个 feature 的界面按页面分子包；`ui/common/` 只放「本模块内两个以上页面共用」的东西。
- 页面级的一次性导出/权限握手（SAF `CreateDocument`）放 `ui/common/` 的工具文件里，不要在 Screen 里重复实现。

## `:core:designsystem` 内部

```
core/designsystem/src/main/kotlin/com/han1997/vipledger/core/designsystem/
  theme/            VipLedgerTheme、语义色（TransactionColors / ChartColors）
  component/        通用组件：AmountText、EmptyState、VipLedgerTopBar、TxnLine、ConfirmDialog、LabeledTextField
  component/chart/  自绘图表：ChartModels（数据）、ChartGeometry（纯几何）、PieChart、BarChart
  format/           文案拼装（Formatters，@Composable 取 strings.xml）+ id→名字查表（NameMaps，纯函数）
core/designsystem/src/main/res/values/strings.xml   跨 feature 共用的文案（日期/星期/类型/账户类型/分类性质/图表）
core/designsystem/src/test/kotlin/...               纯逻辑（如 ChartGeometry）的单测
```

- `format/` 与 `component/` 里的东西是**跨模块公共 API**：改签名前先搜全仓库的调用点。

## 纯 Kotlin 模块内部（以 `:core:model` 为例）

```
core/model/src/main/kotlin/com/han1997/vipledger/core/model/
  money/Money.kt
  time/DateRange.kt
  LedgerEnums.kt
core/model/src/test/kotlin/...   测试与被测包路径一一对应
```

- `core:domain` 同构：`model/`（领域模型）、`calculator/`（纯计算）、`repository/`（接口）、`usecase/<领域>/`（用例）。

## 包名与命名

- 安装 applicationId：`fun.han1997.vipledger`，与源码 namespace 分离。
- 源码包名前缀：`com.han1997.vipledger`，模块后缀与目录一致（如 `core.model`、`feature.stats`）。
- Room 实体 `XxxEntity`、DAO `XxxDao`、仓库实现 `XxxRepositoryImpl`、用例 `XxxUseCase`。
- 表名与列名用小写下划线；Kotlin 属性用驼峰，通过 `@ColumnInfo` 映射。
- 测试类 `XxxTest`，与被测类同包；测试方法用中文描述业务规则或用反引号包裹的英文短句，禁止 `test1` 这类命名。
- 每个模块的 `namespace` 必须与包名一致；新增模块要同时更新 `settings.gradle.kts` 与 `:app` 的依赖。
