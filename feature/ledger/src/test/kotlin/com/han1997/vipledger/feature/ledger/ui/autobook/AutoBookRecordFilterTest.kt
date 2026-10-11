package com.han1997.vipledger.feature.ledger.ui.autobook

import com.han1997.vipledger.core.domain.autobook.AutoCandidate
import com.han1997.vipledger.core.domain.autobook.CandidateStatus
import com.han1997.vipledger.core.domain.autobook.CaptureSource
import com.han1997.vipledger.core.domain.autobook.PaymentApp
import com.han1997.vipledger.core.domain.autobook.PaymentFacts
import com.han1997.vipledger.core.domain.autobook.ReviewReason
import com.han1997.vipledger.core.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**「识别记录」状态筛选的分组口径（P6：完善候选/已入账/忽略入口）。 */
class AutoBookRecordFilterTest {

    private fun candidate(id: String, status: CandidateStatus) = AutoCandidate(
        id = id, ledgerId = "l", app = PaymentApp.WECHAT, source = CaptureSource.NOTIFICATION,
        occurredAt = 0L,
        facts = PaymentFacts(
            type = TransactionType.EXPENSE, amountCents = 100L, merchant = "测试",
            accountToken = null, targetToken = null, externalId = "order-$id",
            originalId = null, success = true, reason = ReviewReason.NONE,
        ),
        status = status,
    )

    private val state = AutoBookUiState(
        candidates = listOf(
            candidate("p1", CandidateStatus.PENDING),
            candidate("p2", CandidateStatus.PENDING),
            candidate("posted", CandidateStatus.POSTED),
            candidate("linked", CandidateStatus.LINKED),
            candidate("ignored", CandidateStatus.IGNORED),
        ),
    )

    @Test
    fun countsGroupPostedAndLinkedTogether() {
        assertEquals(2, state.pendingCount)
        assertEquals(2, state.postedCount)
        assertEquals(1, state.ignoredCount)
    }

    @Test
    fun candidatesViewShowsOnlyPending() {
        val view = state.copy(recordFilter = RecordFilter.CANDIDATES).filteredCandidates()
        assertEquals(listOf("p1", "p2"), view.map { it.id })
    }

    @Test
    fun postedViewIncludesLinkedEntries() {
        val view = state.copy(recordFilter = RecordFilter.POSTED).filteredCandidates()
        assertTrue("posted" in view.map { it.id } && "linked" in view.map { it.id })
        assertTrue(view.none { it.status == CandidateStatus.PENDING || it.status == CandidateStatus.IGNORED })
    }

    @Test
    fun ignoredViewShowsOnlyIgnored() {
        val view = state.copy(recordFilter = RecordFilter.IGNORED).filteredCandidates()
        assertEquals(listOf("ignored"), view.map { it.id })
    }
}