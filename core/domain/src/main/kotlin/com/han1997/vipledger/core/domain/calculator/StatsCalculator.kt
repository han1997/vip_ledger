package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.AccountStat
import com.han1997.vipledger.core.domain.model.CategoryStat
import com.han1997.vipledger.core.domain.model.PeriodGranularity
import com.han1997.vipledger.core.domain.model.PeriodStat
import com.han1997.vipledger.core.domain.model.StatsSummary
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.domain.model.TagStat
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.minus

/**
 * 统计口径（docs/DATA-MODEL.md 第 5 节）。
 *
 * - 只有已确认、未删除、未排除、类型为收支的账目参与；
 * - 转账 / 还款 / 调整影响余额但不进收支；
 * - 时间归属按 [TimeZone] 把 `occurred_at` 折到「某一天」再比对区间；
 * - 分类统计默认汇总到一级分类（[CategoryStat.children] 是二级明细）；
 * - 标签统计一笔账的多标签各计一次全额。
 */
object StatsCalculator {

    fun isCounted(txn: Txn): Boolean =
        !txn.isDeleted &&
            txn.draftStatus == DraftStatus.CONFIRMED &&
            !txn.excludeFromStats &&
            txn.type.isAccountingForStats

    fun countedIn(txns: List<Txn>, range: DateRange, zone: TimeZone): List<Txn> =
        txns.filter { isCounted(it) && it.occurredAt.toLocalDate(zone) in range }

    fun summarize(
        txns: List<Txn>,
        categories: List<Category>,
        tags: List<Tag>,
        range: DateRange,
        zone: TimeZone,
    ): StatsSummary {
        val counted = countedIn(txns, range, zone)
        return StatsSummary(
            range = range,
            income = sumOfType(counted, TransactionType.INCOME),
            expense = sumOfType(counted, TransactionType.EXPENSE),
            count = counted.size,
            byCategory = byCategory(counted, categories),
            byTag = byTag(counted, tags),
            byAccount = byAccount(counted),
        )
    }

    fun byCategory(txns: List<Txn>, categories: List<Category>): Map<CategoryKind, List<CategoryStat>> {
        val counted = txns.filter(::isCounted)
        val byId = categories.associateBy { it.id }
        val result = LinkedHashMap<CategoryKind, List<CategoryStat>>()
        for (kind in CategoryKind.entries) {
            val ofKind = counted.filter { kindOf(it.type) == kind }
            if (ofKind.isEmpty()) continue
            result[kind] = ofKind.groupBy { topLevelIdOf(it.categoryId, byId) }
                .map { (topId, list) ->
                    val children = list.filter { it.categoryId != topId }
                        .groupBy { it.categoryId }
                        .map { (childId, childList) -> statOf(childId, byId[childId]?.name, kind, childList) }
                        .sortedByDescending { it.total }
                    statOf(topId, byId[topId]?.name, kind, list).copy(children = children)
                }
                .sortedByDescending { it.total }
        }
        return result
    }

    fun byTag(txns: List<Txn>, tags: List<Tag>): List<TagStat> {
        val names = tags.associate { it.id to it.name }
        val buckets = LinkedHashMap<String, MutableList<Txn>>()
        txns.filter(::isCounted).forEach { txn ->
            txn.tagIds.distinct().forEach { tagId -> buckets.getOrPut(tagId) { mutableListOf() }.add(txn) }
        }
        return buckets.map { (tagId, list) ->
            TagStat(
                tagId = tagId,
                name = names[tagId] ?: tagId,
                expense = sumOfType(list, TransactionType.EXPENSE),
                income = sumOfType(list, TransactionType.INCOME),
                count = list.size,
            )
        }.sortedByDescending { it.total }
    }

    fun byAccount(txns: List<Txn>): List<AccountStat> =
        txns.filter(::isCounted).groupBy { it.accountId }.map { (accountId, list) ->
            AccountStat(
                accountId = accountId,
                expense = sumOfType(list, TransactionType.EXPENSE),
                income = sumOfType(list, TransactionType.INCOME),
                count = list.size,
            )
        }.sortedByDescending { it.net }

