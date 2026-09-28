package com.han1997.vipledger.core.domain.usecase.backup

import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.stats.BuildTxnCsvUseCase
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class BuildBackupCsvUseCaseTest {

    private val timeProvider = FakeTimeProvider()
    private val useCase = BuildBackupCsvUseCase(timeProvider)

    private fun category(id: String, name: String, parentId: String? = null) = Category(
        id = id,
        ledgerId = "l1",
        parentId = parentId,
        name = name,
        kind = CategoryKind.EXPENSE,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun txn(
        id: String,
        amountCents: Long = 1_000,
        categoryId: String? = null,
        title: String = "",
        remark: String = "",
        tagIds: List<String> = emptyList(),
        occurredAt: Long = LocalDate(2026, 9, 28).startOfDayEpochMillis(timeProvider.zone),
    ) = Txn(
        id = id,
        ledgerId = "l1",
        type = TransactionType.EXPENSE,
        amount = Money.ofCents(amountCents),
        accountId = "a1",
        categoryId = categoryId,
        occurredAt = occurredAt,
        title = title,
        remark = remark,
        source = TxnSource.MANUAL,
        tagIds = tagIds,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun headerIsTheM5ExternalFormatNotTheDetailExportFormat() {
        assertEquals(
            listOf("日期", "类型", "金额", "分类", "子分类", "标签", "账户", "目标账户", "标题", "备注", "来源"),
            BuildBackupCsvUseCase.HEADER,
        )
        assertFalse("时间" in BuildBackupCsvUseCase.HEADER, "M5 格式不含时间列")
        assertFalse(BuildBackupCsvUseCase.HEADER == BuildTxnCsvUseCase.HEADER, "不得与 M3 明细导出共用表头")
    }

    @Test
    fun twoLevelCategoriesBecomeTopAndSubColumns() {
        val csv = useCase(
            txns = listOf(txn("t1", categoryId = "breakfast", title = "早餐")),
            categories = listOf(category("food", "餐饮"), category("breakfast", "早餐", parentId = "food")),
            accountNames = mapOf("a1" to "现金"),
            tagNames = emptyMap(),
        )
        val row = csv.removePrefix("﻿").split("\r\n")[1].split(",")
        assertEquals("餐饮", row[3])
        assertEquals("早餐", row[4])
        assertEquals("现金", row[6])
    }

    @Test
    fun topLevelCategoryLeavesSubColumnEmptyAndAmountsAreTwoDigits() {
        val csv = useCase(
            txns = listOf(txn("t1", amountCents = 123_456, categoryId = "food")),
            categories = listOf(category("food", "餐饮")),
        )
        val row = csv.removePrefix("﻿").split("\r\n")[1].split(",")
        assertEquals("1234.56", row[2])
        assertEquals("餐饮", row[3])
        assertEquals("", row[4])
    }

    @Test
    fun specialCharactersAreEscapedAndLinesEndWithCrLf() {
        val csv = useCase(
            txns = listOf(txn("t1", title = "午餐,带\"引号\"")),
            categories = emptyList(),
        )
        assertTrue(csv.startsWith("﻿"))
        assertTrue(csv.contains("\"午餐,带\"\"引号\"\"\""))
        assertTrue(csv.contains("\r\n"))
    }
}
