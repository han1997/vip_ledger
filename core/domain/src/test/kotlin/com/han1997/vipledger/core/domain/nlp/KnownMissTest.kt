package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 研究 §6「规则层做不到」的已知局限：断言的是**局限本身**，而不是假装它们能用。
 * 这些不写特判 hack，只保证降级为 Partial/LOW/警告，绝不抛异常。
 */
class KnownMissTest {

    @Test
    fun anaphoraCannotResolveAmount() {
        // 省略式引用「跟昨天一样」需要历史记忆，句内规则拿不到金额。
        val result = corpusParser().parse("跟昨天一样", REF)
        assertTrue(result.entries.isEmpty(), "省略式引用不应凭空产出金额，实际 $result")
        assertTrue(result.unresolved.contains(ParsedField.AMOUNT))
    }

    @Test
    fun unseparatedMultipleTransactionsStayOneEntry() {
        // 一句话多笔（无分隔、混单位）不做子句切分，tier1 只能整句一笔记（研究 §6.2）。
        val result = corpusParser().parse("早饭10块午饭20打车30", REF)
        assertEquals(1, result.entries.size, "局限：无分隔多笔只保留一笔，实际 ${result.entries.size}")
        assertEquals(Money.ofCents(1000), result.entries.single().amount)
    }

    @Test
    fun discountMathIsNotPerformed() {
        // 算术/分摊（打八折后算折后价）不做计算，只保留原值并给警告（研究 §6.3）。
        val result = corpusParser().parse("打八折后 240", REF)
        assertEquals(Money.ofCents(24_000), result.entries.single().amount, "局限：不计算折后价")
        assertTrue(result.warnings.contains(NlpWarning.RATIO_NOT_RESOLVED))
    }

    @Test
    fun vagueQuantityIgnoresTheVagueModifier() {
        // 范围与模糊量（差不多/一百多）只落单值，「差不多」被忽略（研究 §6.4）。
        val result = corpusParser().parse("差不多一百", REF)
        assertEquals(Money.ofCents(10_000), result.entries.single().amount)
    }

    @Test
    fun directionWithoutKeywordIsUnresolved() {
        // 无关键词的方向判定：`星巴克 35` 判不出收支，TYPE 明示为未识别（研究 §6.6）。
        val entry = corpusParser().parse("星巴克 35", REF).entries.single()
        assertEquals(TransactionType.EXPENSE, entry.type, "无线索时兜底为支出")
        assertTrue(entry.unresolved.contains(ParsedField.TYPE), "兜底的支出类型必须显式标为未识别")
    }
}
