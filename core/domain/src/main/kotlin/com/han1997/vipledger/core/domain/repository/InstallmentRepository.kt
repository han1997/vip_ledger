package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import kotlinx.coroutines.flow.Flow

interface InstallmentRepository {
    fun observePlans(ledgerId: String): Flow<List<InstallmentPlan>>

    suspend fun listPlans(ledgerId: String): List<InstallmentPlan>

    suspend fun getPlan(id: String): InstallmentPlan?

    /** 计划与各期必须一起写入（同一个事务），避免出现没有期数的计划。 */
    suspend fun savePlanWithPeriods(plan: InstallmentPlan, periods: List<InstallmentPeriod>): AppResult<Unit>

    suspend fun updatePlanStatus(planId: String, status: InstallmentPlanStatus, updatedAt: Long): AppResult<Unit>

    suspend fun listPeriods(planId: String): List<InstallmentPeriod>

    suspend fun getPeriod(planId: String, periodNo: Int): InstallmentPeriod?

    suspend fun markPeriodPaid(planId: String, periodNo: Int, txnId: String, paidAt: Long): AppResult<Unit>

    suspend fun softDeletePlan(planId: String, deletedAt: Long): AppResult<Unit>
}