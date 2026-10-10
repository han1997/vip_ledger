# P2 数据关联与事务复盘

## 1. 根因类别
- E 隐式假设：REPLACE并非原位更新。账本改名可能触发子表级联，已有关联的流水也需要保留身份。
- C 跨层错误传播：仓库将异常转换为Failure后，外层事务正常返回，先前成功的SQL可能提交；储蓄关联、标签替换均需覆盖中途失败。
- D 测试覆盖：纯金额计算测试不能证明数据库外键、并发及回滚行为。

## 2. 调整过程
曾尝试把TxnDao统一切到Room Upsert，既有import_hash唯一冲突测试发现行为改变：非主键冲突不能视为更新。因此保留已有新增路径，单独用UPDATE处理编辑，并把流水与标签组合写入放进DAO事务。

## 3. 预防机制
- LedgerDao事务UPDATE/INSERT，重命名及同名冲突测试断言原账户、流水和目标完整。
- 目标Upsert保留savings_link；通用改删恢复注入SavingsTxnGuard。
- MoveSavingsUseCase把失败变成异常退出外层事务；DAO writeWithTags在仓库捕获异常前回滚。
- SQLite fail_link/fail_tag触发器分别制造后半段失败，断言流水、标签、两账户余额及目标进度未变；并发取回只有一个请求可通过。
- 快照临时库额外校验目标/账户/流水同账本和储蓄非负，不只校验外键。

## 4. 系统性检查
本批所有跨表修改均追踪到Room事务边界；首页卡片全量配置替换使用事务。原有import_hash用例继续通过，不更改旧导入语义。目标账户有过关联后不能重绑（即使流水后来软删除），UI与领域层保持一致。

## 5. 知识捕获
已写入android/assets-savings.md、database.md、import-export.md及README。项目不是Trellis模板生成器，没有src/templates/markdown/spec目录，不创建无关模板。
