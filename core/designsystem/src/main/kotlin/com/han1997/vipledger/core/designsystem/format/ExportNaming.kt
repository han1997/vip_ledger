package com.han1997.vipledger.core.designsystem.format

import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import kotlinx.datetime.LocalDate

/**
 * 导出文件命名（P7：CSV/PNG 带账本/区间/口径）。
 *
 * 原先只放「前缀 + 日期」；统计/搜索/下钻/资产趋势共用后按架构「跨 feature 复用下沉
 * designsystem」迁入。文件名形如
 * `vipledger-<prefix>-<ledger>-<range>-<yyyymmdd>.<ext>`，账本名与区间标签经过
 * 清洗（非法文件名字符与空白折叠成 `-`），空段自动省略。
 */

/** 清洗文件名片段：文件系统非法字符与空白折叠为 `-`，去首尾连字符并限长。 */
fun sanitizeFileNamePart(raw: String, maxLength: Int = 20): String =
    raw.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-").trim('-').take(maxLength)

/**
 * 导出建议文件名：[prefix] 是口径标识（stats/search/drill/trend…），
 * [ledgerName] 与 [rangeLabel] 可为空串（空段省略）。
 */
fun suggestedFileName(
    prefix: String,
    ledgerName: String,
    rangeLabel: String,
    date: LocalDate,
    extension: String = "csv",
): String {
    val parts = listOf(prefix, sanitizeFileNamePart(ledgerName), sanitizeFileNamePart(rangeLabel))
        .filter { it.isNotBlank() } + date.toString().replace("-", "")
    return parts.joinToString("-").let { "vipledger-$it.$extension" }
}

/**
 * 区间标签：自定义区间用起止日期；预设区间解析成实际日期范围后同样取起止
 * （如 `20261001-20261031`），口径里既有年月也有长度，无需按预设逐个特判。
 */
fun rangeLabelOf(preset: DateRangePreset?, custom: DateRange?, today: LocalDate): String {
    val range = when {
        custom != null -> custom
        preset != null -> preset.resolve(today)
        else -> null
    } ?: return "all"
    return range.start.toString().replace("-", "") + "-" + range.endInclusive.toString().replace("-", "")
}
