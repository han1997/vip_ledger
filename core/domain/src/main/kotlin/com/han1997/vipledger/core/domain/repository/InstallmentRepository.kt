package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.model.InstallmentLink
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import kotlinx.coroutines.flow.Flow

interface InstallmentRepository {
    fun observePlans(ledgerId: String): Flow<List<InstallmentPlan>>

    suspend fun listPlans(ledgerId: String): List<InstallmentPlan>

    suspend fun getPlan(id: String): InstallmentPlan?

    /** 计划与各期必须一起写入（同一个事务），避免出现没有期数的计划。 */
    suspend fun savePlanWithPeriods(plan: InstallmentPlan, periods: List<InstallmentPeriod>): AppResult<Unit>

    suspend fun updatePlanStatus(planId: String, status: InstallmentPlanStatus, updatedAt: Long): AppResult<Unit>

    /** 更新计划本体（如部分提前还款后的本金、利率变更后的利率）。 */
    suspend fun savePlan(plan: InstallmentPlan): AppResult<Unit>

    suspend fun listPeriods(planId: String): List<InstallmentPeriod>

    /** 只更新已存在的期次（核销累计、重算尾部），不删除任何行。 */
    suspend fun savePeriods(periods: List<InstallmentPeriod>): AppResult<Unit>

    /** 账本下所有计划的各期（提醒计算要跨计划看，避免按计划逐次查询）。 */
    suspend fun listPeriodsForLedger(ledgerId: String): List<InstallmentPeriod>

    suspend fun getPeriod(planId: String, periodNo: Int): InstallmentPeriod?

    suspend fun markPeriodPaid(planId: String, periodNo: Int, txnId: String, paidAt: Long): AppResult<Unit>

    suspend fun softDeletePlan(planId: String, deletedAt: Long): AppResult<Unit>

    /** 记录一笔还款流水与计划/期次/口径的关联。 */
    suspend fun link(link: InstallmentLink): AppResult<Unit>

    suspend fun linkForTxn(txnId: String): InstallmentLink?

    suspend fun linksForPlan(planId: String): List<InstallmentLink>

    /** 账本下全部还款关联，供守卫与批量展示使用。 */
    suspend fun linksForLedger(ledgerId: String): List<InstallmentLink>
}
