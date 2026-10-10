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
- P2：LedgerDao.upsert采用事务内UPDATE、主键不存在才INSERT；目标用原位Upsert；已有账目用UPDATE。不要将这些路径改回REPLACE，否则会影响子表关联。TxnDao.writeWithTags单独保证流水和标签替换原子性，仓库捕获SQL异常返回Failure时内部更新必须已经回滚。

## 实现约定（M1 落地时定下的）

- 集合型过滤条件不要用 `IN (:list)`：Room 对空列表会生成非法的 `IN ()`。
  统一传 `,id1,id2,` 形式的 CSV，SQL 里用 `INSTR(:csv, ',' || col || ',') > 0` 判断；
  空集合 → 传 null 表示不过滤。参考 `TxnDao.TXN_FILTER_SQL` 与 `TxnFilterSql`。
- 需要分页而不限条数时，`LIMIT :limit` 传 -1（SQLite 的「不限制」写法）。
- 流水排序必须带齐兜底键：列表 `ORDER BY occurred_at DESC, created_at DESC, id DESC`，
  账户明细用 ASC。只按 `occurred_at` 排时，同秒/同毫秒的多笔顺序由 SQLite 自行决定，
  列表刷新会换位；回归见 `LedgerDatabaseTest.orderingIsStableWhenTxnsShareTheSameOccurredAt`。
- 读模型用 `@Relation` + `@Transaction`（如 `TxnWithTagIds`），不要在 UI 层拼装。
- 实体只存 `Long/Int/String/Boolean`，`Money` 与 `LocalDate` 的换算留在 `:core:data` 的 mapper 里。

## 迁移

- `exportSchema = true`，schema JSON 提交到 `core/database/schemas/`。
- 每次改表：升 `version`、写 `Migration(n, n+1)`、加 `MigrationTestHelper` 测试
  （`createDatabase(1)` 后 `runMigrationsAndValidate`）。
- 禁止 `fallbackToDestructiveMigration`（发布版会丢数据）。
- 重命名列走「建新表 → 拷数据 → 删旧表」流程，不要指望 SQLite 的 ALTER RENAME。
- 索引按 `docs/DATA-MODEL.md` 第 3 节建；新增查询前先确认是否命中已有索引。

## 测试

- DAO 测试放 `core/database/src/test`（Robolectric + in-memory Room，`robolectric.properties` 固定 `sdk=34`）。
- 迁移测试单独一个类 `MigrationTest`，覆盖 1→2、2→3 这类链式升级。
- 没有迁移可测的版本也要守住两条不变式（M1 起已生效）：
  导出 schema 的身份哈希必须等于运行时库 `room_master_table.identity_hash`（改表不升版本会红）；
  `ALL_MIGRATIONS` 必须覆盖 1..schemaVersion 的每个缺口。
- Room 2.8 的 `MigrationTestHelper` 在 Robolectric 下走 SupportSQLite 驱动会报 driver/db-name 不匹配，
  真有迁移可测时改用基于 `SQLiteConnection` 的 API（见 docs/DEV-NOTES.md 第 7 节）。
- 余额一致性用「重算余额」用例断言：写入若干流水后 `current_balance_cents` 等于期初加减流水。
