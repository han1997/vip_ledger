package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.data.mapper.toEntity
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.dao.InstallmentPeriodDao
import com.han1997.vipledger.core.database.dao.InstallmentPlanDao
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.repository.InstallmentRepository
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InstallmentRepositoryImpl @Inject constructor(
    private val planDao: InstallmentPlanDao,
    private val periodDao: InstallmentPeriodDao,
    private val transactionRunner: TransactionRunner,
) : InstallmentRepository {

    override fun observePlans(ledgerId: String): Flow<List<InstallmentPlan>> =
        planDao.observePlans(ledgerId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun listPlans(ledgerId: String): List<InstallmentPlan> =
        planDao.listPlans(ledgerId).map { it.toDomain() }

    override suspend fun getPlan(id: String): InstallmentPlan? = planDao.getPlan(id)?.toDomain()

    override suspend fun savePlanWithPeriods(
        plan: InstallmentPlan,
        periods: List<InstallmentPeriod>,
    ): AppResult<Unit> = dataResult {
        transactionRunner.inTransaction {
            planDao.upsertPlan(plan.toEntity())
            planDao.deletePeriodsOfPlan(plan.id)
            if (periods.isNotEmpty()) {
                periodDao.upsertAll(periods.map { it.toEntity() })
            }
        }
    }

    override suspend fun updatePlanStatus(
        planId: String,
        status: InstallmentPlanStatus,
        updatedAt: Long,
    ): AppResult<Unit> = dataResult { planDao.updatePlanStatus(planId, status, updatedAt) }

    override suspend fun listPeriods(planId: String): List<InstallmentPeriod> =
        periodDao.listPeriods(planId).map { it.toDomain() }

    override suspend fun getPeriod(planId: String, periodNo: Int): InstallmentPeriod? =
        periodDao.getPeriod(planId, periodNo)?.toDomain()

    override suspend fun markPeriodPaid(
        planId: String,
        periodNo: Int,
        txnId: String,
        paidAt: Long,
    ): AppResult<Unit> = dataResult {
        periodDao.markPaid(planId, periodNo, InstallmentPeriodStatus.PAID, txnId, paidAt)
    }

    override suspend fun softDeletePlan(planId: String, deletedAt: Long): AppResult<Unit> =
        dataResult { planDao.softDeletePlan(planId, deletedAt) }
}