# 错误处理

## 统一模型

- 数据层失败返回 `AppResult.Failure(AppError)`，不抛异常给上层。
- `AppError` 分类：`Network` / `Auth` / `NotFound` / `Conflict` / `Parse` / `Storage` / `Validation` / `Unknown`。
  禁止用 `Unknown` 糊弄明确分类。
- 仓库实现里用 `runCatching` 或 try/catch 把 `IOException`、`SQLiteException`、`SerializationException`
  翻译成对应 `AppError`，并保留 `cause` 便于日志。

## 使用方式

```kotlin
return when (val result = remoteClient.fetch()) {
    is AppResult.Success -> AppResult.success(result.value.toDomain())
    is AppResult.Failure -> result
}
```

- UI 层把 `AppError` 映射成 `strings.xml` 里的用户文案（含重试提示），不直接显示异常信息。
- 校验类失败（金额为零、分类超两级、账户不属于当前账本）用 `AppError.Validation`，
  在用例里最早处拦截。
- 金额溢出用 `Money` 的 `ArithmeticException` 捕获后转 `Validation`，不允许崩溃。

## 禁止

- 禁止吞异常（空 catch）。
- 禁止把 `AppError.message` 直接当用户文案展示（那是给日志和开发者看的）。
