package com.han1997.vipledger.feature.sync.ui.common

import kotlinx.datetime.LocalDate

/**
 * 待导出的文件：建议文件名 + 内容 + MIME。
 *
 * 与 `feature:stats/ui/common/ExportNaming.kt` 的 `CsvExport` 是**同一套 SAF 契约**
 * 的镜像实现。之所以不搬到 `:core:designsystem`：那两个文件目前只有 stats 在用，
 * 强行下沉会同时改 stats 的调用点，超出本次范围；这里保持相同形状，
 * `feature:sync` 不依赖 `feature:stats`（见 `.trellis/spec/android/architecture.md`）。
 */
data class SyncExport(
    val fileName: String,
    val content: String,
    val mime: String,
)

/** ASCII 前缀 + 日期，避免中文文件名在部分文件管理器上乱码（import-export.md 界面侧导出）。 */
fun csvSuggestedFileName(prefix: String, date: LocalDate): String =
    "vipledger-" + prefix + "-" + date.toString().replace("-", "") + ".csv"

fun jsonSuggestedFileName(prefix: String, date: LocalDate): String =
    "vipledger-" + prefix + "-" + date.toString().replace("-", "") + ".json"
