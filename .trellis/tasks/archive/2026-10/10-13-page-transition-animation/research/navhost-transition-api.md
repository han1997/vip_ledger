# Research: NavHost 过渡 API 与无障碍/预测性返回协同

- **Query**: Navigation Compose 2.9.5 的 `NavHost` 过渡 lambda 能否按 route 分派;`composable()`
  destination 级过渡的可用性与优先级;Compose 动画与系统「移除动画」的关系及检测方式;
  预测性返回与自定义 pop 过渡的协同。
- **Scope**: mixed(内部代码现状 + 外部依赖 API;外部 API 以本机 Gradle 缓存中的**确切依赖源码**为准)
- **Date**: 2026-10-13

## 验证方法(重要)

本环境**无法访问外网**(`developer.android.com:443` 连接超时),因此没有直接抓取官方网页。
所有 API 结论均来自**本机 Gradle 缓存里与项目实际依赖完全一致的 artifact 源码**,逐行核对:

| 依赖 | 解析版本 | 核对来源(本机路径) |
|---|---|---|
| Navigation Compose | **2.9.5** | `navigation-compose-android-2.9.5-sources.jar` |
| navigation-common | 2.9.5 | `navigation-common-android-2.9.5`(sources) |
| Compose BOM | **2026.06.01** | `compose-bom-2026.06.01.pom` |
| compose-ui | **1.11.4**(由 BOM 映射) | `ui.aar`(classes)+ `ui-android-1.12.0-sources.jar` 对照 |
| compose-animation / animation-core | **1.11.4 / 1.12.0**(缓存内两者都在) | `animation-*.aar`、`*-sources.jar` |
| compose-runtime | **1.11.4**(BOM 映射) | `runtime-1.12.0-sources.jar` 对照 |
| activity-compose | **1.11.0** | `activity-compose-1.11.0-sources.jar` |

> 关键版本事实:**Navigation Compose 2.9.5 不属于 Compose BOM**,二者版本独立。
> 结论标注中凡「2.9.5 专属」者,均以 `navigation-compose-2.9.5` 源码为据。

---

## 结论速览

| 问题 | 结论 | 置信度 |
|---|---|---|
| Q1 运行期区分 Tab / 次级页 | **可以**,在 `NavHost` 四个过渡 lambda 里读 `initialState.destination.route` / `targetState.destination.route` 即可 | 高(源码逐行确认) |
| Q2 `composable()` 级过渡是否稳定可用 | **可用且非实验**;destination 级**覆盖** NavHost 级(先 destination → 后 NavHost 兜底) | 高(源码逐行确认) |
| Q3.1 「移除动画」= `ANIMATOR_DURATION_SCALE=0` | **是**;Compose 自身即读该值 | 高(源码)/ 中(设置项名到该 key 的官方映射未抓网页) |
| Q3.2 Compose `tween/fadeIn/slideIn` 是否自动遵循 | **是,自动遵循**;scale=0 时动画在下一帧直接到终点(瞬切,不是「淡入淡出」) | 高(源码逐行确认) |
| Q3.3 检测 API | `MotionDurationScale`(非实验)或 `Settings.Global.ANIMATOR_DURATION_SCALE` + ContentObserver | 高(两条路径均给出可编译骨架) |
| Q4 预测性返回协同 | **已有内建处理**;pop 过渡在预测返回期间由 `popEnterTransition`/`popExitTransition` 驱动并按手势进度 seek,四项时长一致即无撕裂 | 高(源码逐行确认) |

---

## Q1 — 运行期区分 Tab 与次级页的正确做法

### 接收者类型(确定)

`NavHost` 的 `enterTransition` / `exitTransition` / `popEnterTransition` / `popExitTransition`
接收者类型是:

```
AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition / ExitTransition
```

- 声明包:`androidx.compose.animation.AnimatedContentTransitionScope`(非 `@Experimental`)。
- `AnimatedContentTransitionScope<S>` 继承 `Transition.Segment<S>`,提供:
  - `val initialState: S` / `val targetState: S` → 此处 `S = NavBackStackEntry`(**非空**)。