    /** 时间分桶；即使某桶没有账目也会返回，便于图表连线。 */
    fun byPeriod(
        txns: List<Txn>,
        granularity: PeriodGranularity,
        range: DateRange,
        zone: TimeZone,
    ): List<PeriodStat> {
        val buckets = bucketsOf(granularity, range)
        if (buckets.isEmpty()) return emptyList()
        val income = LongArray(buckets.size)
        val expense = LongArray(buckets.size)
        val counts = IntArray(buckets.size)
        countedIn(txns, range, zone).forEach { txn ->
            val index = bucketIndexOf(granularity, buckets, txn.occurredAt.toLocalDate(zone))
            if (index == null || !buckets[index].contains(txn.occurredAt.toLocalDate(zone))) return@forEach
            when (txn.type) {
                TransactionType.INCOME -> income[index] += txn.amount.cents
                TransactionType.EXPENSE -> expense[index] += txn.amount.cents
                TransactionType.REFUND -> expense[index] -= txn.amount.cents
                else -> Unit
            }
            counts[index]++
        }
        return buckets.mapIndexed { index, bucket ->
            PeriodStat(
                start = bucket.start,
                endInclusive = bucket.endInclusive,
                income = Money.ofCents(income[index]),
                expense = Money.ofCents(expense[index]),
                count = counts[index],
            )
        }
    }

    private fun statOf(categoryId: String?, name: String?, kind: CategoryKind, list: List<Txn>): CategoryStat =
        CategoryStat(
            categoryId = categoryId,
            name = name,
            kind = kind,
            total = Money.sum(list.map { if (it.type == TransactionType.REFUND) -it.amount else it.amount }),
            count = list.size,
        )

    private fun kindOf(type: TransactionType): CategoryKind = when (type) {
        TransactionType.INCOME -> CategoryKind.INCOME
        else -> CategoryKind.EXPENSE
    }

    private fun topLevelIdOf(categoryId: String?, byId: Map<String, Category>): String? {
        if (categoryId == null) return null
        val category = byId[categoryId] ?: return categoryId
        val parentId = category.parentId ?: return category.id
        return if (byId.containsKey(parentId)) parentId else category.id
    }

    fun sumOfType(txns: List<Txn>, type: TransactionType): Money =
        Money.sum(txns.mapNotNull {
            when {
                it.type == type -> it.amount
                type == TransactionType.EXPENSE && it.type == TransactionType.REFUND -> -it.amount
                else -> null
            }
        })

    private fun bucketsOf(granularity: PeriodGranularity, range: DateRange): List<DateRange> = when (granularity) {
        PeriodGranularity.DAY -> generateSequence(range.start) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= range.endInclusive }
            .map { DateRange(it, it) }
            .toList()
        PeriodGranularity.WEEK -> {
            val result = mutableListOf<DateRange>()
            var start = DateRangePreset.startOfWeek(range.start)
            while (start <= range.endInclusive) {
                result += DateRange(maxOf(start, range.start), minOf(start.plus(6, DateTimeUnit.DAY), range.endInclusive))
                start = start.plus(7, DateTimeUnit.DAY)
            }
            result
        }
        PeriodGranularity.MONTH -> {
            val result = mutableListOf<DateRange>()
            var start = DateRangePreset.startOfMonth(range.start)
            while (start <= range.endInclusive) {
                val end = start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
                result += DateRange(maxOf(start, range.start), minOf(end, range.endInclusive))
                start = start.plus(1, DateTimeUnit.MONTH)
            }
            result
        }
    }

    private fun bucketIndexOf(granularity: PeriodGranularity, buckets: List<DateRange>, date: LocalDate): Int? {
        val first = buckets.first().start
        val index = when (granularity) {
            PeriodGranularity.DAY -> (date.toEpochDays() - first.toEpochDays()).toInt()
            PeriodGranularity.WEEK -> ((date.toEpochDays() - first.toEpochDays()) / 7).toInt()
            PeriodGranularity.MONTH ->
                ((date.year * 12 + date.monthNumber) - (first.year * 12 + first.monthNumber))
        }
        return index.takeIf { it in buckets.indices }
    }
}