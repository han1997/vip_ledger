# M0 规划落盘与工程骨架 PRD

## 目标

把总体规划写进仓库（防中断丢失），并让工程骨架能编译、能测、能在模拟器跑起来，
为 M1 的数据层开发提供稳定地基。

## 依赖

无上游任务。本任务产出供 M1 到 M7 全部使用。

## 范围

1. `docs/PLAN.md`、`docs/ARCHITECTURE.md`、`docs/DATA-MODEL.md` 三份权威文档落盘。
2. `docs/DEV-NOTES.md` 记录本机环境、镜像与版本矩阵坑位。
3. git 初始化与 `.gitignore`。
4. Gradle 多模块骨架：`:app`、`:core:{common,model,domain,database,data,designsystem}`、
   `:feature:{ledger,stats,ai,sync}`、`:ml:classifier`，统一走阿里云镜像与 version catalog。
5. `:core:model` 落地 `Money`（整数分）、`DateRange`、账目与账户枚举；
   `:core:common` 落地 `AppResult`/`AppError`/`DispatcherProvider`，各自带单测。
6. `:core:designsystem` 落地 Material 3 主题；`:app` 落地 Compose 空壳首页
   （展示工程状态与金额格式化结果）。
7. `.trellis/spec/android/` 规范填充，删除不适用的 frontend/backend 占位。

## 明确不做

不做任何业务表结构、DAO、仓库实现与真实记账界面（属于 M1 与 M2）。

## 验收标准（已达成）

- `.\gradlew.bat test assembleDebug` 通过：18 个单元测试全绿，产出可安装的 debug APK。
- APK 安装到 AVD `nordic-ui-api34` 后能启动并渲染首页，无崩溃。
- `.trellis/spec/android/` 下 10 个规范文件为真实约定（非占位）。
- `docs/PLAN.md` 进度看板勾选 M0 及其子项。

## 实现要点与约束

- 版本矩阵：Kotlin 2.2.10、KSP 2.2.10-2.0.2、Hilt 2.57.2、AGP 8.13.1、Gradle 8.14、
  Compose BOM 2026.06.01、compileSdk 36。三者互相牵制，理由见 `docs/DEV-NOTES.md` 第 3 节。
- 纯 Kotlin 模块（model/common/domain/classifier）不得依赖 Android SDK。
- 金额一律整数分，禁止浮点参与金额运算；`Money.splitEvenly` 保证拆分后总额不变。
- 首页文案全部来自 `strings.xml`，主题来自 `core:designsystem`。

## 产出文件（关键）

- 构建：`settings.gradle.kts`、`build.gradle.kts`、`gradle/libs.versions.toml`、
  `gradle.properties`、`gradlew*`、各模块 `build.gradle.kts`。
- 代码：`core/model/.../money/Money.kt`、`core/model/.../time/DateRange.kt`、
  `core/model/.../LedgerEnums.kt`、`core/common/.../result/AppResult.kt`、
  `core/designsystem/.../theme/*.kt`、`app/.../MainActivity.kt`、`app/.../ui/home/HomeScreen.kt`。
- 验证：`core/model/src/test`、`core/common/src/test`。

## 下一步（M1）

按 `docs/DATA-MODEL.md` 建 Room 实体与 DAO、写迁移与迁移测试、
在 `core:domain` 定义仓库接口与用例，并实现分期利息与统计聚合的纯逻辑加单测。
