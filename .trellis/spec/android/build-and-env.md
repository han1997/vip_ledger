# 构建与环境

## 依赖仓库

- 仓库配置在 `settings.gradle.kts`：阿里云镜像在前，官方源兜底；
  新增仓库时保持这个顺序，否则国内网络会拖慢甚至卡死构建。
- 依赖与 SDK 版本只写在 `gradle/libs.versions.toml`；SDK 版本用 `libs.versions.compileSdk` 这类引用，
  模块里禁止出现字面量 36 / 26。

## 版本矩阵约束（改前必读 `docs/DEV-NOTES.md` 第 3 节）

- AGP 必须留在 8.13.1：AGP 9 会强制 Java 25 工具链，本机不可用。
- Kotlin 必须留在 2.2.10：Hilt 2.57.2 读不了 Kotlin 2.3 元数据，而 Hilt 2.58 起要求 AGP 9。
- Compose BOM 必须满足 `minCompileSdk` 不超过 36；可用读 `aar-metadata.properties` 的办法确认。
- 升级任何一环前，先跑通 `.\gradlew.bat test assembleDebug` 再合入。

## 构建命令

```powershell
.\gradlew.bat test assembleDebug assembleRelease
.\gradlew.bat lintDebug
```

- 以后用户说「编译」时，默认同时产出 debug 与 release：`assembleDebug assembleRelease`。
  release 已配置正式签名，产物是 `app/build/outputs/apk/release/fun.han1997.vipledger-v<versionName>-<versionCode>-release.apk`；本地无需再用 debug keystore 事后签名。
- release 签名来自根目录未跟踪的 `keystore.properties`（四个键：`storeFile`/`storePassword`/`keyAlias`/`keyPassword`）
  与本地 keystore（`keystore/vipledger-release.jks`，alias `vipledger`）。`keystore.properties` 不存在时构建不失败，
  只是产出未签名 apk，因此新鲜 clone 与未来 CI 都能编译。
- debug 构建复用同一把正式签名：真机排障用 `.\gradlew.bat :app:assembleDebug` +
  `adb -s <设备> install -r app\build\outputs\apk\debug\fun.han1997.vipledger-v<versionName>-<versionCode>-debug.apk` 快速覆盖安装，不丢测试数据；
  最终交付仍以 Release 全量门禁为准。
- 红线：不要把 keystore、`keystore.properties`、密码或已签名 apk 提交进仓库。keystore 只在本机，
  换机或重装后要先从密码管理器取回 keystore 与密码，否则无法用同一把 key 覆盖安装已发布版本。

- 保持 `org.gradle.java.installations.auto-download=false`，否则构建会去 foojay 拉 JDK 并失败。
- 若项目根再次出现 `gradle/gradle-daemon-jvm.properties`（AGP 9 产物），删掉它。
- 首次构建要从镜像拉依赖，耗时数分钟属正常，之后走缓存。

## 文件编码

- 本轮观察到新增domain类已进入domain.jar，但长驻Gradle daemon内的KSP仍报告无法解析。先核验jar，再用`gradlew --stop`重启构建进程；必要时单次`'-Pksp.incremental=false'`，不要为缓存问题改依赖版本。PowerShell中带点的`-P`参数必须整体引号，否则会被拆成任务名。之后普通增量构建已通过。

- 仓库内所有源码、配置与文档统一使用 UTF-8（无 BOM）；禁止新建 GBK/GB2312 或带 BOM 的文件。
- 根目录 `.editorconfig` 固化 `charset = utf-8`，编辑器按该约定落盘；遇到带 BOM 的历史文件只移除 BOM，不顺手改内容。
- `gradle.properties` 保持 `org.gradle.jvmargs` 里的 `-Dfile.encoding=UTF-8`，JVM 编译与运行统一 UTF-8。
- Windows PowerShell 查看中文文件先执行 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`，再用 `Get-Content -Encoding UTF8`；控制台乱码不代表文件编码错误，禁止因此反复转码。
- CSV/JSON 导出统一用 `Charsets.UTF_8` 且不写 BOM（`UriWriter.writeTextToUri` 现状即如此）；`tmp/` 等被 gitignore 的本地产物不纳入本约束。

## 应用版本与 APK 标识

### 1. 适用范围

每轮应用源码、资源或构建配置修改交付前递增一次；同轮修复、重复编译及纯文档/Trellis 修改不重复递增。应用版本独立于依赖和数据库 schema。

### 2. 命令与接口

- `python -B tools/version.py bump --message "更新说明"`：默认 patch；可指定 `--part minor|major`。Python 3.8+，无第三方依赖。
- AGP 8.13.1 使用公开 `com.android.build.gradle.api.ApkVariantOutput.outputFileName` 命名并维护输出元数据，不依赖内部实现类或打包后移动 APK。
- 导航层 `rememberInstalledAppVersion()` 查询当前包的 PackageManager 信息，向 `SettingsScreen` 传入 `InstalledAppVersion(name: String, code: Long)`。页面不引用 library BuildConfig，预览传示例数据。

### 3. 数据契约

- 唯一应用版本来源为根目录 `version.properties`：`versionName` 为无前导零的 major.minor.patch，`versionCode` 为 1..2100000000 的整数。
- bump 始终将 versionCode 加一，升级 major/minor 时清零低位，同步在 `CHANGELOG.md` 顶部追加日期和单行更新说明。Gradle 仅校验和读取，不写版本。
- applicationId 为 `fun.han1997.vipledger`，源码包及 namespace 保持 `com.han1997.vipledger`。改 applicationId 是独立安装，保留旧应用及数据，不自动迁移。
- APK 使用实际 variant 的 `vipledger-v<versionName>-<buildType>[-unsigned].apk`（固定 `vipledger` 前缀，不带 applicationId 与 versionCode）；无签名 Release 加 `-unsigned`。安装时按当前版本精确选取，目录可能残留旧 APK。

### 4. 校验与错误

| 条件 | 行为 |
|---|---|
| 无效版本、重复属性、空或多行说明、日志标题无效 | bump 非零退出，不修改文件 |
| versionCode 已达上限 | 拒绝递增 |
| 日志写入失败 | 恢复版本原始内容，包括换行 |
| Gradle 读取的版本格式或范围无效 | 配置失败 |

### 5. 正常与异常示例

- 基本：1.2.9（42）执行 patch → 1.2.10（43），保留历史日志。
- 次版本：1.2.9（42）执行 minor → 1.3.0（43）；major → 2.0.0（43）。
- 异常：01.2.3、内部版本号 0、空说明均拒绝写入。

### 6. 验证要求

`python -B -m unittest discover -s tools/tests -v` 使用临时文件覆盖上述规则和写入失败恢复；构建核对 APK 包名、版本、签名、输出元数据及重复构建版本不变。我的页无账本时也要显示实际安装版本。

### 7. 易错点

错误：在 Gradle 配置或每次 assemble 时自增版本，或根据文件排序安装第一个 APK。
正确：交付前显式 bump 一次，构建只读，安装精确匹配当前版本的已签名 Release。
