package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.AssetBaseline
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.util.TreeMap

/**
 * 资产/负债/净资产历史趋势（P7）：从基线快照重放账目，逐日现算，不落新表。
 *
 * 口径与资产页一致（`BalanceCalculator.deltaOf` + 同一纳入集合
 * `includeInNetWorth && isUsable`）：正余额计入资产、负余额绝对值计入负债。
 *
 * **与资产页数字一致的守恒式**（验收硬约束）：曲线终点 == 资产页余额
 * （`initialBalance + Σ 全部有效账目净额`），因此：
 * - 有效基线 = 快照余额 + **基准修正**：发生日在基准日（含）之前、且基线采集之后才
   写入或改动的账目（`createdAt/updatedAt > capturedAt`）的净影响；
 * - 重放：发生日在基准日之后的账目（`BalanceCalculator.deltaOf`，只计已确认）。
 * 未删除但非已确认（草稿）与已删除的账目不参与；发生日在基准日当天（含）的账目视为
 * 已含在快照内（快照是当日实况），只有事后补记/改动才修正基线。
 *
 * 账户在自身基线建立之前不参与当日曲线（无锚点）；曲线起点 = max(区间起点, 最早基线日)。
 */
object AssetHistoryCalculator {

    /** 一天的资产口径（单位：分）。[debtCents] 恒为非负（负债绝对值）。 */
    data class Point(
        val date: LocalDate,
        val assetsCents: Long,
        val debtCents: Long,
        val netWorthCents: Long,
    )

    fun history(
        accounts: List<Account>,
        baselines: List<AssetBaseline>,
        txns: List<Txn>,
        range: DateRange,
        zone: TimeZone,
    ): List<Point> {
        // 每个纳入账户取最新一条基线（P2：已有记录不覆盖，防御性取 max capturedAt）。
        val series = accounts.asSequence()
            .filter { it.includeInNetWorth && it.isUsable }
            .mapNotNull { account ->
                baselines.filter { it.accountId == account.id && it.ledgerId == account.ledgerId }
                    .maxByOrNull { it.capturedAt }
                    ?.let { baseline -> account to baseline }
            }
            .toList()
        if (series.isEmpty()) return emptyList()

        val replay = series.map { (account, baseline) ->
            val baselineDay = baseline.capturedAt.toLocalDate(zone).toEpochDays()
            var effectiveBaselineCents = baseline.balance.cents
            val deltasByDay = TreeMap<Long, Long>()
            for (txn in txns) {
                val delta = BalanceCalculator.deltaOf(txn, account.id) ?: continue
                val txnDay = txn.occurredAt.toLocalDate(zone).toEpochDays()
                if (txnDay <= baselineDay) {
                    // 基准修正：基线采集之后才写入或改动的基准日前账目。
                    if (txn.createdAt > baseline.capturedAt || txn.updatedAt > baseline.capturedAt) {
                        effectiveBaselineCents += delta.cents
                    }
                } else {
                    deltasByDay.merge(txnDay, delta.cents, Long::plus)
                }
            }
            val cumulative = TreeMap<Long, Long>()
            var running = 0L
            for ((day, delta) in deltasByDay) {
                running += delta
                cumulative[day] = running
            }
            Replay(account.id, baselineDay, effectiveBaselineCents, cumulative)
        }

        val firstDay = maxOf(range.start.toEpochDays(), replay.minOf { it.baselineDay })
        val lastDay = range.endInclusive.toEpochDays()
        if (firstDay > lastDay) return emptyList()

        return (firstDay..lastDay).map { day ->
            var assets = 0L
            var debt = 0L
            for (item in replay) {
                if (day < item.baselineDay) continue
                val balance = item.effectiveBaselineCents + (item.cumulative.floorEntry(day)?.value ?: 0L)
                if (balance >= 0) assets += balance else debt += -balance
            }
            Point(
                date = LocalDate.fromEpochDays(day),
                assetsCents = assets,
                debtCents = debt,
                netWorthCents = assets - debt,
            )
        }
    }

    /** 单账户的重放序列：有效基线 + 基线之后逐日累计净额（key = epochDay）。 */
    private data class Replay(
        val accountId: String,
        val baselineDay: Long,
        val effectiveBaselineCents: Long,
        val cumulative: TreeMap<Long, Long>,
    )

    /** 供界面把某日的账户余额拆出来核对（测试与走查用），非曲线主路径。 */
    fun balanceOn(
        account: Account,
        baseline: AssetBaseline,
        txns: List<Txn>,
        day: LocalDate,
        zone: TimeZone,
    ): Money {
        val baselineDay = baseline.capturedAt.toLocalDate(zone).toEpochDays()
        val targetDay = day.toEpochDays()
        var cents = baseline.balance.cents
        for (txn in txns) {
            val delta = BalanceCalculator.deltaOf(txn, account.id) ?: continue
            val txnDay = txn.occurredAt.toLocalDate(zone).toEpochDays()
            if (txnDay <= baselineDay) {
                if (txn.createdAt > baseline.capturedAt || txn.updatedAt > baseline.capturedAt) cents += delta.cents
            } else if (txnDay <= targetDay) {
                cents += delta.cents
            }
        }
        return Money.ofCents(cents)
    }
}