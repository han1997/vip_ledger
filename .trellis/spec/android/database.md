# 数据库（Room）

## 基本约定

- 表结构以 `docs/DATA-MODEL.md` 为准；实现与文档不一致时，先改文档再改代码。
- 主键 `id TEXT`（UUID v4 字符串）；金额 `INTEGER` 存分；时间 `INTEGER` 存 UTC epoch millis。
- 枚举以 `TEXT` 存枚举名，配 `TypeConverter`；禁止存序号。
- `*_cents` 恒为正数，方向由 `type` 决定；查询默认过滤 `deleted_at IS NULL`。
- 所有业务查询必须带 `ledger_id`，账本隔离不能靠调用方自觉。

## 实体与 DAO

- 实体后缀 `Entity`，DAO 后缀 `Dao`；查询返回 `Flow`（读）或 `suspend`（写）。
- 复杂读模型（统计聚合、搜索结果）用独立的 `@Query` + 投影 data class，禁止在 UI 层做聚合。
- 多表写入必须 `withTransaction`：流水、账户余额缓存、标签关联要在同一事务里。

## 迁移

- `exportSchema = true`，schema JSON 提交到 `core/database/schemas/`。
- 每次改表：升 `version`、写 `Migration(n, n+1)`、加 `MigrationTestHelper` 测试
  （`createDatabase(1)` 后 `runMigrationsAndValidate`）。
- 禁止 `fallbackToDestructiveMigration`（发布版会丢数据）。
- 重命名列走「建新表 → 拷数据 → 删旧表」流程，不要指望 SQLite 的 ALTER RENAME。
- 索引按 `docs/DATA-MODEL.md` 第 3 节建；新增查询前先确认是否命中已有索引。

## 测试

- DAO 测试放 `core/database/src/test`（Robolectric 或 in-memory Room）。
- 迁移测试单独一个类 `MigrationTest`，覆盖 1→2、2→3 这类链式升级。
- 余额一致性用「重算余额」用例断言：写入若干流水后 `current_balance_cents` 等于期初加减流水。
