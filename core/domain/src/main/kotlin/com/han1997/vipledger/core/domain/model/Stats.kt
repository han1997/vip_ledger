package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import kotlinx.datetime.LocalDate

/** 时间分桶粒度。 */
enum class PeriodGranularity { DAY, WEEK, MONTH }

/**
 * 分类统计结果。
 *
 * [categoryId] 为 null 表示「未分类」；[children] 只在按一级分类汇总时填充，
 * 且父项 [total] 已包含所有子项（docs/DATA-MODEL.md 第 5 节）。
 */
data class CategoryStat(
    val categoryId: String?,
    val name: String?,
    val kind: CategoryKind,
    val total: Money,
    val count: Int,
    val children: List<CategoryStat> = emptyList(),
)

/** 标签统计：一笔账的多个标签各计一次全额，不做金额分摊。 */
data class TagStat(
    val tagId: String,
    val name: String,
    val expense: Money,
    val income: Money,
    val count: Int,
) {
    val total: Money get() = expense + income
}

/** 账户维度的流向统计。 */
data class AccountStat(
    val accountId: String,
    val expense: Money,
    val income: Money,
    val count: Int,
) {
    /** 净流入：收入为正、支出为负（转账与还款不参与，见统计口径）。 */
    val net: Money get() = income - expense
}

/** 一个时间桶内的收支。 */
data class PeriodStat(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val income: Money,
    val expense: Money,
    val count: Int,
) {
    val balance: Money get() = income - expense
}

/** 一次统计的完整结果。 */
data class StatsSummary(
    val range: DateRange,
    val income: Money,
    val expense: Money,
    val count: Int,
    val byCategory: Map<CategoryKind, List<CategoryStat>>,
    val byTag: List<TagStat>,
    val byAccount: List<AccountStat>,
) {
    val balance: Money get() = income - expense
}