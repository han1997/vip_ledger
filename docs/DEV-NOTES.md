# 开发环境与工具链注意事项

> 记录本机（Windows 11 + PowerShell 5.1）与依赖版本上踩过的坑。改构建配置前先读这里。
> 最后更新：2026-09-24（M2）

## 1. 工具链版本（已锁定，改动需同步本文与根 `gradle/libs.versions.toml`）

| 组件 | 版本 | 说明 |
|---|---|---|
| Gradle（wrapper） | 8.14 | 本机已缓存对应发行包 |
| AGP | 8.13.1 | 不要升到 9.x（见第 3 节） |
| Kotlin / KSP | 2.2.10 / 2.2.10-2.0.2 | 不要升到 2.3.x（Hilt 读不了 2.3 的元数据） |
| Hilt | 2.57.2 | 2.58 起要求 AGP 9.0.0 以上，因此卡在 2.57.2 |
| Compose BOM | 2026.06.01 | 对应 compose 1.11.4 / material3 1.4.0，要求 compileSdk 不少于 35 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 | 统一在 `libs.versions.toml` 的 `[versions]` 里改 |
| JDK | 17（`C:\Users\hanhu\.jdks\ms-17.0.18`） | `org.gradle.java.installations.auto-download=false`，不联网拉工具链 |

## 2. 依赖仓库

- 所有仓库指向阿里云镜像（google / public / gradle-plugin），官方源仅作兜底；直连 `maven.google.com` 实测超时。配置在 `settings.gradle.kts`。
- 需要查某个依赖有哪些版本时，直接读镜像上的 `maven-metadata.xml`：
  `https://maven.aliyun.com/repository/public/<group 路径>/<artifact>/maven-metadata.xml`
- 想知道某个 AAR 要求的最低 compileSdk：下载该 `.aar`，读其中的
  `META-INF/com/android/build/gradle/aar-metadata.properties`，字段是 `minCompileSdk`。

## 3. 版本矩阵的三个硬约束（踩坑记录）

1. **Compose 从 1.12.0 起要求 compileSdk 37 且 AGP 不低于 9.1.0**，所以停在 BOM 2026.06.01（compose 1.11.4）。
2. **AGP 9.x 会强制 Java 25 工具链**：跑一次 AGP 9.3.2 就会自动生成
   `gradle/gradle-daemon-jvm.properties`（内含 `toolchainVersion=25`），之后所有构建都报
   `Cannot find a Java installation ... languageVersion=25`。本机没有可用的 JDK 25
   （Android Studio 自带 JBR 不是完整 JDK），所以退回 AGP 8.13.1；该文件若再次出现，删掉即可。
3. **Hilt 与 Kotlin 元数据冲突**：Hilt 2.57 及更早版本的 `dagger.spi` 读不了 Kotlin 2.3 的元数据
   （构建停在 `hiltJavaCompileDebug`，报 `checkMetadataVersionForRead`），而 Hilt 2.58 及以上
   又要求 AGP 不低于 9.0.0。两头夹击的结果就是：Kotlin 卡在 2.2.10，Hilt 卡在 2.57.2。
   升级 Kotlin 前先确认 Hilt 是否支持，否则整个 DI 层会崩。

4. **kotlinx-datetime 0.7 起 `Instant` 换成了 `kotlin.time.Instant`**，在 Kotlin 2.2.10 上仍是实验 API：
   用到 `atStartOfDayIn` / `Instant.fromEpochMilliseconds` 的文件必须在文件头加
   `@file:OptIn(ExperimentalTime::class)`（见 `core/model/.../time/TimeConversions.kt`），
   否则编译报 "This declaration needs opt-in"。`LocalDate` 本身不受影响。
5. **Room 2.8 的自定义 schema 目录**：`ksp { arg("room.schemaLocation", "$projectDir/schemas") }`，
   导出的 `1.json` 必须提交进版本库，否则迁移测试读不到 assets。改表不升 `version` 会被
   `MigrationTest` 的身份哈希断言直接拦下。

## 3.5 M2 新增依赖（均已实测可从阿里云镜像解析）

| 依赖 | 版本 | 用途 |
|---|---|---|
| `androidx.hilt:hilt-navigation-compose` | 1.2.0 | `hiltViewModel()` 在 Nav 目的地里取 VM |
| `androidx.compose.material:material-icons-core` | 由 BOM 2026.06.01 锁定（1.7.8） | 顶栏与列表图标；注意不是 `material-icons-extended`（体积大） |
| `androidx.datastore:datastore-preferences` | 1.1.7（从 1.0.0 升上来） | 记录「当前账本」选择 |

