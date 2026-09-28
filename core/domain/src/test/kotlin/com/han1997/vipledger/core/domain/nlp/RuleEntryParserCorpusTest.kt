package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * 研究 §5 语料表（≥20 条）的回归测试。
 *
 * 固定参考日 [REF]=2026-09-28（星期一）；断言金额用 [Money]（分）比较，不比较字符串。
 */
class RuleEntryParserCorpusTest {

    private data class Case(
        val input: String,
        val amountCents: Long,
        val date: LocalDate,
        val type: TransactionType,
        val accountId: String? = null,
        val toAccountId: String? = null,
        val categoryId: String? = null,
        val confidence: Confidence? = null,
        val title: String? = null,
        val time: LocalTime? = null,
    )

    private val cases = listOf(
        Case(
            "昨天午饭 35 块 微信",
            amountCents = 3500,
            date = LocalDate(2026, 9, 27),
            type = TransactionType.EXPENSE,
            accountId = "acct_wechat",
            confidence = Confidence.MEDIUM,
            title = "午饭",
        ),
        Case(
            "上周三打车 28.5",
            amountCents = 2850,
            date = LocalDate(2026, 9, 23),
            type = TransactionType.EXPENSE,
        ),
        Case(
            "3月5日 房租 3500",
            amountCents = 350_000,
            date = LocalDate(2026, 3, 5),
            type = TransactionType.EXPENSE,
            categoryId = "cat_rent",
            title = "房租",
        ),
        Case(
            "收到工资 12000",
            amountCents = 1_200_000,
            date = REF,
            type = TransactionType.INCOME,
            categoryId = "cat_salary",
            title = "收到工资",
        ),
        Case(
            "楼下超市买苹果 12块5",
            amountCents = 1250,
            date = REF,
            type = TransactionType.EXPENSE,
        ),
        Case(
            "退款 88 元",
            amountCents = 8800,
            date = REF,
            type = TransactionType.INCOME,
            categoryId = "cat_refund",
            confidence = Confidence.HIGH,
            title = "退款",
        ),
        Case(
            "今天早上买咖啡 18.5 支付宝",
            amountCents = 1850,
            date = REF,
            type = TransactionType.EXPENSE,
            accountId = "acct_alipay",
            title = "买咖啡",
            time = LocalTime(8, 0),
        ),
        Case(
            "今天午饭 30",
            amountCents = 3000,
            date = REF,
            type = TransactionType.EXPENSE,
            confidence = Confidence.LOW,
        ),
        Case(
            "晚上打车回家 46",
            amountCents = 4600,
            date = REF,
            type = TransactionType.EXPENSE,
            time = LocalTime(20, 0),
        ),
        Case(
            "3/5 买书 42",
            amountCents = 4200,
            date = LocalDate(2026, 3, 5),
            type = TransactionType.EXPENSE,
        ),
        Case(
            "前天超市 123.45",
            amountCents = 12_345,
            date = LocalDate(2026, 9, 26),
            type = TransactionType.EXPENSE,
        ),
        Case(
            "还信用卡 2000",
            amountCents = 200_000,
            date = REF,
            type = TransactionType.DEBT_REPAY,
            toAccountId = "acct_credit",
        ),
        Case(
            "转账 500 到支付宝",
            amountCents = 50_000,
            date = REF,
            type = TransactionType.TRANSFER,
            toAccountId = "acct_alipay",
        ),
        Case(
            "收到报销 1200 微信",
            amountCents = 120_000,
            date = REF,
            type = TransactionType.INCOME,
            accountId = "acct_wechat",
        ),
        Case(
            "三斤苹果一共 一百二",
            amountCents = 12_000,
            date = REF,
            type = TransactionType.EXPENSE,
        ),
        Case(
            "一万五房租",
            amountCents = 1_500_000,
            date = REF,
            type = TransactionType.EXPENSE,
        ),
        Case(
            "一万零五 房租",
            amountCents = 1_000_500,
            date = REF,
            type = TransactionType.EXPENSE,
        ),
        Case(
            "这周三聚餐 200",
            amountCents = 20_000,
            date = LocalDate(2026, 9, 30),
            type = TransactionType.EXPENSE,
        ),
        Case(
            "周三聚餐 200",
            amountCents = 20_000,
            date = LocalDate(2026, 9, 30),
            type = TransactionType.EXPENSE,
        ),
        Case(
            "三点半买奶茶 十五块",
            amountCents = 1500,
            date = REF,
            type = TransactionType.EXPENSE,
            time = LocalTime(15, 30),
        ),
        Case(
            "１２．５ 打车",
            amountCents = 1250,
            date = REF,
            type = TransactionType.EXPENSE,
        ),
        Case(
            "¥35 午饭",
            amountCents = 3500,
            date = REF,
            type = TransactionType.EXPENSE,
        ),
        Case(
            "5号交话费 99",
            amountCents = 9900,
            date = LocalDate(2026, 9, 5),
            type = TransactionType.EXPENSE,
        ),
        Case(
            "借出 2000 给李四",
            amountCents = 200_000,
            date = REF,
            type = TransactionType.TRANSFER,
        ),
        Case(
            "打八折 后 240",
            amountCents = 24_000,
            date = REF,
            type = TransactionType.EXPENSE,
        ),
    )

