package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import kotlinx.datetime.LocalDate

/**
 * 组合搜索条件（docs/DATA-MODEL.md 3.5）。
 *
 * 时间段二选一：[preset] 优先于 [customRange]；两者都为 null 表示不限时间。
 * 集合类条件为空 = 该维度不过滤；[types] 为空 = 全部类型。
 */
data class SearchQuery(
    val ledgerId: String,
    val keyword: String = "",
    val preset: DateRangePreset? = null,
    val customRange: DateRange? = null,
    val categoryIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val accountIds: Set<String> = emptySet(),
    val types: Set<TransactionType> = emptySet(),
    val minAmount: Money? = null,
    val maxAmount: Money? = null,
    val draftStatus: DraftStatus? = null,
) {
    /** 除账本外是否还有生效条件（给「清空条件」按钮与空态文案用）。 */
    val hasConditions: Boolean
        get() = keyword.isNotBlank() ||
            preset != null ||
            customRange != null ||
            categoryIds.isNotEmpty() ||
            tagIds.isNotEmpty() ||
            accountIds.isNotEmpty() ||
            types.isNotEmpty() ||
            minAmount != null ||
            maxAmount != null ||
            draftStatus != null

    /** 解析成闭区间；不限时间时返回 null。 */
    fun resolveRange(today: LocalDate): DateRange? = when {
        preset != null -> preset.resolve(today)
        customRange != null -> customRange
        else -> null
    }
}