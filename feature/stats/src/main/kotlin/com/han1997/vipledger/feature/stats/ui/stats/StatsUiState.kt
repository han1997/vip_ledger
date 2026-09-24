package com.han1997.vipledger.feature.stats.ui.stats

import com.han1997.vipledger.core.domain.model.CategoryStat
import com.han1997.vipledger.core.domain.model.PeriodGranularity
import com.han1997.vipledger.core.domain.model.PeriodStat
import com.han1997.vipledger.core.domain.model.StatsReport
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.feature.stats.ui.common.CsvExport
import kotlinx.datetime.LocalDate

/** 统计页的分组维度。 */
enum class StatsDimension { CATEGORY, TAG, ACCOUNT, TIME }

/**
 * 占比环图与排行里的一行。
 *
 * [cents] 是当前口径（收入或支出）下的金额，环图按它算占比；
 * 三个 id 用来生成下钻路由，为 null 表示这一行不支持下钻（如「未分类」）。
 */
data class StatsRow(
    val label: String?,
    val cents: Long,
    val expense: Money,
    val income: Money,
    val count: Int,
    val categoryId: String? = null,
    val tagId: String? = null,
    val accountId: String? = null,
    val children: List<StatsRow> = emptyList(),
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val ledgerId: String = "",
    val ledgerName: String = "",
    val preset: DateRangePreset? = DateRangePreset.THIS_MONTH,
    val customRange: DateRange? = null,
    val range: DateRange? = null,
    val today: LocalDate = LocalDate(1970, 1, 1),
    val granularity: PeriodGranularity = PeriodGranularity.DAY,
    val dimension: StatsDimension = StatsDimension.CATEGORY,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val income: Money = Money.ZERO,
    val expense: Money = Money.ZERO,
    val count: Int = 0,
    val rows: List<StatsRow> = emptyList(),
    val periods: List<PeriodStat> = emptyList(),
    val notice: StatsNotice? = null,
    val pendingExport: CsvExport? = null,
    /** 一次性图片导出请求：界面把统计卡片截成 PNG 后写盘。 */
    val pendingImageExport: String? = null,
) {
    val balance: Money get() = income - expense

    val isEmpty: Boolean get() = count == 0

    /** 当前口径的收入/支出合计，环图中心与排行都用它。 */
    val kindTotal: Money
        get() = if (kind == CategoryKind.EXPENSE) expense else income
}

enum class StatsNotice { EXPORTED, EXPORT_FAILED }

sealed interface StatsAction {
    data class SelectPreset(val preset: DateRangePreset) : StatsAction

    data class SelectCustomRange(val range: DateRange) : StatsAction

    data class SelectGranularity(val granularity: PeriodGranularity) : StatsAction

    data class SelectDimension(val dimension: StatsDimension) : StatsAction

    data class SelectKind(val kind: CategoryKind) : StatsAction

    data object ExportCsv : StatsAction

    data object ExportImage : StatsAction

    data class ExportFinished(val success: Boolean) : StatsAction

    data object ConsumeExport : StatsAction

    data object ConsumeImageExport : StatsAction

    data object ConsumeNotice : StatsAction
}

/**
 * 报表 → 排行行。
 *
 * 纯函数（只有「账户名」需要外部查表传入），放在这里而不是 Screen 里，
 * 这样「环图占比之和 = 100%」「点一行下钻的条数与图上笔数一致」这类规则才能被单测钉住。
 */
fun statsRowsOf(
    report: StatsReport,
    dimension: StatsDimension,
    kind: CategoryKind,
    accountNames: Map<String, String> = emptyMap(),
): List<StatsRow> = when (dimension) {
    StatsDimension.CATEGORY -> report.summary.byCategory[kind].orEmpty().map { it.toRow(kind) }

    StatsDimension.TAG -> report.summary.byTag.map { tag ->
        StatsRow(
            label = tag.name,
            cents = amountCentsOf(kind, tag.expense, tag.income),
            expense = tag.expense,
            income = tag.income,
            count = tag.count,
            tagId = tag.tagId,
        )
    }

    StatsDimension.ACCOUNT -> report.summary.byAccount.map { account ->
        StatsRow(
            label = accountNames[account.accountId],
            cents = amountCentsOf(kind, account.expense, account.income),
            expense = account.expense,
            income = account.income,
            count = account.count,
            accountId = account.accountId,
        )
    }

    StatsDimension.TIME -> emptyList()
}

private fun CategoryStat.toRow(kind: CategoryKind): StatsRow = StatsRow(
    label = name,
    cents = total.cents,
    expense = if (kind == CategoryKind.EXPENSE) total else Money.ZERO,
    income = if (kind == CategoryKind.INCOME) total else Money.ZERO,
    count = count,
    categoryId = categoryId,
    children = children.map { it.toRow(kind) },
)

private fun amountCentsOf(kind: CategoryKind, expense: Money, income: Money): Long =
    if (kind == CategoryKind.EXPENSE) expense.cents else income.cents