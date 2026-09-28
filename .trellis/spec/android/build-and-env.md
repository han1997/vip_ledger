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
  当前 release 没有正式签名配置，产物是 `app-release-unsigned.apk`；装到已连接的开发机前，用本机 debug keystore 签成 `app-release-debugsigned.apk` 再 `adb install -r`。不要把 keystore、密码或已签名 apk 提交进仓库。

- 保持 `org.gradle.java.installations.auto-download=false`，否则构建会去 foojay 拉 JDK 并失败。
- 若项目根再次出现 `gradle/gradle-daemon-jvm.properties`（AGP 9 产物），删掉它。
- 首次构建要从镜像拉依赖，耗时数分钟属正常，之后走缓存。
