# UI(Compose)

## 原型优先(Prototype-First)

本轮(VipLedger Concept 01「墨绿金 · 静奢记账」)起,UI 改动先改原型、后改 Compose。

- 视觉基准在 `docs/ui-prototype/`(`index.html` / `style.css` / `app.js`),BASELINE.md 登记已确认范围
  与文件 SHA-256。**正式实现不得偏离已确认的原型**;原型没有的界面,先在原型补出来并确认,再写代码。
- 原型是**交互式应用**(内存态 SPA),不是静态稿:改动要能点、能切换、能记账,并跑
  `docs/ui-prototype/smoke-test.cjs`(Playwright,需 `PLAYWRIGHT_MODULE` 指向本机模块)。
- **原型与说明文字分离**:设计说明、章节导航、色卡放手机外的工作台 / 说明面板 / README,
  手机内只呈现应用界面,不写标注文字。
- 原型 token → Compose 映射见应用内「设计系统与 Compose 映射」弹层:墨绿主色 `#1A3C34`、
  纸感底 `#F7F5F0`、黄铜金 `#B08D57`;语义色(支出/收入/转账)与 4dp 网格沿用现有 token,
  卡片圆角 12→16,衬线金额数字需引入字体资源。
- 原型变更后同步更新 `BASELINE.md` 的摘要(差异部分回到「待确认」),并在原型 README 记录。
- 原型两条硬性原则(v1.2 起的):**无假按钮**(每个可点元素都有真实效果)与
  **所有页面在原型内**(二级功能用手机内子页面/底部弹层,不跳出三栏结构)。
- **禁止浏览器原生弹窗**(v1.3 起):不得用 `prompt`/`alert`/`confirm`;新增/输入类交互统一用
  手机内输入弹层。原型 `smoke-test.cjs` 用探针拦截并断言未被调用。
- **记一笔以「不滑动即可记账」为第一原则**(v1.4 起):正文仅类型分段 + 金额 + 分类 chip;
  账户 / 日期时间 / 标签 / 备注 放在底部停靠区的四格快捷行(手机内弹层),与数字键盘同屏。
- **已知有意偏离(待 Compose 同步确认)**:原型 v1.2 取消了编辑页「不选分类」首格
  (分类恒有值)且编辑页一屏不滚动;真机 `TxnEditScreen` 目前首格是可清除的「不选分类」且可滚动。
  在用户确认前,不得用原型行为反向覆盖真机实现;改真机需单独任务。
- **账户类型可编辑 + 卡号(待 Compose 同步确认)**:原型 v1.4 在账户详情增加「修改类型」
  与「卡号」两行(银行卡类型 储蓄/信用/预付 才会出现卡号,4 位分组展示);
  这属于资产模块的新字段/新交互,真机是否落库 `cardNo` 需先确认再写代码,
  不得因原型先改资产 schema。
- **编辑页原型与真实编辑页口径对齐**:时间精确到秒 `HH:MM:SS`、二级区插父级所在行下、
  转账分转出/转入账户、保存防重。改编辑页 UI 时两侧同时改,并同步跑原型 `smoke-test.cjs`。

## 界面与状态

- 一个屏幕三件套：`XxxScreen`（无业务逻辑）、`XxxViewModel`、`XxxUiState`（不可变 `data class`），三者同包。
- 交互统一走 `XxxAction`：Screen 只调用 `viewModel.onAction(action)`，不直接调用仓库。
- 状态用 `StateFlow<XxxUiState>` 暴露，`collectAsStateWithLifecycle()` 收集。
- 一次性事件（弹提示、导航、弹 SAF 选择器）用 `Channel` 或状态字段 + 消费后置空，不要用 LiveData。
- `LabeledTextField` 的 Password/NumberPassword 键盘必须同时使用 `PasswordVisualTransformation`；仅设置键盘类型不会遮蔽字符。回归见 `PasswordFieldTest`，普通文本/金额仍需正常显示。
- UiState 里可以放「数据 + 选中项」，但**不放中文文案、不放 `Context`/`Uri`**；需要查表的名字用
  `categoryNames` / `accountNames` 这类 `Map<String, String>` 由 ViewModel 组装。

## 样式

- 主题只用 `core:designsystem` 的 `VipLedgerTheme`；禁止在屏幕里写死颜色值。
  语义色（支出红、收入绿、转账蓝）从 designsystem 取；图表配色用 `chartColorOf(index)`。
