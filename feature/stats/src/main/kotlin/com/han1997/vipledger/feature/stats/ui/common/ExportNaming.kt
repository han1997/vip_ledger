package com.han1997.vipledger.feature.stats.ui.common

import kotlinx.datetime.LocalDate

/**
 * 待导出的文件：建议文件名 + 内容。
 *
 * 搜索页与下钻页共用；导出走 SAF `CreateDocument`，写完文件由界面回传成功/失败。
 */
data class CsvExport(
    val fileName: String,
    val csv: String,
)

/**
 * SAF（`CreateDocument`）建议的文件名。
 *
 * 用 ASCII 前缀 + 日期，避免部分文件管理器对中文文件名的兼容问题；
 * 带 BOM 的 CSV 由 `BuildTxnCsvUseCase` 产出，这里只管名字。
 */
fun csvSuggestedFileName(prefix: String, date: LocalDate): String =
    "vipledger-" + prefix + "-" + date.toString().replace("-", "") + ".csv"

fun imageSuggestedFileName(prefix: String, date: LocalDate): String =
    "vipledger-" + prefix + "-" + date.toString().replace("-", "") + ".png"