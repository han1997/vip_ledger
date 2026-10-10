package com.han1997.vipledger.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.common.result.getOrThrow
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.data.repository.AccountRepositoryImpl
import com.han1997.vipledger.core.data.repository.CategoryRepositoryImpl
import com.han1997.vipledger.core.data.repository.InstallmentRepositoryImpl
import com.han1997.vipledger.core.data.repository.InvestmentRepositoryImpl
import com.han1997.vipledger.core.data.repository.TxnRepositoryImpl
import com.han1997.vipledger.core.data.support.AccountBalanceSyncerImpl
import com.han1997.vipledger.core.data.support.TransactionRunnerImpl
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.CategoryEntity
import com.han1997.vipledger.core.database.entity.LedgerEntity
import com.han1997.vipledger.core.domain.calculator.InstallmentAmounts
import com.han1997.vipledger.core.domain.calculator.StatsCalculator
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.usecase.investment.CreateInvestmentUseCase
import com.han1997.vipledger.core.domain.usecase.investment.InvestmentTxnGuard
import com.han1997.vipledger.core.domain.usecase.investment.ObserveInvestmentsUseCase
import com.han1997.vipledger.core.domain.usecase.investment.RecordInvestmentResultUseCase
import com.han1997.vipledger.core.domain.usecase.investment.RedeemInvestmentUseCase
import com.han1997.vipledger.core.domain.usecase.loan.InstallmentTxnGuard
import com.han1997.vipledger.core.domain.usecase.loan.RepayInstallmentPeriodInput
import com.han1997.vipledger.core.domain.usecase.loan.RepayInstallmentPeriodUseCase
import com.han1997.vipledger.core.domain.usecase.loan.SaveInstallmentPlanInput
import com.han1997.vipledger.core.domain.usecase.loan.SaveInstallmentPlanUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.DeleteTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.UpdateTxnUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentOrigin
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.InvestmentLinkKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.runner.RunWith

/**
 * P4 分期分账与理财台账跨越真实 Room、仓库与用例的集成回归。
 *
 * 覆盖：三口径分账（资金/负债/支出）、关联流水的改删守卫与重算、关联插入失败整单回滚、
 * 新放款只计一次本金、以及快照对三张新表的往返。
 */
