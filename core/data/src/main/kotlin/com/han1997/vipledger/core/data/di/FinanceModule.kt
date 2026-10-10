package com.han1997.vipledger.core.data.di

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.data.repository.InvestmentRepositoryImpl
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.InstallmentRepository
import com.han1997.vipledger.core.domain.repository.InvestmentRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.usecase.investment.CloseInvestmentUseCase
import com.han1997.vipledger.core.domain.usecase.investment.CreateInvestmentUseCase
import com.han1997.vipledger.core.domain.usecase.investment.InvestMoreUseCase
import com.han1997.vipledger.core.domain.usecase.investment.InvestmentTxnGuard
import com.han1997.vipledger.core.domain.usecase.investment.ObserveInvestmentsUseCase
import com.han1997.vipledger.core.domain.usecase.investment.RecordInvestmentResultUseCase
import com.han1997.vipledger.core.domain.usecase.investment.RedeemInvestmentUseCase
import com.han1997.vipledger.core.domain.usecase.loan.ChangeInstallmentRateUseCase
import com.han1997.vipledger.core.domain.usecase.loan.CorrectLegacyInstallmentRepaymentUseCase
import com.han1997.vipledger.core.domain.usecase.loan.InstallmentTxnGuard
import com.han1997.vipledger.core.domain.usecase.loan.PrepayInstallmentPlanUseCase
import com.han1997.vipledger.core.domain.usecase.loan.PreviewLegacyInstallmentRepaymentsUseCase
import com.han1997.vipledger.core.domain.usecase.loan.SettleInstallmentPlanUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** P4：分期还款增强与理财台账的用例装配。 */
@Module
@InstallIn(SingletonComponent::class)
object FinanceModule {

    @Provides
    fun installmentTxnGuard(
        repository: InstallmentRepository,
        txns: TxnRepository,
        time: TimeProvider,
    ) = InstallmentTxnGuard(repository, txns, time)

    @Provides
    fun settleInstallmentPlan(
        repository: InstallmentRepository,
        accounts: AccountRepository,
        runner: TransactionRunner,
        createTxn: CreateTxnUseCase,
        time: TimeProvider,
    ) = SettleInstallmentPlanUseCase(repository, accounts, runner, createTxn, time)

    @Provides
    fun prepayInstallmentPlan(
        repository: InstallmentRepository,
        accounts: AccountRepository,
        runner: TransactionRunner,
        createTxn: CreateTxnUseCase,
        time: TimeProvider,
    ) = PrepayInstallmentPlanUseCase(repository, accounts, runner, createTxn, time)

    @Provides
    fun changeInstallmentRate(
        repository: InstallmentRepository,
        runner: TransactionRunner,
        time: TimeProvider,
    ) = ChangeInstallmentRateUseCase(repository, runner, time)

    @Provides
    fun previewLegacyInstallmentRepayments(
        repository: InstallmentRepository,
        txns: TxnRepository,
    ) = PreviewLegacyInstallmentRepaymentsUseCase(repository, txns)

    @Provides
    fun correctLegacyInstallmentRepayment(
        repository: InstallmentRepository,
        txns: TxnRepository,
        runner: TransactionRunner,
        balanceSyncer: AccountBalanceSyncer,
        time: TimeProvider,
        ids: IdGenerator,
    ) = CorrectLegacyInstallmentRepaymentUseCase(repository, txns, runner, balanceSyncer, ids, time)

    @Provides
    @Singleton
    fun investmentRepository(db: LedgerDatabase): InvestmentRepository = InvestmentRepositoryImpl(db)

    @Provides
    fun observeInvestments(repository: InvestmentRepository, txns: TxnRepository) =
        ObserveInvestmentsUseCase(repository, txns)

    @Provides
    fun createInvestment(
        repository: InvestmentRepository,
        accounts: AccountRepository,
        createTxn: CreateTxnUseCase,
        runner: TransactionRunner,
        time: TimeProvider,
        ids: IdGenerator,
    ) = CreateInvestmentUseCase(repository, accounts, createTxn, runner, time, ids)

    @Provides
    fun investMore(
        repository: InvestmentRepository,
        accounts: AccountRepository,
        createTxn: CreateTxnUseCase,
        runner: TransactionRunner,
        time: TimeProvider,
    ) = InvestMoreUseCase(repository, accounts, createTxn, runner, time)

    @Provides
    fun redeemInvestment(
        repository: InvestmentRepository,
        txns: TxnRepository,
        accounts: AccountRepository,
        createTxn: CreateTxnUseCase,
        runner: TransactionRunner,
        time: TimeProvider,
    ) = RedeemInvestmentUseCase(repository, txns, accounts, createTxn, runner, time)

    @Provides
    fun recordInvestmentResult(
        repository: InvestmentRepository,
        accounts: AccountRepository,
        createTxn: CreateTxnUseCase,
        runner: TransactionRunner,
        time: TimeProvider,
    ) = RecordInvestmentResultUseCase(repository, accounts, createTxn, runner, time)

    @Provides
    fun closeInvestment(
        repository: InvestmentRepository,
        runner: TransactionRunner,
        time: TimeProvider,
    ) = CloseInvestmentUseCase(repository, runner, time)

    @Provides
    fun investmentTxnGuard(repository: InvestmentRepository, txns: TxnRepository) =
        InvestmentTxnGuard(repository, txns)
}