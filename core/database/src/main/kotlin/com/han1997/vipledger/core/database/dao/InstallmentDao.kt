package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.han1997.vipledger.core.database.entity.InstallmentPeriodEntity
import com.han1997.vipledger.core.database.entity.InstallmentPlanEntity
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentPlanDao {

    @Query("SELECT * FROM installment_plan WHERE ledger_id = :ledgerId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observePlans(ledgerId: String): Flow<List<InstallmentPlanEntity>>

    @Query("SELECT * FROM installment_plan WHERE ledger_id = :ledgerId AND deleted_at IS NULL ORDER BY created_at DESC")
    suspend fun listPlans(ledgerId: String): List<InstallmentPlanEntity>

    @Query("SELECT * FROM installment_plan WHERE id = :id LIMIT 1")
    suspend fun getPlan(id: String): InstallmentPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(plan: InstallmentPlanEntity)

    @Query("UPDATE installment_plan SET status = :status, updated_at = :updatedAt WHERE id = :planId")
    suspend fun updatePlanStatus(planId: String, status: InstallmentPlanStatus, updatedAt: Long)

    @Query("UPDATE installment_plan SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :planId")
    suspend fun softDeletePlan(planId: String, deletedAt: Long)

    @Query("DELETE FROM installment_period WHERE plan_id = :planId")
    suspend fun deletePeriodsOfPlan(planId: String)
}

@Dao
interface InstallmentPeriodDao {

    @Query("SELECT * FROM installment_period WHERE plan_id = :planId ORDER BY period_no ASC")
    suspend fun listPeriods(planId: String): List<InstallmentPeriodEntity>

    @Query(
        """
        SELECT * FROM installment_period
        WHERE plan_id IN (
            SELECT id FROM installment_plan
            WHERE ledger_id = :ledgerId AND deleted_at IS NULL
        )
        ORDER BY due_date ASC, period_no ASC
        """,
    )
    suspend fun listPeriodsForLedger(ledgerId: String): List<InstallmentPeriodEntity>

    @Query("SELECT * FROM installment_period WHERE plan_id = :planId AND period_no = :periodNo LIMIT 1")
    suspend fun getPeriod(planId: String, periodNo: Int): InstallmentPeriodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(periods: List<InstallmentPeriodEntity>)

    @Query(
        """
        UPDATE installment_period
        SET status = :status, paid_txn_id = :txnId, paid_at = :paidAt
        WHERE plan_id = :planId AND period_no = :periodNo
        """,
    )
    suspend fun markPaid(
        planId: String,
        periodNo: Int,
        status: InstallmentPeriodStatus,
        txnId: String,
        paidAt: Long,
    )
}
