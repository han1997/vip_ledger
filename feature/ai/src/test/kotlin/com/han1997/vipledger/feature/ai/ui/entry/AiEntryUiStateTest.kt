package com.han1997.vipledger.feature.ai.ui.entry

import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.nlp.Confidence
import com.han1997.vipledger.core.domain.nlp.NlpWarning
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.domain.nlp.ParsedField
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * AI 记账页的纯状态规则（PRD Requirement 6 / 8）。
 *
 * 覆盖：一句话多笔、未识别字段阻塞确认、账户未识别不静默落库、切类型时分类方向校验。
 */
class AiEntryUiStateTest {

    private val expenseCategories = listOf(
        AiOption("c_food", "餐饮", CategoryKind.EXPENSE),
        AiOption("c_salary", "工资", CategoryKind.INCOME),
        AiOption("c_move", "转账", CategoryKind.TRANSFER),
    )

    private fun entry(
        amountCents: Long,
        title: String,
        accountId: String? = "a_wx",
        categoryId: String? = "c_food",
        unresolved: Set<ParsedField> = emptySet(),
        warnings: List<NlpWarning> = emptyList(),
    ) = ParsedEntry(
        amount = Money.ofCents(amountCents),
        type = TransactionType.EXPENSE,
        date = LocalDate(2026, 9, 27),
        time = null,
        accountId = accountId,
        toAccountId = null,
        categoryId = categoryId,
        title = title,
        remark = "",
        confidence = Confidence.MEDIUM,
        amountHasUnit = true,
        unresolved = unresolved,
        warnings = warnings,
    )

    private fun draftsOf(entries: List<ParsedEntry>): List<AiDraft> =
        draftsOf(entries, occurredAt = { 0L }, source = { TxnSource.AI }, keyOf = { "draft-$it" })

    @Test
    fun oneSentenceYieldsMultipleDrafts() {
        val drafts = draftsOf(listOf(entry(3500, "午饭"), entry(2800, "打车")))
        assertEquals(2, drafts.size)
        assertEquals(listOf(3500L, 2800L), drafts.map { it.amountCents })
        assertEquals(listOf("午饭", "打车"), drafts.map { it.title })
    }

    @Test
    fun unresolvedCategoryBlocksConfirmUntilChosen() {
        val draft = draftsOf(
            listOf(entry(3500, "午饭", categoryId = null, unresolved = setOf(ParsedField.CATEGORY))),
        ).single()

        assertFalse(draft.canConfirm())
        assertEquals(setOf(ParsedField.CATEGORY), draft.blockingUnresolved)
        assertTrue(draft.withCategory("c_food").canConfirm())
    }

    @Test
    fun unresolvedAccountIsShownButNotPersistedUntilChosen() {
        val draft = draftsOf(listOf(entry(3500, "午饭", accountId = null, unresolved = setOf(ParsedField.ACCOUNT)))).single()

        assertFalse(draft.canPersist)
        assertFalse(draft.canConfirm())
        assertNull(draft.toTxnDraft("l1"))

        val chosen = draft.withAccount("a_wx")
        assertTrue(chosen.canPersist)
        assertTrue(chosen.canConfirm())
        assertEquals("a_wx", chosen.toTxnDraft("l1")?.accountId)
    }

    @Test
    fun persistedDraftCarriesPendingStatusAndSource() {
        val draft = draftsOf(listOf(entry(3500, "午饭"))).single()
        val txnDraft: TxnDraft? = draft.toTxnDraft("l1")
        assertEquals(DraftStatus.PENDING, txnDraft?.draftStatus)
        assertEquals(TxnSource.AI, txnDraft?.source)
        assertEquals(Money.ofCents(3500), txnDraft?.amount)
    }

    @Test
    fun switchingTypeDropsMismatchedCategoryAndRequiresRSelection() {
        val draft = draftsOf(listOf(entry(3500, "午饭"))).single()

        val income = draft.withType(TransactionType.INCOME, expenseCategories)
        assertNull(income.categoryId, "支出分类不能挂到收入上，必须清空重选")
        assertTrue(ParsedField.CATEGORY in income.unresolved)
        assertFalse(income.canConfirm())
    }

    @Test
    fun switchingToTransferRequiresTargetAccount() {
        val draft = draftsOf(listOf(entry(3500, "午饭"))).single()

        val transfer = draft.withType(TransactionType.TRANSFER, expenseCategories)
        assertNull(transfer.categoryId, "支出分类不能挂到转账上，必须清空重选")
        assertTrue(ParsedField.TO_ACCOUNT in transfer.unresolved)
        assertFalse(transfer.canConfirm())

        val resolved = transfer.withToAccount("a_cmb").withCategory("c_move")
        assertTrue(ParsedField.TO_ACCOUNT !in resolved.unresolved)
        assertTrue(resolved.canConfirm())
    }

    @Test
    fun dateAndTimeUnresolvedDoNotBlockConfirm() {
        val draft = draftsOf(
            listOf(entry(3500, "午饭", unresolved = setOf(ParsedField.DATE, ParsedField.TIME))),
        ).single()
        assertTrue(draft.canConfirm(), "日期/时间由规则层与落库层负责，不应阻塞用户确认")
    }

    @Test
    fun changingSourceToEqualToTargetClearsConflictAndBlocksConfirm() {
        val transfer = draftsOf(listOf(entry(3500, "午饭"))).single()
            .withType(TransactionType.TRANSFER, expenseCategories)
            .withToAccount("a_cmb")
            .withCategory("c_move")
        assertTrue(transfer.canConfirm())

        // 把转出账户改到与目标账户相同 → 目标账户被清空并重新标未识别，确认被挡下
        val conflicted = transfer.withAccount("a_cmb")
        assertNull(conflicted.toAccountId, "转出 == 转入时目标账户必须清空")
        assertTrue(ParsedField.TO_ACCOUNT in conflicted.unresolved)
        assertFalse(conflicted.canConfirm(), "非法转账不得确认")
    }

    @Test
    fun selectingSourceAsTargetIsIgnored() {
        val transfer = draftsOf(listOf(entry(3500, "午饭"))).single()
            .withType(TransactionType.TRANSFER, expenseCategories)

        val attempt = transfer.withToAccount("a_wx") // 等于转出账户
        assertNull(attempt.toAccountId)
        assertFalse(attempt.canConfirm())
    }
}
