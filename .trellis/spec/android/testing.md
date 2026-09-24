# 测试

## 分层

| 层次 | 位置 | 工具 | 覆盖内容 |
|---|---|---|---|
| 纯逻辑单测 | `core:model` / `core:domain` / `core:designsystem` / `ml:classifier` 的 `src/test` | JUnit4 + kotlin.test | 金额、日期范围、分期计息、周期推算、统计聚合、CSV 转义、图表几何、分类器训练与推理 |
| 数据层测试 | `core:database` / `core:data` 的 `src/test` | Robolectric + Room in-memory | DAO 查询、迁移、仓库错误映射 |
| 网络测试 | `core:data` 的 `src/test` | OkHttp MockWebServer | WebDAV PROPFIND/PUT/GET、AI 端点解析与失败回退 |
| UI 测试 | `feature:*` 的 `androidTest` | Compose UI Test | 记账保存路径、搜索过滤、统计时间段切换 |

## 命名与写法

- 测试方法名描述业务规则，例如 `splitEvenlyKeepsTotal`、`parseRejectsGarbageAndOverflow`；
  禁止 `test1`、`testParse`。
- 断言用 kotlin.test（`assertEquals` / `assertNull` / `assertFailsWith`），不引入新的断言库。
- 涉及协程用 `kotlinx-coroutines-test` 的 `runTest`，时间推进用 `TestDispatcher`。
- 金额与计息类测试必须包含边界：0、负数、除不尽、四舍五入、溢出。
- **测试跟着代码走**：纯函数/纯状态在哪个模块，测试就放哪个模块的 `src/test`
  （图表几何 → `core:designsystem/src/test`；`StatsReport` → 行映射 → `feature:stats/src/test`）。
  不要为了「集中」把纯逻辑测试挪到别的模块，那会强迫被测模块反向依赖。
- 界面里写的纯函数（`statsRowsOf`、`drillArgsOf`、`TxnFilterState.toQuery`）也必须进 `feature:*` 的
  `src/test`——JVM 单测跑得比 `androidTest` 快得多，只有真正需要 Compose 渲染才写 `androidTest`。

## 跨模块口径一致性

同一业务数字在多个页面出现时（首页本月收支 vs 统计页本月），除了「复用同一个计算函数」，
还要用一条**跨模块断言测试**把两边钉在一起：

- 现例：`core/domain/src/test/.../usecase/StatsMatchesHomeOverviewTest.kt` —— 用同一份假数据分别跑
  首页用例与统计用例，断言总收入/总支出/笔数相等。
- 这类测试放**被复用逻辑所在模块**（`core:domain`），因为它同时能构造两边的用例。
- 口径变了但测试没改，说明两边已经漂移；先改公式再改测试，不要反过来。

## 命令

```powershell
.\gradlew.bat test                 # 全部 JVM 单测
.\gradlew.bat :core:model:test     # 单个模块
.\gradlew.bat connectedDebugAndroidTest   # 需要模拟器
```

新增业务逻辑但不带测试的改动，视为未完成。

## 模拟器走查（手工验收）

- `adb shell input text` **注入不了中文**（非 ASCII 抛
  `java.lang.NullPointerException: Attempt to get length of null array`）。
  手工走查里需要输入的账本名/账户名/分类名/标签名一律用 ASCII，中文渲染靠内置数据覆盖。
- 用 `adb shell uiautomator dump` 拿控件真实坐标，比截图估算可靠；Compose 的 `ExtendedFAB`
  文案可能不出现在 dump 里，按坐标点。
- `adb shell input swipe x y x y 1200`（同点、长按）可触发列表项的 `combinedClickable` 长按。
- 多设备/多模拟器同时在线时必须显式指定：`adb -s emulator-5554 ...`，否则命令会因
  `more than one device` 直接失败。
- 无法单测的东西（PNG 截图导出、SAF 选择器）只能靠这轮走查背书，走查项记录进任务 PRD 的「完成情况」。