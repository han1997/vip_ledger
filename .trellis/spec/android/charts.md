# 图表（自绘）

> 只允许 `:core:designsystem` 里的自绘实现；禁止引入第三方图表库（MPAndroidChart / Vico 等）。
> 适用范围：统计页的占比环图与时间趋势柱状图，以及后续任何同屏图表。

## 分层契约

| 文件 | 职责 | 可测性 |
|---|---|---|
| `component/chart/ChartModels.kt` | 界面消费的数据：`PieSlice` / `BarGroup` | 纯数据类，无逻辑 |
| `component/chart/ChartGeometry.kt` | 纯几何计算（占比、弧度、柱高比例、标签步长） | 纯函数，必须有 `src/test` 单测 |
| `component/chart/PieChart.kt` | `Canvas` 画环图 + 中心合计 + 图例 | 手工走查 |
| `component/chart/BarChart.kt` | `Canvas` 画柱状图（收入/支出双柱） | 手工走查 |

**规则**：把算术从绘制里剥出来。`Canvas` 只能靠手工走查，但「金额 → 弧度」「金额 → 柱高」这类
计算必须可单测；新图表一律先写几何纯函数，再写 Compose 绘制。

## Signatures

```kotlin
object ChartGeometry {
    const val START_ANGLE: Float = -90f   // Compose 0° 在 3 点方向、顺时针为正；环图从 12 点开始
    const val FULL_CIRCLE: Float = 360f

    fun sweepAngles(values: List<Long>): List<Float>                 // 每段弧度；总量 <= 0 时全 0
    fun startAngles(sweeps: List<Float>, startAngle: Float = START_ANGLE): List<Float>
    fun fractionOf(value: Long, total: Long): Float                  // 0..1；total <= 0 或 value < 0 → 0
    fun barFractions(values: List<Long>): List<Float>                // 相对最大值；全 0 → 全 0
    fun labelStride(count: Int, maxLabels: Int): Int                 // 横轴标签取样步长，>= 1
}
```

```kotlin
data class PieSlice(val label: String, val cents: Long, val color: Color)
data class BarGroup(val label: String, val incomeCents: Long, val expenseCents: Long)

@Composable fun PieChart(
    slices: List<PieSlice>,
    valueText: (PieSlice) -> String,      // 图例/环上显示的金额文案，由界面用 Money 组装
    modifier: Modifier = Modifier,
    chartSize: Dp = 168.dp,
    strokeWidth: Dp = 24.dp,
    centerTitle: String? = null,
    centerCaption: String? = null,
)
@Composable fun BarChart(
    groups: List<BarGroup>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    maxLabels: Int = 6,                   // 交给 ChartGeometry.labelStride 控制横轴密度
    incomeColor: Color = IncomeGreen,
    expenseColor: Color = ExpenseRed,
)
fun percentLabel(fraction: Float): String   // 「12.0%」（Locale.US，保留一位小数），环图/图例共用
```

## 数据契约

- 金额一律传**分（`Long`，非负）**。不要让 `Money` 出现在图表数据类或几何函数签名里：
  `Money` 是 `@JvmInline value class`，不能当 lambda 参数类型（见 ui.md 的坑 2）。
  需要显示时在界面层 `Money(cents)` 包一层。
- 颜色由调用方给出（`chartColorOf(index)` 或语义色），图表本身不做配色决策，也不写死色值。
- 空数据（`slices` 为空 / 全 0）时界面显示 `chart_empty` 灰环或空态，不要画一圈随机色。

## 边界与错误矩阵

| 输入 | 期望行为 | 断言点（`ChartGeometryTest`） |
|---|---|---|
| 正常占比 `[1, 1, 2]` | 弧度之和 = 360°，逐段 = 90/90/180 | `sweepAnglesCoverFullCircleInProportion` |
| 全 0 / 总量为 0 | 全 0 弧度，不除零 | `sweepAnglesAreAllZeroWhenTotalIsZero` |
| 负数金额 | 该段按 0 处理，其它段不受影响 | `sweepAnglesTreatNegativeAmountsAsZero` |
| `startAngles` | 首尾相接，起点默认 -90° | `startAnglesChainSweepsFromTwelveOClock` |
| `barFractions` | 相对最大值 0..1 | `barFractionsScaleAgainstTheLargestValue` / `barFractionsAreAllZeroWithoutData` |
| `labelStride(30, 6)` | 返回 5，标签不重叠 | `labelStrideKeepsVisibleLabelsUnderLimit` |
| `labelStride(count = 0)` / `maxLabels = 0` | 返回 1，不除零 | 同上 |

## 交互与布局

- 图表放 `LazyColumn` 的 `item {}` 里，不嵌套滚动（见 ui.md）。
- 环图中心显示当前口径合计（`StatsUiState.kindTotal`，随「收入/支出」切换）。
- 点某个分组 → 下钻：**图上的笔数与该分组的行数必须一致**，因为两者读同一份
  `report.summary`（`statsRowsOf` 产出 `StatsRow.children` 供环图展开）。不要在点击处重新查库。
- 横轴标签密度靠 `labelStride` 控制，禁止硬编码「只显示前 N 个」。

## 测试要求

- `ChartGeometry` 的每个函数都要有覆盖；`fractionOf` 目前由 `sweepAngles` / `barFractions` 间接覆盖，
  单独改动它时要补直接断言。
- 纯几何测试放**拥有该代码的模块**：`core/designsystem/src/test/.../component/chart/ChartGeometryTest.kt`。
  不要为了「集中测」把它挪到 `feature:*` 的 test 里。
- 数据映射（`StatsReport` → `StatsRow` → `PieSlice`/`BarGroup`）的测试放数据映射所在模块
  （现为 `feature:stats` 的 `StatsRowsTest`）。
- PNG 截图导出无法单测：`runCatching { layer.toImageBitmap() }` 兜底 + 模拟器手工走查（见 import-export.md）。