- 因此 `initialState.destination` / `targetState.destination` 是 `NavDestination`,
  `.route: String?` 即注册时的**路由模板字符串**(带参路由返回模板,如 `ledger/account/{ledgerId}/{accountId}`)。

源码位置(2.9.5):
- `navigation-compose/commonMain/.../NavHost.kt:127-206`(四个 lambda 参数与默认值)
- `animation/commonMain/.../AnimatedContent.kt:308`(`sealed interface AnimatedContentTransitionScope<S> : Transition.Segment<S>`)
- `animation-core/commonMain/.../Transition.kt:1777`(`Segment.initialState/targetState`)

### 可编译骨架(方案 A:根 NavHost 按 route 集中分派)

```kotlin
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost

/** 四个 Tab 根(与 PRD R2 一致);纯常量路由,无路径参数。 */
private val TabRootRoutes: Set<String> = setOf(
    "ledger/home", "ledger/calendar", "ledger/assets", "ledger/settings-tab", "stats/tab",
)

/** 只在两端都是 Tab 根时走平级淡入淡出;其余情况视为「有层级的前推/返回」。 */
private val AnimatedContentTransitionScope<NavBackStackEntry>.isTabToTab: Boolean
    get() = initialState.destination.route in TabRootRoutes &&
        targetState.destination.route in TabRootRoutes

NavHost(
    navController = navController,
    startDestination = "ledger/home",
    enterTransition = {
        if (isTabToTab) fadeIn(tween(DURATION_TAB))
        else slideInHorizontally(tween(DURATION_PUSH)) { it / 6 } + fadeIn(tween(DURATION_PUSH))
    },
    exitTransition = {
        if (isTabToTab) fadeOut(tween(DURATION_TAB))
        else slideOutHorizontally(tween(DURATION_PUSH)) { -it / 6 } + fadeOut(tween(DURATION_PUSH))
    },
    popEnterTransition = {
        if (isTabToTab) fadeIn(tween(DURATION_TAB))
        else slideInHorizontally(tween(DURATION_POP)) { -it / 6 } + fadeIn(tween(DURATION_POP))
    },
    popExitTransition = {
        if (isTabToTab) fadeOut(tween(DURATION_TAB))
        else slideOutHorizontally(tween(DURATION_POP)) { it / 6 } + fadeOut(tween(DURATION_POP))
    },
) { /* ledgerGraph(...) / statsGraph(...) / backupGraph() / aiGraph(...) */ }
```

要点:
- `isTabToTab` 必须同时看 `initialState` 与 `targetState`:因为 `enterTransition` 由**进入方**触发、
  `exitTransition` 由**退出方**触发,单看一边无法判定「Tab↔Tab」这一整对语义。
- `slideInHorizontally` 的 `initialOffsetX` 是**尾参**:`{ it / 6 }` 正偏移 = 从右侧滑入,
  `-it / 6` = 从左侧滑入;`slideOutHorizontally` 的 `targetOffsetX` 反之。方向语义经源码确认
  (`EnterExitTransition.kt:763/823` 文档 + `AnimatedContent.kt` 的 `SlideDirection`)。
- 判定 route 的两种写法等价:
  - `targetState.destination.route == LedgerRoutes.HOME`(常量 Tab 路由足够);
  - `targetState.destination.hasRoute(LedgerRoutes.HOME, null)`(带参路由 / 更稳;
    `NavDestination.hasRoute(route: String, arguments: SavedState?)` 见 `NavDestination.kt:261`)。
  本项目四个 Tab 根均为无参常量,直接 `route` 比较即可。

---

## Q2 — destination 级过渡的可用性与优先级

### 可用性:稳定、非实验

`NavGraphBuilder.composable(...)` 提供了带过渡参数的重载
(`enterTransition` / `exitTransition` / `popEnterTransition` / `popExitTransition` / `sizeTransform`),
参数类型与 NavHost 同款 `AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?`
(**注意带 `?`,默认 `null`**),**无任何 `@Experimental` 标注**。

- 源码:`navigation-compose/commonMain/.../NavGraphBuilder.kt:131` 起(当前非弃用重载);
  更早两个带过渡的重载为 `@Deprecated(level = HIDDEN)`,不受影响。