    @Test
    fun corpusMatchesExpectedFields() {
        val parser = corpusParser()
        for (case in cases) {
            val result = parser.parse(case.input, REF)
            val entry = result.entries.singleOrNull()
                ?: error("「${case.input}」期望恰好一笔，实际 ${result.entries.size}（$result）")

            assertEquals(Money.ofCents(case.amountCents), entry.amount, "金额不符：${case.input}")
            assertEquals(case.date, entry.date, "日期不符：${case.input}")
            assertEquals(case.type, entry.type, "类型不符：${case.input}")
            assertEquals(case.accountId, entry.accountId, "账户不符：${case.input}")
            assertEquals(case.toAccountId, entry.toAccountId, "目标账户不符：${case.input}")
            if (case.categoryId != null) {
                assertEquals(case.categoryId, entry.categoryId, "分类不符：${case.input}")
            }
            if (case.confidence != null) {
                assertEquals(case.confidence, entry.confidence, "置信度不符：${case.input}")
            }
            if (case.title != null) {
                assertEquals(case.title, entry.title, "标题不符：${case.input}")
            }
            if (case.time != null) {
                assertEquals(case.time, entry.time, "时间不符：${case.input}")
            }
        }
    }

    @Test
    fun multipleAmountsProduceMultipleEntries() {
        val result = corpusParser().parse("午饭35 打车28", REF)
        assertEquals(2, result.entries.size, "「午饭35 打车28」应产出两笔（PRD Requirement 6）")
        assertEquals(Money.ofCents(3500), result.entries[0].amount)
        assertEquals(Money.ofCents(2800), result.entries[1].amount)
        assertEquals(listOf("午饭", "打车"), result.entries.map { it.title })
    }

    @Test
    fun dateDigitsDoNotBecomeAmount() {
        val result = corpusParser().parse("3月5日 房租 3500", REF)
        val entry = result.entries.single()
        assertEquals(Money.ofCents(350_000), entry.amount)
        assertEquals(LocalDate(2026, 3, 5), entry.date)
    }

    @Test
    fun fullyResolvedSentenceIsOk() {
        // 类型有线索 + 账户命中 + 标题含真实分类名 → 所有字段已确定。
        val result = corpusParser().parse("收到工资 12000 微信", REF)
        assertTrue(result is ParseResult.Ok, "期望 Ok，实际 $result")
        val entry = result.entries.single()
        assertEquals(TransactionType.INCOME, entry.type)
        assertEquals("acct_wechat", entry.accountId)
        assertEquals("cat_salary", entry.categoryId)
        assertTrue(entry.unresolved.isEmpty())
    }

    @Test
    fun merchantRuleHitSetsCategoryDirectly() {
        val parser = corpusParser(listOf(merchantRule("rule_lunch", "午饭", "cat_food")))
        val entry = parser.parse("昨天午饭 35 块", REF).entries.single()
        assertEquals("cat_food", entry.categoryId, "merchant_rule 命中应直接定分类")
        assertEquals("rule_lunch", entry.matchedRuleId)
    }
}
