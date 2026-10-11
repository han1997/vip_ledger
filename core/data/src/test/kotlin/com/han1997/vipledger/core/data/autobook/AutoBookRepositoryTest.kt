package com.han1997.vipledger.core.data.autobook

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.common.result.*
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.data.repository.*
import com.han1997.vipledger.core.data.support.AccountBalanceSyncerImpl
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.*
import com.han1997.vipledger.core.domain.autobook.*
import com.han1997.vipledger.core.domain.nlp.*
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.runner.RunWith
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class AutoBookRepositoryTest {
    @Test fun completedJdOcrOrderRequiresReviewAndDeduplicatesByOrderId() = runTest {
        val repo = seed()
        repo.bind(AccountBinding("l", PaymentApp.JD, "白条", "a")).getOrThrow()
        val page = PaymentObservation(PaymentApp.JD, CaptureSource.OCR, "jd-page", 1_791_417_600_000,
            "完成\n测试数码旗舰店\n实付款\n共减¥50.02\n合计¥217.98\n订单编号\njd-order-123\n支付方式\n白条\n支付时间\n2026-09-28 09:26:03")
        val first = assertNotNull(repo.capture(page).getOrThrow())
        assertEquals(21798L, first.facts.amountCents)
        assertEquals(ReviewReason.TIME, first.reason)
        assertEquals(CandidateStatus.PENDING, first.status)
        assertEquals(first.id, assertNotNull(repo.capture(page.copy(eventKey = "jd-reopen")).getOrThrow()).id)
        assertEquals(1, repo.observeCandidates("l").first().size)
        assertEquals(0, db.txnDao().countByLedger("l"))
    }
    @Test fun signedPddOrderNeverPostsAutomaticallyEvenWithVerifiedAppAndBoundAccount() = runTest {
        val repo = seed()
        repo.bind(AccountBinding("l", PaymentApp.PDD, "微信支付", "a")).getOrThrow()
        val page = PaymentObservation(PaymentApp.PDD, CaptureSource.ACCESSIBILITY, "signed-order", 1_791_417_600_000,
            "已签收\n测试数码旗舰店\n实付:,134元\n支付方式：微信支付\n订单编号：pdd-order-123\n下单时间：2026-09-29 17:21:49")
        val first = assertNotNull(repo.capture(page).getOrThrow())
        assertEquals(13400L, first.facts.amountCents)
        assertEquals(CandidateStatus.PENDING, first.status)
        assertEquals(ReviewReason.TIME, first.reason)
        assertEquals(first.id, assertNotNull(repo.capture(page.copy(eventKey = "reopen")).getOrThrow()).id)
        assertEquals(1, repo.observeCandidates("l").first().size)
        assertEquals(0, db.txnDao().countByLedger("l"))
        assertEquals(0L, db.accountDao().getById("a")!!.currentBalanceCents)
    }
    @Test fun expandingOrderNumberEnrichesSameBillButDifferentOrdersStaySeparate() = runTest {
        val repo = seed()
        val initial = PaymentObservation(PaymentApp.ALIPAY, CaptureSource.ACCESSIBILITY, "same-window", 1_791_417_600_000,
            "账单详情\n测试商户\n-3.50\n交易成功\n支付时间\n2025-01-01 12:00:00\n付款方式\n测试信用卡(1234)")
        val first = assertNotNull(repo.capture(initial).getOrThrow())
        assertNull(first.facts.externalId)
        val expanded = initial.copy(text = initial.text + "\n订单号\nplatform-123456")
        val enriched = assertNotNull(repo.capture(expanded).getOrThrow())
        assertEquals(first.id, enriched.id)
        assertEquals("platform-123456", enriched.facts.externalId)
        assertEquals(1, repo.observeCandidates("l").first().size)
        val second = assertNotNull(repo.capture(expanded.copy(text = expanded.text.replace("platform-123456", "platform-654321"))).getOrThrow())
        assertNotEquals(first.id, second.id)
        assertEquals(2, repo.observeCandidates("l").first().size)
        assertEquals(0, db.txnDao().countByLedger("l"))
    }

    @Test fun reliableOrderEnrichesUnknownDirectionWithoutCreatingAnotherCandidate() = runTest {
        val repo = seed()
        val initial = observation(app = PaymentApp.ALIPAY).copy(text = "交易成功\n实付金额：3.50\n订单号：platform-123456")
        val first = assertNotNull(repo.capture(initial).getOrThrow())
        assertNull(first.facts.type)
        val enriched = assertNotNull(repo.capture(initial.copy(eventKey = "new-window",
            text = initial.text.replace("交易成功", "支付成功"))).getOrThrow())
        assertEquals(first.id, enriched.id)
        assertEquals(TransactionType.EXPENSE, enriched.facts.type)
        assertEquals(1, repo.observeCandidates("l").first().size)
    }
    @Test fun ignoredDirectionlessOrderCannotBeRepostedWhenDirectionBecomesKnown() = runTest {
        val repo = seed()
        val initial = observation().copy(text = "交易成功\n实付金额：3.50\n订单号：ignored-order-123")
        val first = assertNotNull(repo.capture(initial).getOrThrow())
        repo.ignore(first.id).getOrThrow()
        assertNull(repo.capture(initial.copy(text = initial.text.replace("交易成功", "支付成功"))).getOrThrow())
        assertEquals(0, db.txnDao().countByLedger("l"))
        assertEquals(CandidateStatus.IGNORED, repo.observeCandidates("l").first().single().status)
    }

    private lateinit var db: LedgerDatabase
    private val time = object : TimeProvider {
        override fun nowMillis() = 1000L
        override val zone = TimeZone.UTC
    }
    @BeforeTest fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @AfterTest fun close() = db.close()
    private fun repository(syncer: AccountBalanceSyncer = AccountBalanceSyncerImpl(db.accountDao(), db.txnDao())) = RoomAutoBookRepository(
        db, AccountRepositoryImpl(db.accountDao()), LedgerRepositoryImpl(db.ledgerDao(), time), CategoryRepositoryImpl(db.categoryDao()),
        TxnRepositoryImpl(db.txnDao(), time), MerchantRuleRepositoryImpl(db.merchantRuleDao()),
        object : CategoryClassifier { override suspend fun classify(title: String, remark: String) = emptyList<CategorySuggestion>() }, syncer,
        time,
    )
    private suspend fun seed(verified: Boolean = true): RoomAutoBookRepository {
        db.ledgerDao().upsert(LedgerEntity("l", "Ledger", createdAt = 1, updatedAt = 1))
        db.accountDao().upsert(AccountEntity("a", "l", "Wallet", AccountType.VIRTUAL, createdAt = 1, updatedAt = 1))
        return repository().also {
            it.saveConfig(AutoBookConfig("l", true, verifiedApps = if (verified) PaymentApp.entries.toSet() else emptySet())).getOrThrow()
            it.bind(AccountBinding("l", PaymentApp.WECHAT, "微信零钱", "a")).getOrThrow()
            it.bind(AccountBinding("l", PaymentApp.TAOBAO, "微信零钱", "a")).getOrThrow()
        }
    }
    private fun observation(key: String = "event", id: String = "order123", app: PaymentApp = PaymentApp.WECHAT, at: Long = 1000) =
        PaymentObservation(app, CaptureSource.NOTIFICATION, key, at,
            "支付成功\n实付金额：100.00\n付款方式：微信零钱\n商户名称：超市\n交易单号：$id")
    @Test fun replayAfterRestartAndDeletionNeverPostsTwice() = runTest {
        val repo = seed()
        val first = assertNotNull(repo.capture(observation()).getOrThrow())
        assertEquals(CandidateStatus.POSTED, first.status)
        db.txnDao().softDelete(first.txnId!!, 2000)
        assertNull(repository().capture(observation(key = "new-event")).getOrThrow())
        assertEquals(1, db.txnDao().listByLedger("l", true).size)
    }
    @Test fun crossPlatformAndEqualAmountSecondPaymentRemainReviewable() = runTest {
        val repo = seed()
        repo.capture(observation()).getOrThrow()
        val second = assertNotNull(repo.capture(observation("second", "different456", PaymentApp.TAOBAO)).getOrThrow())
        assertEquals(CandidateStatus.PENDING, second.status)
        assertEquals(ReviewReason.DUPLICATE, second.reason)
        assertEquals(1, db.txnDao().countByLedger("l"))
        repo.confirm(second.id, CandidateConfirmation(TransactionType.EXPENSE, 10000, "a", title = "第二次购买")).getOrThrow()
        assertEquals(2, db.txnDao().countByLedger("l"))
    }
    @Test fun reopenedIgnoredCandidateReturnsToPendingAndReplayStillDoesNotPost() = runTest {
        // P6：已忽略可重新确认；凭据保持，同一笔支付重放仍不会二次入账。
        val repo = seed(verified = false)
        val candidate = assertNotNull(repo.capture(observation()).getOrThrow())
        assertEquals(ReviewReason.UNVERIFIED, candidate.reason)
        repo.ignore(candidate.id).getOrThrow()
        assertEquals(CandidateStatus.IGNORED, repo.observeCandidates("l").first().single().status)

        repo.reopen(candidate.id).getOrThrow()
        val reopened = repo.observeCandidates("l").first().single()
        assertEquals(CandidateStatus.PENDING, reopened.status)
        assertEquals(ReviewReason.UNVERIFIED, reopened.reason)
        assertEquals(0, db.txnDao().countByLedger("l"))

        // 重放同一笔支付：凭据仍命中，返回同一条候选、不新增记录，也不会产生新账目。
        assertEquals(candidate.id, assertNotNull(repo.capture(observation()).getOrThrow()).id)
        assertEquals(1, repo.observeCandidates("l").first().size)
        assertEquals(0, db.txnDao().countByLedger("l"))

        // 重新确认后可正常入账。
        repo.confirm(candidate.id, CandidateConfirmation(TransactionType.EXPENSE, 10000, "a", title = "重新确认")).getOrThrow()
        assertEquals(1, db.txnDao().countByLedger("l"))
    }
    @Test fun diagnosticAndUnboundAccountsNeverChangeBalance() = runTest {
        val repo = seed(verified = false)
        assertEquals(ReviewReason.UNVERIFIED, repo.capture(observation()).getOrThrow()!!.reason)
        val unknown = observation("unknown", "unknown123").copy(text = "付款成功\n付款金额：10.00\n支付方式：银行卡(1234)")
        assertEquals(ReviewReason.ACCOUNT, repo.capture(unknown).getOrThrow()!!.reason)
        assertEquals(0, db.txnDao().countByLedger("l"))
        assertEquals(0, db.accountDao().getById("a")!!.currentBalanceCents)
    }
    @Test fun aiScreenshotDraftSameDayMakesCapturedCandidateDuplicate() = runTest {
        // P6 判重共享（capture 侧）：同账本同日同额同向的 AI 页来源草稿 → 候选 DUPLICATE，自动入账被挡。
        val repo = seed()
        db.txnDao().upsert(TxnEntity(
            id = "ai-draft", ledgerId = "l", type = TransactionType.EXPENSE, amountCents = 10000,
            accountId = "a", occurredAt = 0, title = "超市", source = TxnSource.AI,
            draftStatus = DraftStatus.PENDING, createdAt = 1, updatedAt = 1,
        ))
        val candidate = assertNotNull(repo.capture(observation()).getOrThrow())
        assertEquals(ReviewReason.DUPLICATE, candidate.reason)
        assertEquals("ai-draft", candidate.duplicateTxnId)
        // 只有截图草稿一条账目：采集候选未自动入账。
        assertEquals(1, db.txnDao().countByLedger("l"))

        // 草稿确认后，同日另一笔同额采集仍被挡（已确认的 AI 账目也算）。
        db.txnDao().updateDraftStatus("ai-draft", "CONFIRMED", 2)
        val second = assertNotNull(repo.capture(observation("second", "different456")).getOrThrow())
        assertEquals(ReviewReason.DUPLICATE, second.reason)
        assertEquals(1, db.txnDao().countByLedger("l"))
    }

    @Test fun hasSimilarCandidateMatchesSameDayPendingOrPostedOnly() = runTest {
        // P6 判重共享（AI 草稿侧）：同日 PENDING/POSTED 候选算疑似重复；IGNORED/不同日/不同额/不同向不算。
        val repo = seed(verified = false)
        val candidate = assertNotNull(repo.capture(observation()).getOrThrow())
        val draftDayStart = 0L // observation(at = 1000, UTC) → 同日日界零点
        assertTrue(repo.hasSimilarCandidate("l", draftDayStart, 10000, TransactionType.EXPENSE))
        assertFalse(repo.hasSimilarCandidate("l", draftDayStart, 9999, TransactionType.EXPENSE))
        assertFalse(repo.hasSimilarCandidate("l", draftDayStart, 10000, TransactionType.INCOME))
        assertFalse(repo.hasSimilarCandidate("l", draftDayStart + 3 * 24 * 60 * 60 * 1000L, 10000, TransactionType.EXPENSE))

        repo.ignore(candidate.id).getOrThrow()
        assertFalse(repo.hasSimilarCandidate("l", draftDayStart, 10000, TransactionType.EXPENSE), "已忽略候选不再算重复")

        repo.reopen(candidate.id).getOrThrow()
        repo.confirm(candidate.id, CandidateConfirmation(TransactionType.EXPENSE, 10000, "a", title = "已入账")).getOrThrow()
        assertTrue(repo.hasSimilarCandidate("l", draftDayStart, 10000, TransactionType.EXPENSE), "已入账候选仍算重复（防双记）")
    }

    @Test fun historicalPaymentPageRequiresReviewInsteadOfPostingAgain() = runTest {
        val repo = seed()
        val event = observation(at = 1_791_417_600_000).let {
            it.copy(text = it.text + "\n付款时间：2025-01-01 12:00:00")
        }
        assertEquals(ReviewReason.TIME, repo.capture(event).getOrThrow()!!.reason)
        assertEquals(0, db.txnDao().countByLedger("l"))
    }
    @Test fun historicalWechatBillKeepsActualAmountWithoutChangingBalance() = runTest {
        val repo = seed()
        val event = PaymentObservation(PaymentApp.WECHAT, CaptureSource.ACCESSIBILITY, "bill", 1_791_417_600_000,
            "-12.30\n原价 ¥12.70\n优惠 ¥0.40\n当前状态\n支付成功\n支付时间\n2025年1月1日 12:00:00\n支付方式\n微信零钱\n交易单号\nwechat-bill-123")
        val result = assertNotNull(repo.capture(event).getOrThrow())
        assertEquals(1230L, result.facts.amountCents)
        assertEquals(CandidateStatus.PENDING, result.status)
        assertEquals(ReviewReason.TIME, result.reason)
        assertEquals(0, db.txnDao().countByLedger("l"))
        assertEquals(0, db.accountDao().getById("a")!!.currentBalanceCents)
    }
    @Test fun repeatedIncompleteBillKeepsExistingRecordUntilAmountIsResolved() = runTest {
        val repo = seed()
        val incomplete = observation().copy(text = "支付成功\n交易单号：same-bill-123")
        val first = assertNotNull(repo.capture(incomplete).getOrThrow())
        assertEquals(ReviewReason.AMOUNT, first.reason)
        assertNull(repo.capture(incomplete.copy(eventKey = "reopened", observedAt = 2000)).getOrThrow())
        val records = repo.observeCandidates("l").first()
        assertEquals(listOf(first), records)
        assertEquals(0, db.txnDao().countByLedger("l"))
    }
    @Test fun balanceFailureRollsBackFinancialMutationButRetainsCandidate() = runTest {
        seed()
        val failing = repository(object : AccountBalanceSyncer {
            override suspend fun syncBalances(accountIds: Collection<String>, updatedAt: Long) { error("failure") }
        })
        val result = assertNotNull(failing.capture(observation()).getOrThrow())
        assertEquals(ReviewReason.STORAGE, result.reason)
        assertEquals(0, db.txnDao().countByLedger("l"))
        assertEquals(1, failing.observeCandidates("l").first().size)
        repository().confirm(result.id, CandidateConfirmation(TransactionType.EXPENSE, 10000, "a", title = "Retry")).getOrThrow()
        assertEquals(-10000, db.accountDao().getById("a")!!.currentBalanceCents)
    }
    @Test fun simultaneousCapturesWriteOnlyOneFinancialTransaction() = runTest {
        val repo = seed()
        val a = async { repo.capture(observation()).getOrThrow() }
        val b = async { repo.capture(observation()).getOrThrow() }
        a.await(); b.await()
        assertEquals(1, db.txnDao().countByLedger("l"))
        assertEquals(-10000, db.accountDao().getById("a")!!.currentBalanceCents)
    }
    @Test fun relatedPartialRefundPostsAndOverRefundWaitsForReview() = runTest {
        val repo = seed()
        repo.capture(observation()).getOrThrow()
        val refund = observation("refund", at = 200000).copy(text = "退款到账\n退款金额：30.00\n退款账户：微信零钱\n原交易单号：order123\n退款单号：refund123")
        assertEquals(CandidateStatus.POSTED, repo.capture(refund).getOrThrow()!!.status)
        assertEquals(-7000, db.accountDao().getById("a")!!.currentBalanceCents)
        val excessive = refund.copy(eventKey = "refund2", text = refund.text.replace("30.00", "80.00").replace("refund123", "refund456"))
        assertEquals(ReviewReason.REFUND, repo.capture(excessive).getOrThrow()!!.reason)
        assertEquals(2, db.txnDao().countByLedger("l"))
    }
}