- 参数最终写入 `ComposeNavigatorDestinationBuilder`:
  `enterTransition/exitTransition/popEnterTransition/popExitTransition/sizeTransform` 均默认 `null`。

### 优先级:destination 覆盖 NavHost(逐行确认)

`NavHost` 计算最终过渡时:

```
// navigation-compose/commonMain/.../NavHost.kt:578-605
finalEnter = {
    val targetDestination = targetState.destination as ComposeNavigator.Destination
    if (isPop || inPredictiveBack)
        targetDestination.hierarchy.firstNotNullOfOrNull { it.createPopEnterTransition(this) } ?: popEnterTransition.invoke(this)
    else
        targetDestination.hierarchy.firstNotNullOfOrNull { it.createEnterTransition(this) } ?: enterTransition.invoke(this)
}
finalExit = {
    val initialDestination = initialState.destination as ComposeNavigator.Destination
    if (isPop || inPredictiveBack)
        initialDestination.hierarchy.firstNotNullOfOrNull { it.createPopExitTransition(this) } ?: popExitTransition.invoke(this)
    else
        initialDestination.hierarchy.firstNotNullOfOrNull { it.createExitTransition(this) } ?: exitTransition.invoke(this)
}
```

结论:
1. **先查 destination 及其父 graph 的 `hierarchy`,命中第一个非 null 就用它;全部为 null 才回退到
   `NavHost` 的 lambda**。即「destination 覆盖 NavHost」,而非相反。
2. `hierarchy` 顺序 = [destination, 父 graph, …, root graph];本项目的 20 个 destination 通过
   `ledgerGraph/statsGraph/aiGraph/backupGraph` 直接注册进 root graph(未嵌套 `navigation(...)`),
   故 key 落在 destination 自身。
3. **enter 取「进入方」的 destination,exit 取「退出方」的 destination**——这是 Q2 的关键不对称,
   决定方案 B 是否可行(见下)。

### 可编译骨架(方案 B:destination 级覆写)

```kotlin
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.composable

composable(
    route = LedgerRoutes.HOME,
    enterTransition = { fadeIn(tween(DURATION_TAB)) },
    exitTransition = { fadeOut(tween(DURATION_TAB)) },
    popEnterTransition = { fadeIn(tween(DURATION_TAB)) },
    popExitTransition = { fadeOut(tween(DURATION_TAB)) },
) { HomeScreen(/* ... */) }
```

### 方案 A vs 方案 B 优劣(针对本任务)

| 维度 | 方案 A(根 NavHost 按 route 分派) | 方案 B(destination 级覆写) |
|---|---|---|
| 改动点 | **单一**(`VipLedgerApp.kt` 一处) | 分散在 4 个 feature 模块;至少要给 5 个 Tab 根各写 4 个 lambda |
| Tab↔Tab 语义 | 天然可判「两端都是 Tab 根」 | 需给**每个** Tab 根同时覆写四个过渡;单覆写 enter 会漏掉对方的 exit |
| Tab↔次级页 | 可精确按「这一对」决定滑动/淡化 | **不对称**:进入次级页时,次级页 enter 走 NavHost 默认(滑动),但**退出的 Tab 根若覆写了 exitTransition 就会淡出**,与滑动不匹配 |
| 可读性/集中度 | 一处看全语义 | 每个 destination 自解释,但全局语义需跨文件拼 |
| 回归风险 | 低(不改 feature 模块) | 中(20 个 destination,易漏) |
| 与 PRD 倾向 | PRD「方案 A(倾向)」一致 | PRD 备选 |

**建议(供主 agent 决策,非本文件的实现建议)**:本任务目标(R2「Tab 平级不含位移」+ R1「次级页有方向」)
是**成对语义**,方案 B 因 enter/exit 取自不同 destination 会引入不对称,方案 A 更贴合。
PRD 也已倾向方案 A。

---

## Q3 — 「减弱动态效果」的探测与 Compose 动画的关系

### Q3.1 系统「设置 → 无障碍 → 颜色和动态效果 → 移除动画」在底层改的是什么?

