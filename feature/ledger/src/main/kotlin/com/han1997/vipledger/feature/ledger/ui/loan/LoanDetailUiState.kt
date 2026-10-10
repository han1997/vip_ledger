package com.han1997.vipledger.feature.ledger.ui.loan

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.usecase.loan.InstallmentPlanDetail
import com.han1997.vipledger.core.domain.usecase.loan.LegacyInstallmentRepayment
import com.han1997.vipledger.core.model.money.Money

/** 分期计划详情状态：计划 + 各期明细 + 还款/结清/提前还款/利率变更/旧口径对话框。 */
data class LoanDetailUiState(
    val isLoading: Boolean = true,
    val planId: String = "",
    val ledgerId: String = "",
    val detail: InstallmentPlanDetail? = null,
    val accountNames: Map<String, String> = emptyMap(),
    /** 可选的还款来源账户（已排除负债账户本身）。 */
    val repayAccounts: List<Account> = emptyList(),
    val repayPeriodNo: Int? = null,
    val repayAccountId: String? = null,
    val repayAmountInput: String = "",
    val settleOpen: Boolean = false,
    val prepayOpen: Boolean = false,
    val prepayAmountInput: String = "",
    val rateOpen: Boolean = false,
    val rateFromPeriodInput: String = "",
    val rateValueInput: String = "",
    val legacy: List<LegacyInstallmentRepayment> = emptyList(),
    val legacyOpen: Boolean = false,
    val legacyCorrectedCount: Int = 0,
    val busy: Boolean = false,
    val notice: LoanNotice? = null,
    val noticeDetail: String? = null,
) {
    /** 完全未还的期次：可提前还款、可改利率。 */
    val untouchedPeriods: List<InstallmentPeriod>
        get() = detail?.periods?.filter { it.isUntouched }.orEmpty()

    val repayPeriod: InstallmentPeriod?
        get() = detail?.periods?.firstOrNull { it.periodNo == repayPeriodNo }

    val canSettle: Boolean
        get() {
            val plan = detail ?: return false
            return !plan.isClosed && plan.periods.any { period -> !period.isPaid }
        }

    val canPrepay: Boolean
        get() {
            val plan = detail ?: return false
            return !plan.isClosed &&
                plan.periods.none { it.isPartiallyPaid } &&
                untouchedPeriods.isNotEmpty()
        }

    val canChangeRate: Boolean
        get() {
            val plan = detail ?: return false
            return !plan.isClosed && untouchedPeriods.isNotEmpty()
        }

    val hasLegacy: Boolean get() = legacy.isNotEmpty()
}

sealed interface LoanDetailAction {
    data class StartRepay(val periodNo: Int) : LoanDetailAction

    data class SelectRepayAccount(val accountId: String) : LoanDetailAction

    data class UpdateRepayAmount(val text: String) : LoanDetailAction

    data object ConfirmRepay : LoanDetailAction

    data object CancelRepay : LoanDetailAction

    data object OpenSettle : LoanDetailAction

    data object CancelSettle : LoanDetailAction

    data object ConfirmSettle : LoanDetailAction

    data object OpenPrepay : LoanDetailAction

    data object CancelPrepay : LoanDetailAction

    data class UpdatePrepayAmount(val text: String) : LoanDetailAction

    data object ConfirmPrepay : LoanDetailAction

    data object OpenRate : LoanDetailAction

    data object CancelRate : LoanDetailAction

    data class UpdateRateFrom(val text: String) : LoanDetailAction

    data class UpdateRateValue(val text: String) : LoanDetailAction

    data object ConfirmRate : LoanDetailAction

    data object OpenLegacy : LoanDetailAction

    data object CancelLegacy : LoanDetailAction

    data object ConfirmLegacy : LoanDetailAction

    data object ConsumeNotice : LoanDetailAction
}

/** 留空表示还清本期；否则解析出的金额。 */
fun LoanDetailUiState.repayAmountValue(): Money? =
    if (repayAmountInput.isBlank()) null else Money.parse(repayAmountInput)

fun LoanDetailUiState.repayAmountInvalid(): Boolean {
    if (repayAmountInput.isBlank()) return false
    val parsed = Money.parse(repayAmountInput) ?: return true
    val remaining = repayPeriod?.remainingTotal ?: return true
    return !parsed.isPositive || parsed > remaining
}

fun LoanDetailUiState.prepayAmountValue(): Money? =
    Money.parse(prepayAmountInput)?.takeIf { it.isPositive }

fun LoanDetailUiState.prepayAmountInvalid(): Boolean {
    val parsed = prepayAmountValue() ?: return true
    val remainingPrincipal = detail?.remainingPrincipal ?: return true
    return parsed >= remainingPrincipal
}

fun LoanDetailUiState.rateFromPeriodValue(): Int? =
    rateFromPeriodInput.trim().toIntOrNull()

fun LoanDetailUiState.rateFromPeriodInvalid(): Boolean {
    val value = rateFromPeriodValue() ?: return true
    return untouchedPeriods.none { it.periodNo == value }
}

/** 年化利率按「%」录入，内部换算成基点（1% = 100 bp）。 */
fun LoanDetailUiState.rateValueBp(): Int? {
    val percent = Money.parse(rateValueInput) ?: return null
    if (percent.isNegative) return null
    return percent.cents.toInt()
}