- **V2 起必须走 designsystem 组件与 token，不再手写视觉模式**（V2 UI 设计系统任务确立）：
  - 间距/圆角/边框/elevation/图标尺寸/动效时长/语义 alpha 一律取 `VipLedgerTokens`
    （`Space1..Space8` 4dp 网格、`RadiusSmall..Full`、`ElevationLevel0..5`、`TouchTargetMin`、`Duration*`、`Alpha*`）；
    LazyColumn 预留 FAB 的 `bottom = 96.dp` 这类布局常量可豁免。
  - 强调色/装饰色从 `vipLedgerAccentColors()` 取（`accentMint/Coral/Amber` + hero 系）；禁止在 feature 里
    `Color(0x…)` 字面量或对语义色做 alpha 变体。强调色是**有限装饰资源**：不进语义色、不进图表色板。
  - 分组容器用 `GroupCard`，分组标题用 `SectionHeader`，可点击行用 `ActionRow`（自带 48dp 触摸目标、
    禁用态、`Role.Button` 语义），按钮用 `PrimaryButton`/`SecondaryButton`/`DangerButton`，
    不可逆操作确认用 `VipLedgerConfirmDialog(danger = true)`，snackbar 反馈用
    `VipLedgerSnackbarHost`（Info/Success/Error），加载态用 `LoadingState`，状态标签用
    `StatusBadge`（五档 `BadgeTone`，调用方不得自带颜色）。
  - **HeroCard 上的文字必须显式取 `onHero`/`onHeroMuted`**：hero 是深绿渐变（深色模式退化为纯色），
    默认 `onSurface` 在上面不可读；这是 V2 首页迁移时踩过的坑。
  - 新增共享组件先查 `core/designsystem/component/` 是否已有（GroupCard/ActionRow/SectionHeader/
    StatusBadge/VipLedgerButton/VipLedgerConfirmDialog/VipLedgerSnackbarHost/LoadingState/HeroCard），
    不要在 feature 里重写同类视觉模式；组件一律无状态 + `@Preview` + 文案由调用方传入。
- 金额展示统一 `Money.toDisplayString()`；列表里的金额右对齐、等宽数字，避免跳动。
- 文案全部进 `strings.xml`；带参数的用占位符，不在 Kotlin 里拼中文。
  跨 feature 共用的文案放 `core:designsystem/src/main/res/values/strings.xml`，feature 自有文案加模块前缀。
- 日期/星期/类型/账户类型这类拼装放 `core.designsystem.format.Formatters`（`@Composable` 取串 + 填参），
  不要在屏幕里手写字符串模板。
- 列表用 `LazyColumn` + `key`，滚动状态用 `rememberLazyListState` 保存。
- **一屏一个 `LazyColumn`**：图表 + 排行 + 列表都塞进同一个 `LazyColumn`，用 `item {}` / `items(...)` 拼装。
  `LazyColumn` 里再套 `LazyColumn` 或 `Modifier.verticalScroll` 会抛
  `Vertically scrollable component was measured with an infinity maximum height constraints`。
  不可折叠的长内容用 `item { Column { ... } }` 包。
- 筛选条件这种「多页共用的一组控件」放模块的 `ui/common/`（如 `FilterComponents` / `TxnFilterState`），
  搜索页与统计页共用同一份状态与控件，避免两套条件语义漂移。

## 统计类页面的口径

- 统计页默认时间段必须是「本月」（`DateRangePreset.THIS_MONTH`），与首页「本月收支」一致；
  默认「不限时间」会让两个页面的数字对不上（M3 验收项之一）。
- 同一口径的聚合只允许有一处实现：按天分组走 `TxnDayGrouper.group(...)`，参与统计的判断走
  `StatsCalculator.isCounted`。明细、搜索结果、下钻三处的「当日合计」都复用前者，否则数字会漂移。
- 同屏图表与排行用同一份 `rows`（见 `StatsUiState.statsRowsOf`），不要各自再算一遍金额。
- 跨页面/跨模块的口径一致性用单测钉住（`StatsMatchesHomeOverviewTest`），别靠手工对数。

## 预览与可测性

- 每个 Screen 配一个 `@Preview`，用假数据渲染；预览不得依赖 Hilt。
- 组件保持无状态优先：状态从参数传入，便于 Compose 测试直接渲染。
- 表单类 UiState 的「默认选中 / 合法性」这类规则写成纯函数放在 UiState 上（如
  `TxnEditUiState.resolvedTargetAccountId()`），并在模块 `src/test` 里覆盖；
  放 Screen 里就只能靠手工走查，M2 的转账保存失败就是这么漏掉的。
- 列表 → 排行/图表的映射同理写成 UiState 文件里的纯函数（如 `statsRowsOf(...)`），不写在 Screen 里。
- 无状态组件的核心算术（图表几何、占比、桶边界）抽成纯函数对象单独测（见 charts.md）。

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
   图表数据类同理用 `Long` 分（见 charts.md）。
3. **`SavedStateHandle.get<String>("arg")` 缺参时返回 `""` 而不是 null**（Nav 路径参数缺失）。
   用 `.orEmpty()` 或 `.takeIf { it.isNotBlank() }` 归一，别直接 `?:` 判断。
   可选查询参数的解析要连「非法值」一起兜住：解析失败的枚举名/日期当作没传
   （参考 `feature:stats` 的 `drillArgsOf(...)`）。
4. **状态里不放中文**：一次性提示用枚举（`HomeNotice` / `ManageNotice` / `SearchNotice`）+ 界面层 `stringResource` 查表；
   但领域层 `AppError.message` 本身就是给用户看的说明，失败提示应优先透出它（别一律显示「操作失败」）。
5. **id → 名字的查表集中在一处**：`core.designsystem.format.NameMaps` 的 `categoryNameMap()` /
   `accountNameMap()` / `tagNameMap()`（`feature:ledger` 与 `feature:stats` 共用）。账目只存 id，
   任何列表都要显式补齐名字，漏了就显示成「未分类」。
