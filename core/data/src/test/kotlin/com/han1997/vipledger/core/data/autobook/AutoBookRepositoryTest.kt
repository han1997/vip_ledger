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
        TxnRepositoryImpl(db.txnDao(), db.txnTagDao(), time), MerchantRuleRepositoryImpl(db.merchantRuleDao()),
        object : CategoryClassifier { override suspend fun classify(title: String, remark: String) = emptyList<CategorySuggestion>() }, syncer,
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
    @Test fun diagnosticAndUnboundAccountsNeverChangeBalance() = runTest {
        val repo = seed(verified = false)
        assertEquals(ReviewReason.UNVERIFIED, repo.capture(observation()).getOrThrow()!!.reason)
        val unknown = observation("unknown", "unknown123").copy(text = "付款成功\n付款金额：10.00\n支付方式：银行卡(1234)")
        assertEquals(ReviewReason.ACCOUNT, repo.capture(unknown).getOrThrow()!!.reason)
        assertEquals(0, db.txnDao().countByLedger("l"))
        assertEquals(0, db.accountDao().getById("a")!!.currentBalanceCents)
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
