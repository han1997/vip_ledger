package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class TxnDayGrouperTest {

    private val zone = TimeZone.of("Asia/Shanghai")

    @Test
    fun groupsByLocalDayNewestFirstAndKeepsOrderInsideADay() {
        val groups = TxnDayGrouper.group(
            listOf(
                txn("t1", TransactionType.EXPENSE, 1000, at(9, 1, 9)),
                txn("t2", TransactionType.EXPENSE, 2000, at(9, 24, 12)),
                txn("t3", TransactionType.EXPENSE, 3000, at(9, 24, 20)),
                txn("t4", TransactionType.EXPENSE, 4000, at(9, 10, 8)),
            ),
            zone,
        )

        assertEquals(
            listOf(LocalDate(2026, 9, 24), LocalDate(2026, 9, 10), LocalDate(2026, 9, 1)),
            groups.map { it.date },
        )
        assertEquals(listOf("t2", "t3"), groups.first().txns.map { it.id })
    }

    @Test
    fun dayTotalsOnlyCountTxnsThatParticipateInStats() {
        val groups = TxnDayGrouper.group(
            listOf(
                txn("t1", TransactionType.EXPENSE, 1000, at(9, 24, 9)),
                txn("t2", TransactionType.INCOME, 5000, at(9, 24, 10)),
                txn("t3", TransactionType.TRANSFER, 2000, at(9, 24, 11), toAccountId = "a2"),
                txn("t4", TransactionType.EXPENSE, 7000, at(9, 24, 12), draftStatus = DraftStatus.PENDING),
                txn("t5", TransactionType.EXPENSE, 8000, at(9, 24, 13), excludeFromStats = true),
                txn("t6", TransactionType.EXPENSE, 9000, at(9, 24, 14), deleted = true),
            ),
            zone,
        )

        val group = groups.single()
        assertEquals(Money.ofCents(1000), group.expense, "转账/草稿/排除统计/已删都不进当日合计")
        assertEquals(Money.ofCents(5000), group.income)
        assertEquals(6, group.txns.size, "列表本身仍要显示全部命中项")
    }

    @Test
    fun localDayBoundaryFollowsTheGivenZone() {
        // UTC 的 2026-09-23 17:00 在上海是 2026-09-24 01:00。
        val occurredAt = LocalDate(2026, 9, 23).startOfDayEpochMillis(TimeZone.UTC) + 17 * 3600_000L
        val groups = TxnDayGrouper.group(listOf(txn("t1", TransactionType.EXPENSE, 1000, occurredAt)), zone)

        assertEquals(LocalDate(2026, 9, 24), groups.single().date)
    }

    private fun txn(
        id: String,
        type: TransactionType,
        amountCents: Long,
        occurredAt: Long,
        toAccountId: String? = null,
        draftStatus: DraftStatus = DraftStatus.CONFIRMED,
        excludeFromStats: Boolean = false,
        deleted: Boolean = false,
    ) = Txn(
        id = id,
        ledgerId = "l1",
        type = type,
        amount = Money.ofCents(amountCents),
        accountId = "a1",
        toAccountId = toAccountId,
        occurredAt = occurredAt,
        draftStatus = draftStatus,
        excludeFromStats = excludeFromStats,
        createdAt = 0,
        updatedAt = 0,
        deletedAt = if (deleted) 1L else null,
    )

    private fun at(month: Int, day: Int, hour: Int): Long =
        LocalDate(2026, month, day).startOfDayEpochMillis(zone) + hour * 3600_000L
}