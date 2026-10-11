package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.AssetBaseline
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 资产/负债/净资产历史（P7）的纯计算测试。
 *
 * 覆盖：基线重放、跨账户转账、基准修正（基准日前补记/改动）、草稿与已删除不参与、
 * 无基线账户排除、资产/负债拆分、曲线起点裁剪与终点守恒（== 资产页口径）。
 */
class AssetHistoryCalculatorTest {

    private val zone = TimeZone.UTC

    // 固定日历：2026-10-01 起的连续日期（UTC）。
    private val d1 = LocalDate(2026, 10, 1)
    private val d2 = LocalDate(2026, 10, 2)
    private val d3 = LocalDate(2026, 10, 3)
    private val d4 = LocalDate(2026, 10, 4)
    private val millis: (LocalDate) -> Long = { it.toEpochDays() * 24 * 60 * 60 * 1000L }

    private fun account(id: String, include: Boolean = true, archived: Boolean = false) = Account(
        id = id, ledgerId = "l", name = id, type = com.han1997.vipledger.core.model.AccountType.VIRTUAL,
        createdAt = 0L, updatedAt = 0L,
        includeInNetWorth = include, isArchived = archived,
    )

    private fun baseline(accountId: String, capturedAt: Long, cents: Long) =
        AssetBaseline(accountId, "l", capturedAt, Money.ofCents(cents), included = true)

    /** 已确认支出：[at] 当天从 [accountId] 付 [cents] 分。 */
    private fun expense(id: String, accountId: String, cents: Long, at: Long, createdAt: Long = at) = Txn(
        id = id, ledgerId = "l", type = TransactionType.EXPENSE, amount = Money.ofCents(cents),
        accountId = accountId, occurredAt = at, title = id, createdAt = createdAt, updatedAt = createdAt,
    )

    private fun transfer(id: String, from: String, to: String, cents: Long, at: Long, createdAt: Long = at) = Txn(
        id = id, ledgerId = "l", type = TransactionType.TRANSFER, amount = Money.ofCents(cents),
        accountId = from, toAccountId = to, occurredAt = at, title = id, createdAt = createdAt, updatedAt = createdAt,
    )

    @Test
    fun replaysFromBaselineWithDailySeries() {
        val wallet = account("wallet")
        val history = AssetHistoryCalculator.history(
            accounts = listOf(wallet),
            baselines = listOf(baseline("wallet", millis(d1), 100_00)),
            txns = listOf(expense("e1", "wallet", 30_00, millis(d2))),
            range = DateRange(d1, d3),
            zone = zone,
        )
        assertEquals(3, history.size)
        assertEquals(100_00L, history[0].assetsCents)
        assertEquals(70_00L, history[1].assetsCents)
        assertEquals(70_00L, history[2].assetsCents, "之后无账目则维持")
        assertEquals(0L, history[1].debtCents)
        assertEquals(70_00L, history[1].netWorthCents)
    }

    @Test
    fun transfersAffectBothAccountsAndSplitsAssetsAndDebt() {
        val wallet = account("wallet")
        val credit = account("credit")
        val history = AssetHistoryCalculator.history(
            accounts = listOf(wallet, credit),
            baselines = listOf(
                baseline("wallet", millis(d1), 10_000L),  // 100 元
                baseline("credit", millis(d1), -5_000L),  // 欠 50 元
            ),
            txns = listOf(transfer("t1", "wallet", "credit", 3_000L, millis(d2))),
            range = DateRange(d1, d2),
            zone = zone,
        )
        // d1：资产 10000，负债 5000，净资产 5000。
        assertEquals(10_000L, history[0].assetsCents)
        assertEquals(5_000L, history[0].debtCents)
        assertEquals(5_000L, history[0].netWorthCents)
        // d2：钱包 7000，信用 -2000 → 资产 7000，负债 2000，净资产 5000。
        assertEquals(7_000L, history[1].assetsCents)
        assertEquals(2_000L, history[1].debtCents)
        assertEquals(5_000L, history[1].netWorthCents)
    }

    @Test
    fun backfilledPreBaselineTxnCorrectsTheBaseline() {
        val wallet = account("wallet")
        // 基线 d1 快照 10000；d3 才补记一笔基准日当天（d1）的支出 3000（createdAt 在基线之后）。
        val backfill = expense("late", "wallet", 3_000L, millis(d1), createdAt = millis(d3))
        val history = AssetHistoryCalculator.history(
            accounts = listOf(wallet),
            baselines = listOf(baseline("wallet", millis(d1), 10_000L)),
            txns = listOf(backfill),
            range = DateRange(d1, d3),
            zone = zone,
        )
        // 基准修正：基线前账目不重放，而是把净影响计入基线，曲线整体下移。
        assertEquals(7_000L, history[0].assetsCents)
        assertEquals(7_000L, history[2].assetsCents)
    }

