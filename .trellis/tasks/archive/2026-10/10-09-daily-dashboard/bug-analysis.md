# 分类展开崩溃复盘

## 1. 根因类别
D 测试覆盖缺口、E 隐式假设。既有编辑页将一级Row及可选子分类直接发射到外层，循环中有发射后return@forEach路径。点击餐饮时，Debug和Release都在Compose MutableVector.add/LayoutNode.insertAt抛数组越界。

## 2. 排查
首次在Release日历记账验收复现；Debug独立从首页进入编辑，输入100再点餐饮同样复现，排除混淆及日期路由特有问题。没有升级依赖或隐藏异常。

## 3. 预防
每行稳定key+独立Column、结构化if约束子节点；新增CategorySectionTest，连续3轮跨行/同行展开切换收起，模拟器通过。纯UiState测试无法覆盖Composer真实变更。

## 4. 扩展检查
搜索编辑/管理UI的return@forEach；分类管理中的跳过发生于内容发射之前，不机械改写。首页、月历新增循环均使用结构化分支；列表账目使用稳定id。

## 5. 知识捕获
已更新android/ui.md与README；仓库没有src/templates/markdown/spec目录，不创建无关模板。规格与代码一起提交。
