# P0/P1 首页、预算、日历

需求已确认，详见 docs/FEATURE-ROADMAP.md P0/P1。首批四Tab；首页双列卡片+按日流水；月历与选日记账；自然月总预算与两级分类预算；80%/100%去重提醒；日期时区变化刷新；数据库schema3、快照格式3，兼容1/2。

## 实现决策
- budget_limit 记录按月生效的额度（null为停用）。同scope查询小于等于目标月的最新记录；编辑历史月若无下一月覆盖，先为下一月保存修改前额度，使修改仅影响目标月，当前月修改可供未来沿用。
- budget_alert 保存账本/月/scope/阈值去重；无通知权限不认领，应用内进度仍提示。
- domain 共用 StatsCalculator/TxnDayGrouper。日期观察流以设备时区刷新，页面恢复立即读当前日期。
- 日历不保存派生汇总；选中日期通过 SavedStateHandle 恢复，新建日期路由仅对新增生效。
- 所有新用户文案使用资源；不引新生产依赖。为复现分类节点崩溃启用catalog中已有的Compose设备测试依赖。

## 验收
- [x] 总预算及父子分类、跨月继承/修改/停用、退款/草稿/排除与零额度
- [x] 首页/日历/预算/统计口径一致及跨月跨年日期刷新
- [x] schema1/2升级3不丢数据，旧备份恢复，新预算快照往返
- [x] 模拟器预算保存/重启、切月/返回今天/选日记账/直接编辑/四Tab；月份布局及跨月继承另有单测覆盖
- [x] test assembleDebug assembleRelease lintDebug
- [x] bump一次，签名Release验证，模拟器覆盖安装并核验版本；本轮只有模拟器连接，无真机

## 交付记录
0.1.2（3），最终签名Release SHA256：389184D69E9FD2AC5ECA524892FE873D426BE2476ACD723B25FC19DFC1D79535。
JVM 607个唯一用例、881次变体执行全部通过；另4个Python版本工具测试及1个Compose模拟器测试通过。
最后一次全门禁见本地忽略日志tmp-dashboard-final.log（2m23s）；本地截图/UI证据tmp/dashboard。
模拟器创建1000元预算，重启保留；10月8日录入DashboardTest/午餐100元，日历/首页/统计均100，预算剩900；首页直接编辑、Release分类展开/切换及未保存退出确认通过。深浅色及1.3字体走查，模拟器设置已恢复。
修复既有分类展开Compose节点越界，见bug-analysis.md与CategorySectionTest。
当前实现与验证完成，提交/归档依workflow3.4的一次确认执行；总任务P2–P8尚未实现。
