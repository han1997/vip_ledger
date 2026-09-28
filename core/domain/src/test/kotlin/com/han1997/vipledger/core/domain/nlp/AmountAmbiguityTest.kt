package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 研究 §1.3 的歧义陷阱与 §5 的负例：规则层必须按「先占位、后归并」消解。 */
class AmountAmbiguityTest {

    @Test
    fun ratioIsNotAmount() {
        val result = corpusParser().parse("打八折 后 240", REF)
        val entry = result.entries.single()
        assertEquals(Money.ofCents(24_000), entry.amount, "折不应被当成金额；金额应为 240")
        assertTrue(
            result.warnings.contains(NlpWarning.RATIO_NOT_RESOLVED),
            "出现折扣词应带 RATIO_NOT_RESOLVED 警告，实际 ${result.warnings}",
        )
    }

    @Test
    fun discountAloneYieldsNoAmount() {
        val result = corpusParser().parse("五折", REF)
        assertTrue(result.entries.isEmpty(), "「五折」不应产出金额，实际 $result")
        assertTrue(result.unresolved.contains(ParsedField.AMOUNT))
    }

    @Test
    fun dateDigitsAreNotAmount() {
        val entry = corpusParser().parse("3月5日 房租 3500", REF).entries.single()
        assertEquals(Money.ofCents(350_000), entry.amount)
    }

    @Test
    fun clockIsNotAmount() {
        val entry = corpusParser().parse("三点半买奶茶 十五块", REF).entries.single()
        assertEquals(Money.ofCents(1500), entry.amount)
    }

    @Test
    fun placeOmissionVsZeroSeparator() {
        assertEquals(Money.ofCents(1_500_000), corpusParser().parse("一万五房租", REF).entries.single().amount)
        assertEquals(Money.ofCents(1_000_500), corpusParser().parse("一万零五 房租", REF).entries.single().amount)
    }

    @Test
    fun blockAfterYuanUnitIsJiao() {
        assertEquals(Money.ofCents(1250), corpusParser().parse("楼下超市买苹果 12块5", REF).entries.single().amount)
    }

    @Test
    fun haiciAmbiguityIsFlaggedNotGuessed() {
        val result = corpusParser().parse("还了 500", REF)
        val entry = result.entries.single()
        assertTrue(
            entry.warnings.contains(NlpWarning.AMBIGUOUS_TYPE_CUE),
            "单字「还」应标记歧义警告，实际 ${entry.warnings}",
        )
        assertTrue(entry.unresolved.contains(ParsedField.TYPE))
        assertEquals(Confidence.LOW, entry.confidence)
    }

    @Test
    fun debtCueBeatsDefaultExpense() {
        assertEquals(
            TransactionType.DEBT_REPAY,
            corpusParser().parse("还信用卡 2000", REF).entries.single().type,
        )
    }

    @Test
    fun pureDigitsWithoutAmountFails() {
        val result = corpusParser().parse("13800000000", REF)
        assertTrue(result is ParseResult.Failed, "连续 ≥ 7 位数字不认成金额，实际 $result")
        assertEquals(ParseFailure.NO_AMOUNT, result.reason)
    }

    @Test
    fun overPreciseNumberIsLowConfidence() {
        val entry = corpusParser().parse("3.14159", REF).entries.single()
        assertEquals(Money.ofCents(314), entry.amount)
        assertEquals(Confidence.LOW, entry.confidence)
    }

    @Test
    fun dateOnlyIsPartialWithAmountUnresolved() {
        val result = corpusParser().parse("今天", REF)
        assertTrue(result is ParseResult.Partial, "「今天」只有日期，实际 $result")
        assertTrue(result.entries.isEmpty())
        assertTrue(result.unresolved.contains(ParsedField.AMOUNT))
    }

    @Test
    fun triggerOnlyIsFailed() {
        val result = corpusParser().parse("帮我记一笔", REF)
        assertTrue(result is ParseResult.Failed)
        assertEquals(ParseFailure.EMPTY_INPUT, result.reason)
    }

    @Test
    fun bareAmountAlongsideUnitAmountIsFlaggedNotSilentlyDropped() {
        // 有单位候选优先的启发式会丢弃裸数字 `28`；丢弃必须可见，不能静默少一笔。
        val result = corpusParser().parse("午饭35块 打车28", REF)
        assertEquals(Money.ofCents(3500), result.entries.single().amount)
        assertTrue(
            result.warnings.contains(NlpWarning.BARE_AMOUNT_DROPPED),
            "丢弃裸金额时应带 BARE_AMOUNT_DROPPED 警告，实际 ${result.warnings}",
        )
    }

    @Test
    fun twoEntriesWhenBothAmountsAreBare() {
        val result = corpusParser().parse("午饭35 打车28", REF)
        assertEquals(2, result.entries.size, "一句话多笔应产出两条草稿（Requirement 6）")
        assertEquals(listOf(Money.ofCents(3500), Money.ofCents(2800)), result.entries.map { it.amount })
    }
}
