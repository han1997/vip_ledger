package com.han1997.vipledger.feature.stats.ui.common

/**
 * 待导出的文件：建议文件名 + 内容。
 *
 * 搜索页与下钻页共用；导出走 SAF `CreateDocument`，写完文件由界面回传成功/失败。
 * 文件名规则见 `core.designsystem.format.suggestedFileName`（带账本/区间/口径）。
 */
data class CsvExport(
    val fileName: String,
    val csv: String,
)