`material-icons-core` 只带常用图标集（`Icons.Filled.Add/Check/MoreVert/KeyboardArrowDown` 等）；
用到别的图标时先确认是否在 core 里，不在就改用文字或自绘，别顺手引 extended。

## 4. Gradle wrapper 生成方式

本机全局 `gradle`（9.5）在项目里跑 `wrapper` 任务会因发行包 URL 校验失败而报错
（`services.gradle.org` 的 HEAD 校验不过）。可行姿势：在临时目录生成后再拷进项目，
并加上 `--no-validate-url`：

```powershell
$tmp = Join-Path $env:TEMP ("vlw-" + (Get-Random))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
Set-Content -Path (Join-Path $tmp "settings.gradle.kts") -Value 'rootProject.name = "wrappergen"' -Encoding UTF8
Set-Content -Path (Join-Path $tmp "build.gradle.kts") -Value '' -Encoding UTF8
gradle -p $tmp wrapper --gradle-version 8.14 --distribution-type all --no-validate-url
# 再把 gradlew、gradlew.bat、gradle/wrapper 下的两个文件复制进项目
```

## 5. Agent 编辑文件的坑（PowerShell 5.1）

- 经 `apply_patch.bat` 调用时，参数里的双引号会被 PowerShell 吞掉：轻则写进文件的代码缺引号，
  重则整块 patch 被拒（报 last line of the patch must be 之类的错误）。可行做法是直接调用
  `codex.exe`，并先把 patch 文本里的每个双引号替换成反斜杠加双引号：

```powershell
$exe = 'C:\Users\hanhu\AppData\Roaming\npm\node_modules\@openai\codex\node_modules\@openai\codex-win32-x64\vendor\x86_64-pc-windows-msvc\bin\codex.exe'
$quote = [char]34
$escaped = $patch.Replace($quote, [char]92 + $quote)   # 每个双引号前补一个反斜杠
& $exe --codex-run-as-apply-patch $escaped
```

- 另一个坑：patch 内容里不要让某一行以双引号结尾。转义后行尾会变成反斜杠加双引号，
  解析时会把换行吃掉，于是 `*** End Patch` 不再位于最后一行，整块 patch 被拒。
  需要时在该行末尾补一句注释，或改用 `[char]34` 写法。
- 写完文件立刻校验编码与引号：`Get-Content <file> -Encoding UTF8`，或用 `[System.IO.File]::ReadAllText()`。
- 删除文件用 `apply_patch` 的 `Delete File:`；`Remove-Item` 在部分策略下会被直接拒绝。
- 控制台里的中文可能因代码页显示成乱码，文件本身是 UTF-8，加 `-Encoding UTF8` 读即可。

## 6. 常用命令

```powershell
.\gradlew.bat test assembleDebug   # 全量单测 + 调试包，每次改完都跑
.\gradlew.bat lintDebug            # 静态检查
.\gradlew.bat :app:installDebug    # 装到已连接的模拟器或真机
py -3 ./.trellis/scripts/task.py list        # Trellis 任务（用 py -3 走 Python 3.12）
```

模拟器：AVD `nordic-ui-api34`（API 34）；本机在线设备用 `adb devices` 查看。

## 7. 测试环境（Robolectric / Room）

- Android 库模块的单测用 **Robolectric**：`testOptions { unitTests.isIncludeAndroidResources = true }`，
  并在 `src/test/resources/robolectric.properties` 里写 `sdk=34`。
  **必须显式写 34**：本机 `~/.m2/repository/org/robolectric/android-all-instrumented/`
  只缓存了 API 34 的 jar，默认跟随 `targetSdk`（36）会尝试联网下载并失败。
- `:core:database` 的迁移测试要把 `schemas/` 挂成 test assets：
  `sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")`。
- Room 2.8 的 `MigrationTestHelper` 在 Robolectric 下用 SupportSQLite 驱动会报
  `This driver is configured to open a database named ... but ... was requested`，
  所以 M1 用「导出 schema 身份哈希 vs 运行时 `room_master_table`」+「迁移链完整性」两条断言替代；
  真有迁移可测时改用 `createDatabase(version)` / `runMigrationsAndValidate(version, migrations)`
  这组基于 `SQLiteConnection` 的 API。
- 单模块跑测试：`.\gradlew.bat :core:database:test`、`.\gradlew.bat :core:domain:test`。
  注意 `test` 任务会同时跑 debug 与 release 变体，统计条数时不要重复算。
