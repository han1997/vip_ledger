# 构建与环境

## 依赖仓库

- 仓库配置在 `settings.gradle.kts`：阿里云镜像在前，官方源兜底；
  新增仓库时保持这个顺序，否则国内网络会拖慢甚至卡死构建。
- 版本只写在 `gradle/libs.versions.toml`；SDK 版本用 `libs.versions.compileSdk` 这类引用，
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
  release 已配置正式签名，产物是 `app/build/outputs/apk/release/app-release.apk`；本地无需再用 debug keystore 事后签名。
- release 签名来自根目录未跟踪的 `keystore.properties`（四个键：`storeFile`/`storePassword`/`keyAlias`/`keyPassword`）
  与本地 keystore（`keystore/vipledger-release.jks`，alias `vipledger`）。`keystore.properties` 不存在时构建不失败，
  只是产出未签名 apk，因此新鲜 clone 与未来 CI 都能编译。
- debug 构建复用同一把正式签名：真机排障用 `.\gradlew.bat :app:assembleDebug` +
  `adb -s <设备> install -r app\build\outputs\apk\debug\app-debug.apk` 快速覆盖安装，不丢测试数据；
  最终交付仍以 Release 全量门禁为准。
- 红线：不要把 keystore、`keystore.properties`、密码或已签名 apk 提交进仓库。keystore 只在本机，
  换机或重装后要先从密码管理器取回 keystore 与密码，否则无法用同一把 key 覆盖安装已发布版本。

- 保持 `org.gradle.java.installations.auto-download=false`，否则构建会去 foojay 拉 JDK 并失败。
- 若项目根再次出现 `gradle/gradle-daemon-jvm.properties`（AGP 9 产物），删掉它。
- 首次构建要从镜像拉依赖，耗时数分钟属正常，之后走缓存。

## 文件编码

- 仓库内所有源码、配置与文档统一使用 UTF-8（无 BOM）；禁止新建 GBK/GB2312 或带 BOM 的文件。
- 根目录 `.editorconfig` 固化 `charset = utf-8`，编辑器按该约定落盘；遇到带 BOM 的历史文件只移除 BOM，不顺手改内容。
- `gradle.properties` 保持 `org.gradle.jvmargs` 里的 `-Dfile.encoding=UTF-8`，JVM 编译与运行统一 UTF-8。
- Windows PowerShell 查看中文文件先执行 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`，再用 `Get-Content -Encoding UTF8`；控制台乱码不代表文件编码错误，禁止因此反复转码。
- CSV/JSON 导出统一用 `Charsets.UTF_8` 且不写 BOM（`UriWriter.writeTextToUri` 现状即如此）；`tmp/` 等被 gitignore 的本地产物不纳入本约束。
