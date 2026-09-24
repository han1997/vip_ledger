package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.domain.calculator.CsvWriter
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.stats.BuildTxnCsvUseCase
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class BuildTxnCsvUseCaseTest {

    private val useCase = BuildTxnCsvUseCase(FakeTimeProvider())

    @Test
    fun headerIsStable() {
        val text = useCase(emptyList())
        assertEquals("\uFEFF" + BuildTxnCsvUseCase.HEADER.joinToString(","), text, "写入器不在末尾补换行")
    }

    @Test
    fun rowCarriesNamesDateAndCountedFlag() {
        val text = useCase(
            txns = listOf(expense()),
            categoryNames = mapOf("c1" to "午餐"),
            accountNames = mapOf("a1" to "现金"),
            tagNames = mapOf("tag1" to "报销", "tag2" to "公司"),
        )
        val row = text.trimStart('\uFEFF').split("\r\n")[1]

        assertEquals(
            listOf(
                "2026-09-24",
                "12:30",
                "支出",
                "30.00",
                "现金",
                "",
                "午餐",
                "报销|公司",
                "午餐",
                "备注",
                "是",
                "手动",
                "已确认",
                "否",
            ),
            parseRow(row),
        )
    }

    @Test
    fun untrackedNamesFallBackToIdsAndAreQuotedWhenNeeded() {
        val text = useCase(
            txns = listOf(expense(source = TxnSource.IMPORT, draftStatus = DraftStatus.PENDING, title = "外卖,加辣")),
        )
        val row = parseRow(text.trimStart('\uFEFF').split("\r\n")[1])

        assertEquals("a1", row[4], "没有账户名时退回 id")
        assertEquals("c1", row[6])
        assertEquals("", row[7])
        assertEquals("外卖,加辣", row[8])
        assertEquals("否", row[10], "待确认草稿不计入统计")
        assertEquals("导入", row[11])
        assertEquals("待确认", row[12])
        assertTrue(text.contains("\"外卖,加辣\""), "含逗号必须加引号")
    }

    @Test
    fun transferKeepsTargetAccountAndAdjustKeepsNegativeAmount() {
        val text = useCase(
            txns = listOf(
                expense(
                    id = "t1",
                    type = TransactionType.TRANSFER,
                    toAccountId = "a2",
                    categoryId = null,
                    title = "转入储蓄卡",
                ),
                expense(id = "t2", type = TransactionType.ADJUST, amountCents = -5000, title = "调减"),
            ),
            accountNames = mapOf("a1" to "现金", "a2" to "储蓄卡"),
        )
        val rows = text.trimStart('\uFEFF').split("\r\n")

        assertEquals("储蓄卡", parseRow(rows[1])[5])
        assertEquals("转账", parseRow(rows[1])[2])
        assertEquals("-50.00", parseRow(rows[2])[3], "调整允许负数，原样导出")
        assertEquals("调整", parseRow(rows[2])[2])
    }

    private fun parseRow(row: String): List<String> {
        val cells = mutableListOf<String>()
        val builder = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < row.length) {
            val char = row[index]
            when {
                char == '"' && inQuotes && row.getOrNull(index + 1) == '"' -> {
                    builder.append('"')
                    index++
                }

                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    cells += builder.toString()
                    builder.clear()
                }

                else -> builder.append(char)
            }
            index++
        }
        cells += builder.toString()
        return cells
    }

    private fun expense(
        id: String = "t1",
        type: TransactionType = TransactionType.EXPENSE,
        amountCents: Long = 3000,
        toAccountId: String? = null,
        categoryId: String? = "c1",
        title: String = "午餐",
        remark: String = "备注",
        source: TxnSource = TxnSource.MANUAL,
        draftStatus: DraftStatus = DraftStatus.CONFIRMED,
        tagIds: List<String> = listOf("tag1", "tag2"),
    ) = Txn(
        id = id,
        ledgerId = "l1",
        type = type,
        amount = Money.ofCents(amountCents),
        accountId = "a1",
        toAccountId = toAccountId,
        categoryId = categoryId,
        occurredAt = LocalDate(2026, 9, 24).startOfDayEpochMillis(ZONE) + (12 * 60 + 30) * 60_000L,
        title = title,
        remark = remark,
        source = source,
        draftStatus = draftStatus,
        tagIds = tagIds,
        createdAt = 0,
        updatedAt = 0,
    )

    private companion object {
        val ZONE: TimeZone = TimeZone.of("Asia/Shanghai")
    }
}