@RunWith(AndroidJUnit4::class)
class FinanceIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: LedgerDatabase
    private lateinit var installments: InstallmentRepositoryImpl
    private lateinit var investments: InvestmentRepositoryImpl
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var txns: TxnRepositoryImpl
    private lateinit var categories: CategoryRepositoryImpl
    private lateinit var runner: TransactionRunnerImpl
    private lateinit var syncer: AccountBalanceSyncerImpl
    private lateinit var create: CreateTxnUseCase
    private lateinit var savePlan: SaveInstallmentPlanUseCase
    private lateinit var repay: RepayInstallmentPeriodUseCase
    private lateinit var update: UpdateTxnUseCase
    private lateinit var delete: DeleteTxnUseCase
    private lateinit var createInvestment: CreateInvestmentUseCase
    private lateinit var redeemInvestment: RedeemInvestmentUseCase
    private lateinit var recordInvestment: RecordInvestmentResultUseCase
    private lateinit var observeInvestments: ObserveInvestmentsUseCase

    private val zone = TimeZone.of("Asia/Shanghai")
    private val september = LocalDate(2026, 9, 1).startOfDayEpochMillis(zone)
    private val october = LocalDate(2026, 10, 1).startOfDayEpochMillis(zone)
    private val time = object : TimeProvider {
        override fun nowMillis() = october
        override val zone = TimeZone.of("Asia/Shanghai")
    }
    private val ids = object : IdGenerator {
        override fun newId() = UUID.randomUUID().toString()
    }

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).allowMainThreadQueries().build()
        installments = InstallmentRepositoryImpl(
            db.installmentPlanDao(),
            db.installmentPeriodDao(),
            db.installmentLinkDao(),
            TransactionRunnerImpl(db),
        )
        investments = InvestmentRepositoryImpl(db)
        accounts = AccountRepositoryImpl(db.accountDao())
        txns = TxnRepositoryImpl(db.txnDao(), time)
        categories = CategoryRepositoryImpl(db.categoryDao())
        runner = TransactionRunnerImpl(db)
        syncer = AccountBalanceSyncerImpl(db.accountDao(), db.txnDao())
        create = CreateTxnUseCase(txns, accounts, categories, runner, syncer, time, ids)
        savePlan = SaveInstallmentPlanUseCase(installments, accounts, runner, create, time, ids)
        repay = RepayInstallmentPeriodUseCase(installments, accounts, runner, create, time)
        val installmentGuard = InstallmentTxnGuard(installments, txns, time)
        val investmentGuard = InvestmentTxnGuard(investments, txns)
        update = UpdateTxnUseCase(
            txns,
            accounts,
            categories,
            runner,
            syncer,
            time,
            installmentGuard = installmentGuard,
            investmentGuard = investmentGuard,
        )
        delete = DeleteTxnUseCase(
            txns,
            runner,
            syncer,
            time,
            installmentGuard = installmentGuard,
            investmentGuard = investmentGuard,
        )
        createInvestment = CreateInvestmentUseCase(investments, accounts, create, runner, time, ids)
        redeemInvestment = RedeemInvestmentUseCase(investments, txns, accounts, create, runner, time)
        recordInvestment = RecordInvestmentResultUseCase(investments, accounts, create, runner, time)
        observeInvestments = ObserveInvestmentsUseCase(investments, txns)
    }

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun seed() {
        db.ledgerDao().upsert(LedgerEntity(id = "l1", name = "One", createdAt = 1, updatedAt = 1))
        db.accountDao().upsert(
            AccountEntity(
                id = "cash",
                ledgerId = "l1",
                name = "现金",
                type = AccountType.CASH,
                initialBalanceCents = 1_000_000,
                currentBalanceCents = 1_000_000,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        db.accountDao().upsert(
            AccountEntity(
                id = "card",
                ledgerId = "l1",
                name = "信用卡",
                type = AccountType.CREDIT_CARD,
                initialBalanceCents = 0,
                currentBalanceCents = 0,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        db.categoryDao().upsert(
            CategoryEntity(
                id = "shopping",
                ledgerId = "l1",
                parentId = null,
                name = "购物",
                kind = CategoryKind.EXPENSE,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
    }

    /** 先让负债账户真的欠上 1000 元（9 月刷卡），10 月再还款。 */
    private suspend fun spendOnCard(cents: Long = 100_000) = create(
        TxnDraft(
            ledgerId = "l1",
            type = TransactionType.EXPENSE,
            amount = Money.ofCents(cents),
            accountId = "card",
            categoryId = "shopping",
            occurredAt = september,
            title = "刷卡",
        ),
    ).getOrThrow()

    /** 本金 1000 + 利息 50 + 手续费 10（单位：元），一次性还清。 */
    private suspend fun newOnePeriodPlan(): String = savePlan(
        SaveInstallmentPlanInput(
            ledgerId = "l1",
            accountId = "card",
            name = "装修分期",
            method = InstallmentMethod.CUSTOM,
            principal = Money.ofCents(100_000),
            periods = 1,
            firstDueDate = LocalDate(2026, 10, 15),
            customAmounts = listOf(
                InstallmentAmounts(
                    principal = Money.ofCents(100_000),
                    interest = Money.ofCents(5_000),
                    fee = Money.ofCents(1_000),
                ),
            ),
        ),
    ).getOrThrow().id

    private suspend fun monthSummary() = StatsCalculator.summarize(
        txns.listByLedger("l1"),
        categories.listByLedger("l1", includeArchived = true),
        emptyList(),
        DateRange(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31)),
        zone,
    )

    @Test
    fun repaymentSplitsIntoPrincipalInterestAndFeeWithThePromisedBalances() = runTest {
        seed()
        val planId = newOnePeriodPlan()
        spendOnCard()
        assertEquals(-100_000L, assertNotNull(accounts.getById("card")).currentBalance.cents)
        val entries = repay(RepayInstallmentPeriodInput(planId, 1, "cash")).getOrThrow()

        assertEquals(3, entries.size, "本金 + 利息 + 手续费三条流水")
        assertEquals(Money.ofCents(100_000), entries.single { it.type == TransactionType.TRANSFER }.amount)
        assertEquals(
            Money.ofCents(6_000),
            Money.sum(entries.filter { it.type == TransactionType.EXPENSE }.map { it.amount }),
        )

        // 验收：资金 −1060、负债账户 −1000、支出 60（元）
        assertEquals(894_000L, assertNotNull(accounts.getById("cash")).currentBalance.cents)
        assertEquals(0L, assertNotNull(accounts.getById("card")).currentBalance.cents, "负债减少 1000 元后归零")
        assertEquals(Money.ofCents(6_000), monthSummary().expense)

        val period = assertNotNull(installments.getPeriod(planId, 1))
        assertEquals(InstallmentPeriodStatus.PAID, period.status)
        assertEquals(Money.ofCents(100_000), period.paidPrincipal)
        assertEquals(Money.ofCents(5_000), period.paidInterest)
        assertEquals(Money.ofCents(1_000), period.paidFee)
        assertEquals(InstallmentPlanStatus.CLOSED, assertNotNull(installments.getPlan(planId)).status)
    }

    @Test
    fun installmentGuardKeepsTheSplitIntactAndResyncsOnDelete() = runTest {
        seed()
        val planId = newOnePeriodPlan()
        spendOnCard()
        repay(RepayInstallmentPeriodInput(planId, 1, "cash")).getOrThrow()
        val entries = txns.listByLedger("l1")
        val principal = entries.single { it.type == TransactionType.TRANSFER }
        val interest = entries.single { it.type == TransactionType.EXPENSE && it.amount == Money.ofCents(5_000) }

        // 本金分账不能改类型 / 方向，也不能超过该期应还本金
        assertFalse(update(principal.copy(type = TransactionType.EXPENSE)).isSuccess)
        assertFalse(update(principal.copy(toAccountId = null)).isSuccess)
        assertFalse(update(principal.copy(amount = Money.ofCents(105_000))).isSuccess)
        // 利息分账必须保持支出类型
        assertFalse(update(interest.copy(type = TransactionType.TRANSFER)).isSuccess)

        // 删除利息分账：允许，但期次累计要同步回退
        assertTrue(delete(interest.id).isSuccess)
        val period = assertNotNull(installments.getPeriod(planId, 1))
        assertEquals(InstallmentPeriodStatus.PARTIAL, period.status)
        assertEquals(Money.ofCents(100_000), period.paidPrincipal)
        assertEquals(Money.ZERO, period.paidInterest)
        assertEquals(Money.ofCents(1_000), monthSummary().expense, "利息流水删除后支出只剩手续费")
        assertEquals(899_000L, assertNotNull(accounts.getById("cash")).currentBalance.cents)
    }

    @Test
    fun linkInsertFailureRollsBackPeriodsAndTransactionsAtomically() = runTest {
        seed()
        val planId = newOnePeriodPlan()
        val cashBefore = assertNotNull(accounts.getById("cash")).currentBalance.cents
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_installment_link BEFORE INSERT ON installment_link BEGIN SELECT RAISE(ABORT,'fixture'); END",
        )

        assertFalse(repay(RepayInstallmentPeriodInput(planId, 1, "cash")).isSuccess)

        assertEquals(0, txns.countByLedger("l1"))
        assertEquals(cashBefore, assertNotNull(accounts.getById("cash")).currentBalance.cents)
        val period = assertNotNull(installments.getPeriod(planId, 1))
        assertEquals(InstallmentPeriodStatus.PENDING, period.status)
        assertEquals(Money.ZERO, period.paidTotal)
        assertEquals(InstallmentPlanStatus.ACTIVE, assertNotNull(installments.getPlan(planId)).status)
    }

    @Test
    fun disbursementPlanPostsPrincipalOnceAndNoteExisting() = runTest {
        seed()
        val disbursed = savePlan(
            SaveInstallmentPlanInput(
                ledgerId = "l1",
                accountId = "card",
                name = "新放款",
                method = InstallmentMethod.CUSTOM,
                principal = Money.ofCents(100_000),
                periods = 1,
                firstDueDate = LocalDate(2026, 10, 15),
                origin = InstallmentOrigin.DISBURSEMENT,
                disbursementAccountId = "cash",
                customAmounts = listOf(InstallmentAmounts(principal = Money.ofCents(100_000))),
            ),
        ).getOrThrow()

        assertNotNull(disbursed.disbursedAt)
        val txn = txns.listByLedger("l1").single()
        assertEquals(TransactionType.TRANSFER, txn.type)
        assertEquals("card", txn.accountId)
        assertEquals("cash", txn.toAccountId)
        assertEquals(1_100_000L, assertNotNull(accounts.getById("cash")).currentBalance.cents)
        assertEquals(-100_000L, assertNotNull(accounts.getById("card")).currentBalance.cents)

        // 已有负债不会再发本金
        val existing = savePlan(
            SaveInstallmentPlanInput(
                ledgerId = "l1",
                accountId = "card",
                name = "已有负债",
                method = InstallmentMethod.CUSTOM,
                principal = Money.ofCents(50_000),
                periods = 1,
                firstDueDate = LocalDate(2026, 10, 15),
                customAmounts = listOf(InstallmentAmounts(principal = Money.ofCents(50_000))),
            ),
        ).getOrThrow()
        assertEquals(1, txns.countByLedger("l1"), "已有负债不产生新流水")
        assertEquals(InstallmentOrigin.EXISTING, existing.origin)
    }

    @Test
    fun investmentFlowsUseTransfersAndResultsAndSurviveASnapshotRoundTrip() = runTest {
        seed()
        val record = createInvestment("l1", "基金", "cash", Money.ofCents(100_000)).getOrThrow()
        recordInvestment(record.id, "cash", Money.ofCents(5_000), InvestmentLinkKind.INCOME).getOrThrow()
        redeemInvestment(record.id, "cash", Money.ofCents(30_000)).getOrThrow()

        val ledgerTxns = txns.listByLedger("l1")
        assertEquals(2, ledgerTxns.count { it.type == TransactionType.TRANSFER })
        assertEquals(1, ledgerTxns.count { it.type == TransactionType.INCOME })
        assertEquals(Money.ofCents(5_000), monthSummary().income)

        val progress = observeInvestments("l1").first().single()
        assertEquals(Money.ofCents(100_000), progress.summary.invested)
        assertEquals(Money.ofCents(70_000), progress.summary.principalOutstanding)
        assertEquals(Money.ofCents(5_000), progress.summary.realizedNet)

        val archive = SnapshotArchive(context, db)
        val file = File(context.cacheDir, "finance-${System.nanoTime()}.zip")
        try {
            val manifest = archive.create(file)
            assertEquals(1, manifest.counts["investment"])
            assertEquals(3, manifest.counts["investment_link"])
            SnapshotArchive.TABLES.asReversed().forEach {
                db.openHelper.writableDatabase.execSQL("DELETE FROM `$it`")
            }
            assertTrue(investments.observeInvestments("l1").first().isEmpty())

            archive.restore(file)

            val restored = observeInvestments("l1").first().single()
            assertEquals(Money.ofCents(70_000), restored.summary.principalOutstanding)
            assertEquals(Money.ofCents(5_000), restored.summary.realizedNet)
            assertEquals(935_000L, assertNotNull(accounts.getById("cash")).currentBalance.cents)
        } finally {
            file.delete()
        }
    }
}