    @Test
    fun preBaselineTxnCapturedBeforeBaselineDoesNotAlterIt() {
        val wallet = account("wallet")
        // 基线采集之前就已存在的账目：已含在快照内，不修正、不重放。
        val early = expense("early", "wallet", 30_00, millis(d1), createdAt = 0L)
        val history = AssetHistoryCalculator.history(
            accounts = listOf(wallet),
            baselines = listOf(baseline("wallet", millis(d2), 70_00)),
            txns = listOf(early),
            range = DateRange(d1, d3),
            zone = zone,
        )
        // 曲线从基线日（d2）开始：d1 被裁剪。
        assertEquals(2, history.size)
        assertEquals(d2, history[0].date)
        assertEquals(70_00L, history[0].assetsCents)
    }

    @Test
    fun editedPreBaselineTxnCountsAsCorrection() {
        val wallet = account("wallet")
        // 基线前账目在基线之后被改金额（updatedAt 晚于基线）→ 修正基线。
        val edited = expense("edited", "wallet", 30_00, millis(d1), createdAt = 0L).copy(updatedAt = millis(d3))
        val history = AssetHistoryCalculator.history(
            accounts = listOf(wallet),
            baselines = listOf(baseline("wallet", millis(d2), 100_00)),
            txns = listOf(edited),
            range = DateRange(d2, d3),
            zone = zone,
        )
        assertEquals(70_00L, history[0].assetsCents)
    }

    @Test
    fun draftsAndDeletedTxnsNeverAffectHistory() {
        val wallet = account("wallet")
        val draft = expense("draft", "wallet", 30_00, millis(d2)).copy(draftStatus = DraftStatus.PENDING)
        val deleted = expense("deleted", "wallet", 30_00, millis(d2)).copy(deletedAt = millis(d3))
        val history = AssetHistoryCalculator.history(
            accounts = listOf(wallet),
            baselines = listOf(baseline("wallet", millis(d1), 100_00)),
            txns = listOf(draft, deleted),
            range = DateRange(d1, d2),
            zone = zone,
        )
        assertEquals(100_00L, history[1].assetsCents)
    }

    @Test
    fun accountWithoutBaselineIsExcludedAndExcludedAccountsToo() {
        val baselined = account("a1")
        val noBaseline = account("a2")
        val excluded = account("a3", include = false)
        val history = AssetHistoryCalculator.history(
            accounts = listOf(baselined, noBaseline, excluded),
            baselines = listOf(baseline("a1", millis(d1), 100_00), baseline("a3", millis(d1), 500_00)),
            txns = listOf(transfer("t", "a1", "a2", 40_00, millis(d2))),
            range = DateRange(d1, d2),
            zone = zone,
        )
        // a2 无基线不参与；a3 被排除；t1 只重放 a1 侧（a2 无锚点）。
        assertEquals(60_00L, history[1].assetsCents)
    }

    @Test
    fun rangeBeforeFirstBaselineYieldsEmptyHistory() {
        val wallet = account("wallet")
        val history = AssetHistoryCalculator.history(
            accounts = listOf(wallet),
            baselines = listOf(baseline("wallet", millis(d3), 100_00)),
            txns = emptyList(),
            range = DateRange(d1, d2),
            zone = zone,
        )
        assertTrue(history.isEmpty())
    }

    @Test
    fun noBaselinesAtAllYieldsEmptyHistory() {
        val wallet = account("wallet")
        val history = AssetHistoryCalculator.history(
            accounts = listOf(wallet),
            baselines = emptyList(),
            txns = listOf(expense("e", "wallet", 30_00, millis(d2))),
            range = DateRange(d1, d3),
            zone = zone,
        )
        assertTrue(history.isEmpty())
    }

    @Test
    fun endpointMatchesAssetsPageBalanceConservatively() {
        // 守恒式：曲线终点 == 资产页口径（initialBalance + 全部有效账目净额）。
        // 夹具必须自洽：基线快照 = 期初 + 基线采集时已存在的账目净额。
        val wallet = account("wallet").copy(initialBalance = Money.ofCents(10_000L))
        val baseline = baseline("wallet", millis(d1), 8_000L) // 10000 - 2000（基线前已存在的支出）
        val txns = listOf(
            expense("pre", "wallet", 2_000L, millis(d1), createdAt = 0L),          // 基线前已存在 → 已含在快照内
            expense("backfill", "wallet", 500L, millis(d1), createdAt = millis(d3)), // 基线后补记 → 修正基线
            expense("post", "wallet", 3_000L, millis(d3)),                          // 基线后发生 → 重放
        )
        val history = AssetHistoryCalculator.history(
            accounts = listOf(wallet),
            baselines = listOf(baseline),
            txns = txns,
            range = DateRange(d1, d3),
            zone = zone,
        )
        // 资产页：10000 - 2000 - 500 - 3000 = 4500；曲线：有效基线 7500 - 3000 = 4500。
        assertEquals(4_500L, history.last().assetsCents)
    }
}