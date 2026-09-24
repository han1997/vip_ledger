package com.han1997.vipledger.feature.stats.ui.common

import com.han1997.vipledger.core.domain.model.SearchQuery
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import kotlinx.datetime.LocalDate

/**
 * 搜索页与统计页共用的条件状态（界面直接绑定它，`onAction` 里整体替换）。
 *
 * 时间条件是「预设或自定义」二选一（与 [SearchQuery] 一致）；
 * 金额区间存的是输入框文本，[toQuery] 时才解析——半截输入（如 `1.`）不会让界面崩，
 * 也不会把非法区间塞进查询（`min > max` 时整个区间条件不生效）。
 */
data class TxnFilterState(
    val keyword: String = "",
    val preset: DateRangePreset? = null,
    val customRange: DateRange? = null,
    val categoryIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val accountIds: Set<String> = emptySet(),
    val types: Set<TransactionType> = emptySet(),
    val minAmountText: String = "",
    val maxAmountText: String = "",
    val includeDrafts: Boolean = false,
) {

    /** 除账本外是否还有生效条件（给「清空条件」与空态文案用）。 */
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
            includeDrafts

    val minAmount: Money? get() = parseAmount(minAmountText)

    val maxAmount: Money? get() = parseAmount(maxAmountText)

    /** 两边都填了且最小 > 最大时为 false，界面据此提示而不是静默返回空结果。 */
    val isAmountRangeValid: Boolean
        get() {
            val min = minAmount ?: return true
            val max = maxAmount ?: return true
            return min <= max
        }

    /** 界面当前应当展示的时间区间文案（自定义优先展示自己的闭区间）。 */
    fun resolvedRange(today: LocalDate): DateRange? = when {
        customRange != null -> customRange
        preset != null -> preset.resolve(today)
        else -> null
    }

    fun selectPreset(value: DateRangePreset?): TxnFilterState = copy(preset = value, customRange = null)

    fun selectCustomRange(range: DateRange): TxnFilterState = copy(preset = null, customRange = range)

    fun toggleCategory(id: String): TxnFilterState = copy(categoryIds = categoryIds.toggled(id))

    fun toggleTag(id: String): TxnFilterState = copy(tagIds = tagIds.toggled(id))

    fun toggleAccount(id: String): TxnFilterState = copy(accountIds = accountIds.toggled(id))

    fun toggleType(type: TransactionType): TxnFilterState = copy(types = types.toggled(type))

    /** 勾上「包含待确认草稿」= 不限草稿状态；不勾表示只看已确认。 */
    fun toQuery(ledgerId: String): SearchQuery {
        val amountsApply = isAmountRangeValid
        return SearchQuery(
            ledgerId = ledgerId,
            keyword = keyword.trim(),
            preset = preset,
            customRange = customRange,
            categoryIds = categoryIds,
            tagIds = tagIds,
            accountIds = accountIds,
            types = types,
            minAmount = if (amountsApply) minAmount else null,
            maxAmount = if (amountsApply) maxAmount else null,
            draftStatus = if (includeDrafts) null else DraftStatus.CONFIRMED,
        )
    }

    private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value

    private fun parseAmount(text: String): Money? = Money.parse(text)?.abs()?.takeIf { it.isPositive }

    companion object {
        /** 首页/统计页默认落在「本月」，搜索页默认不限时间。 */
        val THIS_MONTH: TxnFilterState = TxnFilterState(preset = DateRangePreset.THIS_MONTH)
    }
}