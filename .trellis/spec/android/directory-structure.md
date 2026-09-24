# 目录与命名

## Android 模块内部（以 `:feature:ledger` 为例）

```
feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/
  ui/            Compose 界面：XxxScreen.kt（含 @Preview）
  viewmodel/     XxxViewModel.kt + XxxUiState.kt + XxxAction.kt
```

## 纯 Kotlin 模块内部（以 `:core:model` 为例）

```
core/model/src/main/kotlin/com/han1997/vipledger/core/model/
  money/Money.kt
  time/DateRange.kt
  LedgerEnums.kt
core/model/src/test/kotlin/...   测试与被测包路径一一对应
```

## 包名与命名

- 包名前缀：`com.han1997.vipledger`，模块后缀与目录一致（如 `core.model`、`feature.stats`）。
- Room 实体 `XxxEntity`、DAO `XxxDao`、仓库实现 `XxxRepositoryImpl`、用例 `XxxUseCase`。
- 表名与列名用小写下划线；Kotlin 属性用驼峰，通过 `@ColumnInfo` 映射。
- 测试类 `XxxTest`，与被测类同包；测试方法用中文描述业务规则或用反引号包裹的英文短句，禁止 `test1` 这类命名。
- 每个模块的 `namespace` 必须与包名一致；新增模块要同时更新 `settings.gradle.kts`。
