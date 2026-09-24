# 测试

## 分层

| 层次 | 位置 | 工具 | 覆盖内容 |
|---|---|---|---|
| 纯逻辑单测 | `core:model` / `core:domain` / `ml:classifier` 的 `src/test` | JUnit4 + kotlin.test | 金额、日期范围、分期计息、周期推算、统计聚合、分类器训练与推理 |
| 数据层测试 | `core:database` / `core:data` 的 `src/test` | Robolectric + Room in-memory | DAO 查询、迁移、仓库错误映射 |
| 网络测试 | `core:data` 的 `src/test` | OkHttp MockWebServer | WebDAV PROPFIND/PUT/GET、AI 端点解析与失败回退 |
| UI 测试 | `feature:*` 的 `androidTest` | Compose UI Test | 记账保存路径、搜索过滤、统计时间段切换 |

## 命名与写法

- 测试方法名描述业务规则，例如 `splitEvenlyKeepsTotal`、`parseRejectsGarbageAndOverflow`；
  禁止 `test1`、`testParse`。
- 断言用 kotlin.test（`assertEquals` / `assertNull` / `assertFailsWith`），不引入新的断言库。
- 涉及协程用 `kotlinx-coroutines-test` 的 `runTest`，时间推进用 `TestDispatcher`。
- 金额与计息类测试必须包含边界：0、负数、除不尽、四舍五入、溢出。

## 命令

```powershell
.\gradlew.bat test                 # 全部 JVM 单测
.\gradlew.bat :core:model:test     # 单个模块
.\gradlew.bat connectedDebugAndroidTest   # 需要模拟器
```

新增业务逻辑但不带测试的改动，视为未完成。
