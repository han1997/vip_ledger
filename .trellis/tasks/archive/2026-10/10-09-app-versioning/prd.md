# 版本管理与应用包名调整

## 已确认需求
- 每轮应用修改交付时递增一次；重复编译、同轮修复和纯文档修改不递增。
- 本次 0.1.1（2），applicationId 为 fun.han1997.vipledger；源码 namespace 保持现有值。
- 新版空数据开始，旧应用及数据保留，不做跨包名迁移。
- 根目录 version.properties 为唯一应用版本来源；Python bump 工具支持 patch/minor/major，校验输入并维护 CHANGELOG.md。
- APK 使用 <applicationId>-v<versionName>-<versionCode>-<buildType>[-unsigned].apk。
- 我的页底部从 PackageManager 读取并展示实际安装版本，预览使用假数据。
- 更新 README、AGENTS 和构建规范。

## 验收
- Python 临时文件测试：版本递增、非法输入不写入、更新日志。
- test assembleDebug assembleRelease lintDebug 通过。
- APK 包名、版本、签名、输出元数据一致，重复构建不改变版本。
- 我的页在无账本时也展示版本。
- 交付检查 ADB；唯一真机连接时 install -r 最新 Release 并核验，不自动操作真机页面。

## 边界
不增加在线升级、应用内更新日志页或数据库迁移。用户已有的未跟踪 ZIP 不纳入修改。

## 研究
本机 AGP 8.13.1 源码确认公开 com.android.build.gradle.api.ApkVariantOutput 支持 outputFileName；新 VariantOutput 接口尚不提供该属性。
Android 官方 configure-app-module 文档确认 applicationId 与 namespace 可独立配置，改 applicationId 后是独立应用。

## 完成情况（2026-10-09）
- 已实现版本文件、Python 工具与临时文件测试、更新日志、包名/APK 命名、我的页版本，以及 README/AGENTS/spec。
- `python -B -m unittest discover -s tools/tests -v`：4 个测试方法通过，覆盖 patch/minor/major、7 组非法输入、非法日志和模拟日志写入失败恢复。
- `test assembleDebug assembleRelease lintDebug`：BUILD SUCCESSFUL，885 tasks；随后 `assembleDebug assembleRelease` 再次成功。AGP 公开旧版 ApkVariantOutput API 存在弃用提示，无编译/lint 错误。
- aapt 确认 Debug/Release 均为 fun.han1997.vipledger，versionName=0.1.1、versionCode=2；output-metadata.json 文件名一致。
- Release 通过 apksigner，证书 SHA256：1894b6cdcdd84f2d52a2a2911873b84976d498e3cb259cc58ea64203154ab3fd。
- 重复构建前后 version.properties SHA256 为 1DC0DBEBE9114401E1479EECB5CFAB17174503FA09FC4B5A4BFBE30C8E30406F，CHANGELOG.md SHA256 为 B1CCEF47261C469BE42D6DE92DA0B175568FF22EAC85434BF09AF969037DE0F1，均未改变。
- 模拟器 emulator-5554 成功安装 Debug 后覆盖安装 Release；dumpsys 确认版本，新旧应用并存。实际 UI dump 与截图确认“版本 0.1.1（2）”，截图在忽略目录 tmp/version-screen.png。
- 新安装自动创建默认空账本，默认账本禁止删除/归档，未通过正常 UI 构造零账本状态；代码检查确认版本展示不受 ledgerReady 控制。
- 当前仅连接模拟器，未连接真机，故未执行真机安装。无签名分支完成代码检查，未移走本机密钥来实测该分支。
- 代码已完成，用户已确认提交并归档；按 Trellis 流程记录 journal。原有账单 ZIP 不包含在提交中。
