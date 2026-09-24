package com.han1997.vipledger.core.database

import androidx.room.migration.Migration

/**
 * 迁移清单（docs/DATA-MODEL.md 第 4 节）。
 *
 * 当前 `schemaVersion = 1`，还没有需要迁移的版本；改表时在这里追加 `Migration(n, n+1)`，
 * 并在 `MigrationTest` 里补一条 `runMigrationsAndValidate(n+1, 迁移...)`。
 * 禁止使用 `fallbackToDestructiveMigration`：发布版会丢用户数据。
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()