6. **导出图片要 `drawWithContent` + `graphicsLayer` 录制**：
   ```kotlin
   val layer = rememberGraphicsLayer()
   Box(Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) { ... }
   // 导出时
   val image = runCatching { layer.toImageBitmap() }.getOrNull() ?: return onExportFailed()
   ```
   只在 `drawWithContent` 里包一层 `graphicsLayer` 不会录制内容；`record`/`toImageBitmap` 在部分设备/版本上
   会抛异常，必须 `runCatching` 兜底并提示「导出失败，可改用导出 CSV」，不能崩（见 import-export.md）。
7. **彩色容器上的文字不能用默认 onSurface**：品牌绿渐变/容器（`HeroCard`、`primaryContainer` 系）上必须显式
   取对应的 on 色（`onHero`/`onHeroMuted` 或 `onPrimaryContainer`），浅深两套都要核对对比度（≥4.5:1）。
   `StatusBadge`、`VipLedgerSnackbarHost` 的配对色已内置，调用方不要再自行配色。
8. **替换 material3 裸组件后要删干净 import**：`CircularProgressIndicator`/`SnackbarHost`/`ConfirmDialog`
   换成 designsystem 版本后，残留的无用 import 不会报编译错但会混入下一次 grep 排查；
   收尾时 `grep -rn "material3.SnackbarHost$\|material3.ConfirmDialog\|material3.CircularProgressIndicator$" feature/` 应零命中。
9. **`LocalDateTime(y, m, d, h, min, s, X)` 第 7 个参数是 `nanosecond` 不是毫秒**：要传「321 毫秒」
   必须写 `321_000_000`；写 `321` 只代表 321 纳秒，断言会莫名其妙地失败（见 `TxnEditTimeTest`）。
   `LocalDateTime(...).toInstant(zone)` 需要 `@file:OptIn(ExperimentalTime::class)`。

## 设置页

## 记账编辑页（记一笔 / 编辑账目）

- 单屏单操作：整页只有一个底部 `PrimaryButton` 保存（顶栏不再放保存），保存后返回，不做连续记账。
- 时间精确到秒：`SelectDate(LocalDate)` 只换日期（保留时分秒/毫秒），`SelectTime(LocalTime)` 只换时间；
  今天/昨天由 `todayMillis/yesterdayMillis` 实时计算，进页面发一次 `RefreshToday`。
  纯换算函数在 `TxnEditTime.kt`（`replaceEntryDate/replaceEntryTime/parsedEntryTime/parseEntryClock`），
  单测 `TxnEditTimeTest`。AI 识别出时刻时用识别值（`parsedEntryTime`），识别不出才沿用手表时刻。
- 分类是一级网格（`chunked(4)` 拼 `Row`，不用 `LazyVerticalGrid`，避免嵌套滚动），
  二级区插在被点一级所在那一行下方，一次只展开一个父级（`expandedCategoryId`）；
  首格「不选分类」= 清除。网格单元 `maxLines = 2`，不要退回 `weight(1f)` 单行省略。
- 标签在本页搜索/新建：输入非空时列表过滤为 `matchingTags`，同名命中走 `existingTag` 复用（不重复建），
  新建失败保留输入并就地提示。
- 未保存退出（顶栏返回与系统返回）走 `BackHandler` + `VipLedgerConfirmDialog`（判 `hasUnsavedChanges`）；
  保存防重统一看 `isBusy`（`isSaving || isCreatingTag`），不要只判断 `isSaving`。

- 应用主导航使用底部五 Tab：`Home`、`Calendar`、`Assets`、`Stats`、`Settings`。首页概览下方主按钮记账，日历FAB携带选中日期，统计页保留FAB；首页卡片设置按账本保存。AI从编辑器进入。
- `SettingsScreen` 保持无业务 ViewModel；Tab 根由导航层通过 `SettingsTabViewModel` 观察当前账本并传入启用状态与带账本 ID 的回调。没有当前账本时，依赖账本的入口禁用。
- 「我的」页分组为账本、计划、AI 配置（模型管理/商户规则）、数据（备份）；统计只在独立 Tab 提供，AI 记账入口在记账页金额输入旁。
- 设置 Tab 根不显示返回箭头；二级管理页仍使用带账本 ID 的路由并显示返回箭头。底部栏只在五个 Tab 根显示，二级页隐藏。

### 动态分类插入的渲染回归

2026-10-09在Release/Debug模拟器复现：分类循环先发射Row，再用`?: return@forEach`跳过后续节点，展开时触发MutableVector.add / LayoutNode.insertAt越界。分类行使用稳定key及独立Column，以结构化if决定子分类，不在已发射节点的循环中用标签return控制后续节点。CategorySectionTest实际重复展开/跨行切换/收起，须通过 `:feature:ledger:connectedDebugAndroidTest`；UiState单测不能验证节点树。
- 通用设置入口走strings.xml和我的页分组；只影响首页本身的卡片配置通过首页顶栏Tune按钮进入（P2）。