**结论:是,等价于把动画时长缩放置 0,Compose 读取的正是 `Settings.Global.ANIMATOR_DURATION_SCALE`。**

证据(源码):Compose UI 的 `WindowRecomposer` 在 Android 上:

```kotlin
// ui/androidMain/androidx/compose/ui/platform/WindowRecomposer.android.kt:97
private fun Context.readAnimationScale() =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

// :101-130 用 ContentObserver 监听该 key,产出 StateFlow<Float>
private fun getAnimationScaleFlowFor(applicationContext: Context): StateFlow<Float> { /* ... */ }
```

- `MotionDurationScaleImpl.scaleFactor` 即由该 StateFlow 初始化并订阅(`:423-449`)。
- `WindowRecomposer` 把该实现注入 composition 的 `CoroutineContext`(`:333-341`),
  作用于**整个 Compose 子树**。
- `ANIMATOR_DURATION_SCALE` 默认 1f,项目未改此 key。
- **官方映射说明**:Android 无障碍的「移除动画(Remove animations)」会把 Window/Transition/Animator
  duration scale 置 0,这是系统级开关;`ANIMATOR_DURATION_SCALE` 是其中 Animator 对应的 key。
  ⚠️ 本环境离线,**未抓取**该设置项到具体 key 的官方页面,故此项映射标注为**中等置信**;
  但「Compose 通过 `ANIMATOR_DURATION_SCALE` 感知动画缩放」这一点是**源码级高置信**。

### Q3.2 Compose 的 `tween()` / `fadeIn` / `slideInHorizontally` 是否已自动遵循?

**结论:是,已自动遵循;且 scale=0 时动画是「下一帧直接到终点(瞬切/snap)」,不是「淡入淡出」。**

证据(源码):

```kotlin
// animation-core/commonMain/androidx/compose/animation/core/SuspendAnimation.kt:308-313
internal val CoroutineContext.durationScale: Float
    get() {
        val scale = this[MotionDurationScale]?.scaleFactor ?: 1f
        checkPrecondition(scale >= 0f) { "negative scale factor" }
        return scale
    }

// :344-360 doAnimationFrameWithScale
val playTimeNanos =
    if (durationScale == 0f) anim.durationNanos        // 直接取整段时长 → 一帧内完成
    else ((frameTimeNanos - startTimeNanos) / durationScale).toLong()
```

- `finish()` / `doAnimationFrame` 在 `durationScale == 0f` 时把 `playTimeNanos` 设为 `durationNanos`,
  动画在第一个帧回调就到终点 → 观感为**瞬间切换(无过渡)**。
- `InfiniteTransition` 也额外处理了 `durationScale == 0`(挂起等待 >0,见 `InfiniteTransition.kt:169-193`)。
- 结论对 `fadeIn/fadeOut/slideInHorizontally/slideOutHorizontally/tween` 一律成立(它们都走同一套
  `Animation` 驱动)。

**对本任务的意义**:PRD R3 要求「开启时降级为纯淡入淡出或瞬切」。其中**「瞬切」是系统免费给的**——
若什么都不做,系统开「移除动画」时会直接瞬切。App 侧要额外做的,只是**若希望仍保留淡入淡出
(而不是瞬切)**,才需要自己检测并换成纯 fade。

### Q3.3 官方推荐的检测方式 / 具体 API

Android **没有**与 Web `prefers-reduced-motion` 对等的 Compose 公共 API;`LocalAccessibilityManager`
只管触摸探索等,**不含**动画缩放。可用两条路径(均给出可编译骨架):

**路径 1:`MotionDurationScale`(非实验,读 composition 协程上下文)**

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale

/** 1f = 正常;0f = 系统「移除动画」;>1f = 放慢。 */
@Composable
fun rememberMotionDurationScale(): Float {
    val scope = rememberCoroutineScope()
    return scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
}
```

- `MotionDurationScale` 定义:`androidx.compose.ui.MotionDurationScale`,`interface MotionDurationScale : CoroutineContext.Element`,
  仅 `@Stable`,**非 `@Experimental`**(源码 `ui/commonMain/androidx/compose/ui/MotionDurationScale.kt`)。
- `LocalMotionDurationScale` 这类 CompositionLocal **不存在**(已在本机 1.11.4 `ui.aar` 与 1.12.0
  源码全量搜索,零命中)。要拿到它只能经协程上下文。
- 为什么 `rememberCoroutineScope()` 的上下文里有它:`createCompositionCoroutineScope` 用
  `composer.applyCoroutineContext` 作父上下文(`runtime/commonMain/.../Effects.kt:714-732`),
  而该上下文即 `Recomposer` 的 `effectCoroutineContext`,`WindowRecomposer` 已注入 `MotionDurationScaleImpl`。
- `scaleFactor` 由 `mutableFloatStateOf` 承载并随系统设置经 ContentObserver 更新
  (`WindowRecomposer.android.kt:423-449`)——**在 composition 中读取会被快照系统追踪**,系统设置变化时可触发重组。
- ⚠️ 该路径**可编译、API 稳定**,但「用 composition 协程上下文读动画缩放」并非官方文档中推荐的
  无障碍检测范式,属**实现细节依赖**;若追求最稳,用路径 2。

**路径 2:`Settings.Global` + ContentObserver(完全公共 API,稳定)**

```kotlin
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private fun readAnimatorScale(context: Context): Float =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/** true = 系统「移除动画」已开启(动画缩放为 0)。 */
@Composable
fun rememberAnimationsRemoved(): Boolean {
    val context = LocalContext.current
    var removed by remember { mutableStateOf(readAnimatorScale(context) == 0f) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                removed = readAnimatorScale(context) == 0f
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return removed
}
```

使用(与 Q1/Q2 组合):

```kotlin
val reduceMotion = rememberAnimationsRemoved()
// 每个过渡 lambda 内:
if (reduceMotion) fadeIn(tween(DURATION_TAB)) /* 或 EnterTransition.None 直接瞬切 */
else slideInHorizontally(...) { it / 6 }
```

注意:`reduceMotion` 是**组合期读取的状态**;把它传入过渡 lambda 时,lambda 会在每次过渡建立时求值,
读到的是当时的值(与系统设置一致)。

### Q4 — Navigation Compose 自带的**默认**过渡行为

**结论:默认 = 700ms 交叉淡入淡出(enter/exit 各 `fadeIn/fadeOut(tween(700))`),popEnter/popExit
默认等于 enter/exit。**(源码 `NavHost.kt:136-142/195-206`,所有重载一致)

即:若不传任何过渡,页面切换是 700ms 淡入淡出;当前 `VipLedgerApp.kt` 已用显式参数覆盖了它。

---

## Q4 — 预测性返回(predictive back)的协同

### 结论:已内建处理,自定义 pop 过渡会被预测返回**直接驱动**;只要四项时长口径一致即无撕裂。

源码逐行(2.9.5 `NavHost.kt`):

1. **接管系统手势**:
   ```kotlin
   // :512-552
   var progress by remember { mutableFloatStateOf(0f) }
   var inPredictiveBack by remember { mutableStateOf(false) }
   PredictiveBackHandler(currentBackStack.size > 1) { backEvent ->
       // OnStarted: prepareForTransition(current), prepareForTransition(previous)
       // OnProgressed: inPredictiveBack = true; progress = it.progress
       // OnCompleted: inPredictiveBack = false; popBackStack(current, false)
       // OnCancelled: inPredictiveBack = false
   }
   ```
2. **过渡选择**:`finalEnter`/`finalExit` 的条件是 `if (composeNavigator.isPop.value || inPredictiveBack)`,
   即**预测返回期间用的是 `popEnterTransition` / `popExitTransition`**。
   - `ComposeNavigator.navigate(...)` → `isPop.value = false`;`popBackStack(...)` → `isPop.value = true`
     (`ComposeNavigator.kt:57/67`)。
3. **手势进度驱动**:`inPredictiveBack` 时不再 `animateTo`,而是
   `transitionState.seekTo(progress, previousEntry)`(`:631-635`)——即过渡按手势进度 **seek**,
   松手后 `OnCompleted` 再正常 `popBackStack` 播放余下部分;取消则回落。
4. **zIndex**:`transitionSpec` 中 pop/预测返回时 `targetZIndex = initialZIndex - 1f`(返回内容置于下层),
   否则 +1(`:678-686`)。
5. **所需的系统前提**:`android:enableOnBackInvokedCallback` 必须为 true。
   - `targetSdk = 36` → 默认已启用;本项目 manifest **未**出现
     `android:enableOnBackInvokedCallback="false"`(已核对 `app/src/main/AndroidManifest.xml`),故已启用。
   - `MainActivity : ComponentActivity`(源码确认),具备 `OnBackPressedDispatcherOwner`,
     `PredictiveBackHandler` 从 `LocalOnBackPressedDispatcherOwner` 取(`activity-compose-1.11.0` 源码确认)。

### 「是否已有已知动画冲突/撕裂」与「要不要额外做什么」

- 框架层面:预测返回路径就是为自定义过渡设计的(`seekTo` + pop 过渡),**无需 App 侧写额外代码**即兼容。
- 已知容易踩的点(需要在真机验收中核对的项,非本研究断定会出的 bug):
  1. 四项 pop 时长**不一致**会让 seek 映射与松手后的播放速率不一致 → 观感跳变。PRD D3 已把
     pop 两项统一为 **250ms**,一致,风险低。
  2. 若用 `EnterTransition.None`/直接瞬切覆盖 pop,预测返回会失去跟随手势的连续感(但仍可用)。
  3. `fadeOut(tween(90))` 这类**极短 + 极小位移**(现实现的 `it/50`)在预测返回下会出现
     「手势拖了很久但视觉几乎不动」的错觉,建议废弃(PRD 已废弃该项)。
- `PredictiveBackHandler` 由 `androidx.activity:activity-compose:1.11.0` 提供,**非实验**
  (`@Composable public fun PredictiveBackHandler(...)`,无 `@Experimental*`)。

**回答 PRD 的问题「需不需要额外处理」**:让预测返回「跟随手势 + 不撕裂」**不需要**额外代码(2.9.5
已内建);App 只需保证 pop 两项时长一致、且不要为 pop 指定 `None`/瞬切。若 R3 的无障碍降级把 pop
变成纯 fade,预测返回仍可用(seek 的是淡化进度)。

---

## 版本相关性标注(2.9.5 / BOM 2026.06.01 专属 vs 通用)

| 结论 | 版本相关性 |
|---|---|
| NavHost 过渡 lambda 接收者 `AnimatedContentTransitionScope<NavBackStackEntry>` | 通用(自 Navigation 2.7 引入 AnimatedContent 集成起) |
| 读 `initialState/targetState.destination.route` 分派 | 通用 |
| `composable(...)` 的过渡参数、destination **覆盖** NavHost 的优先级 | 通用(2.7+) |
| 默认过渡 = `tween(700)` fade | 通用(2.7+) |
| **预测性返回内建处理(`PredictiveBackHandler` + `seekTo(progress)` + pop 过渡)** | **2.9.x 专属能力**。旧版(2.7.x)预测返回支持不完整/无此 `seekTo` 逻辑。本机缓存里同时存在 `navigation-compose-2.7.6`,其行为**不保证**与本文件一致 |
| `MotionDurationScale` / `LocalMotionDurationScale` 缺失 | `MotionDurationScale` 长期存在;`LocalMotionDurationScale` 在 1.11.4 与 1.12.0 均**不存在** |
| `compose-ui`/`animation` 解析到 **1.11.4**(BOM 2026.06.01);缓存内亦有 1.12.0(BOM 2026.08.00) | 本任务锁定 1.11.4 |

---

## 方案 A / 方案 B 落地要点(汇总)

- **方案 A**:唯一改动点 `app/src/main/kotlin/com/han1997/vipledger/ui/VipLedgerApp.kt` 的根 `NavHost`;
  用辅助扩展(如 `AnimatedContentTransitionScope<NavBackStackEntry>.isTabToTab`)按 **route 对** 分派。
  Tab 根集合 = `LedgerRoutes.HOME/CALENDAR/ASSETS/SETTINGS_TAB` + `StatsRoutes.STATS_TAB`
  (四/五个常量路由,`ledgerGraph`/`statsGraph` 中均为无参 `composable`)。见 Q1 骨架。
- **方案 B**:在四个 feature 的 graph 里给**每个 Tab 根**同时覆写四个过渡;注意 enter 取进入方、
  exit 取退出方的不对称(见 Q2),否则 Tab↔次级页会出现「一边滑、一边淡」。骨架见 Q2。

---

## 相关规格

- `.trellis/spec/android/ui-contract.md` — 契约 #4(禁止字面量,走 designsystem token)、
  #6(平台行为/无障碍优先)、#5(版本与确认记录)。
- `.trellis/spec/android/ui.md` — 「样式」节要求动效时长取 `VipLedgerTokens.Duration*`;
  已踩坑清单(import 清理、`LaunchedEffect` 不可调 `@Composable`)与本任务直接相关。
- 代码事实(已核对):
  - 根 `NavHost`:`app/src/main/kotlin/com/han1997/vipledger/ui/VipLedgerApp.kt:98`(已挂未提交的全局过渡 `:103-126`)。
  - Tab 切换 `navigateTab()`:`VipLedgerApp.kt:149`(`popUpTo`+`saveState`+`launchSingleTop`+`restoreState`)。
  - 五 Tab 根路由与显示判定:`VipLedgerApp.kt:88-91`、`209-240`。
  - 20 个 destination 注册:四个 graph 文件(**均未覆写过渡**)。
  - 动效 token:`core/designsystem/.../theme/Tokens.kt:66-68`
    (`DurationShort=120 / DurationMedium=220 / DurationLong=300`,`DurationLong` 注释为「页面切换上限」);
    断言 `core/designsystem/src/test/.../TokensTest.kt:49-55`(`DurationLong <= 300`)。
  - 原型侧 `docs/ui-prototype/style.css`:**426 行** `@media(prefers-reduced-motion:reduce){...none!important}`
    全局兜底;**440-442 行** `.phone-page{animation:vl-slide .2s ...}` + `@keyframes vl-slide`
    (`translateX(16px)`→0,即原型「次级页从右滑入 16px」的 CSS 表达);`app.js:212` `go(page)` 为瞬态切页、
    无翻页动画。

## Caveats / 未能确认

1. **外网不可用**:所有 `developer.android.com` 链接为**参考链接(未抓取验证)**。API 结论已改用
   「本机确切依赖源码」佐证,证据强度不低于文档。参考链接(标准入口):
   - Navigation Compose 过渡:`https://developer.android.com/develop/ui/compose/navigation`
   - `NavHost` 参考:`https://developer.android.com/reference/kotlin/androidx/navigation/compose/package-summary`
   - Compose 动画总览:`https://developer.android.com/develop/ui/compose/animation/composables-modifiers`
   - `MotionDurationScale`:`https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale`
   - `Settings.Global.ANIMATOR_DURATION_SCALE`:`https://developer.android.com/reference/android/provider/Settings.Global#ANIMATOR_DURATION_SCALE`
   - 预测性返回:`https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture`
2. **「移除动画」设置项 → `ANIMATOR_DURATION_SCALE=0` 的官方文字映射未抓取**(离线)。源码侧只证明
   Compose 读该 key;设置项名到 key 的对应属常识性映射,置信**中**。
3. **`MotionDurationScale` 经 composition 协程上下文读取**(路径 1)可编译且 API 非实验,但**非官方
   文档化的推荐检测范式**;若评审要求「只用官方推荐 API」,应选路径 2(`Settings.Global` + ContentObserver)。
4. **预测性返回「无撕裂」未在真机验证**——本研究的手势协同结论来自 2.9.5 源码逻辑;PRD 验收项
   「Android 14+ 预测性返回下无撕裂/错位」仍需真机走查确认(尤其 `it/6` 位移 + 250ms 手感)。
5. **`navigation-compose-2.7.6` 的旧行为未逐行对比**;表内标注为「2.9.x 专属」的预测返回能力,
   请勿按旧版理解。