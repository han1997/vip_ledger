package com.han1997.vipledger.feature.ledger.ui.loan

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

/**
 * 新建分期计划表单。
 *
 * 「能不能保存」这类规则写成纯函数放在这里（见 .trellis/spec/android/ui.md），
 * 界面只负责渲染，便于 JVM 单测覆盖。
 */
data class LoanEditUiState(
    val isLoading: Boolean = true,
    val ledgerId: String = "",
    val name: String = "",
    val accountId: String? = null,
    val method: InstallmentMethod = InstallmentMethod.EQUAL_INSTALLMENT,
    val principalInput: String = "",
    val periodsInput: String = "",
    val annualRateInput: String = "",
    val upfrontFeeInput: String = "",
    val perPeriodFeeInput: String = "",
    val customTotalsInput: String = "",
    val firstDueDate: LocalDate,
    val dueDayInput: String = "",
    val remark: String = "",
    val accounts: List<Account> = emptyList(),
    val error: LoanEditError? = null,
    val saveFailureMessage: String? = null,
    val saved: Boolean = false,
) {
    /** 只有负债账户能建分期。 */
    val debtAccounts: List<Account> get() = accounts.filter { it.type.isDebt }
}

enum class LoanEditError {
    NAME_REQUIRED,
    ACCOUNT_REQUIRED,
    INVALID_PRINCIPAL,
    INVALID_PERIODS,
    INVALID_RATE,
    INVALID_FEE,
    INVALID_DUE_DAY,
    INVALID_CUSTOM,
    SAVE_FAILED,
}

sealed interface LoanEditAction {
    data class UpdateName(val text: String) : LoanEditAction

    data class SelectAccount(val accountId: String) : LoanEditAction

    data class SelectMethod(val method: InstallmentMethod) : LoanEditAction

    data class UpdatePrincipal(val text: String) : LoanEditAction

    data class UpdatePeriods(val text: String) : LoanEditAction

    data class UpdateAnnualRate(val text: String) : LoanEditAction

    data class UpdateUpfrontFee(val text: String) : LoanEditAction

    data class UpdatePerPeriodFee(val text: String) : LoanEditAction

    data class UpdateCustomTotals(val text: String) : LoanEditAction

    data class SelectFirstDueDate(val date: LocalDate) : LoanEditAction

    data class UpdateDueDay(val text: String) : LoanEditAction

    data class UpdateRemark(val text: String) : LoanEditAction

    data object Save : LoanEditAction

    data object ConsumeError : LoanEditAction
}

fun LoanEditUiState.principalValue(): Money? =
    Money.parse(principalInput)?.takeIf { it.isPositive }

fun LoanEditUiState.periodsValue(): Int? =
    periodsInput.trim().toIntOrNull()?.takeIf { it >= 1 }

/** 年化利率按「%」录入；留空等于 0。内部换算成基点（1% = 100 bp）。 */
fun LoanEditUiState.annualRateBpValue(): Int? {
    if (annualRateInput.isBlank()) return 0
    val percent = Money.parse(annualRateInput) ?: return null
    if (percent.isNegative) return null
    return percent.cents.toInt()
}

fun LoanEditUiState.upfrontFeeValue(): Money? =
    if (upfrontFeeInput.isBlank()) Money.ZERO else Money.parse(upfrontFeeInput)?.takeIf { !it.isNegative }

fun LoanEditUiState.perPeriodFeeValue(): Money? =
    if (perPeriodFeeInput.isBlank()) Money.ZERO else Money.parse(perPeriodFeeInput)?.takeIf { !it.isNegative }

fun LoanEditUiState.dueDayValue(): Int? =
    dueDayInput.trim().toIntOrNull()?.takeIf { it in 1..28 }

/** 自定义每期的还款总额；用逗号 / 空格 / 换行分隔。 */
fun LoanEditUiState.customTotalsValue(): List<Money>? {
    val parts = customTotalsInput.split(Regex("[^0-9.]+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    val totals = ArrayList<Money>(parts.size)
    parts.forEach { part ->
        val money = Money.parse(part) ?: return null
        if (!money.isPositive) return null
        totals += money
    }
    return totals
}

/** 界面层预检（领域层保存时还会再校验一次）。 */
fun LoanEditUiState.validationError(): LoanEditError? = when {
    name.isBlank() -> LoanEditError.NAME_REQUIRED
    accountId == null -> LoanEditError.ACCOUNT_REQUIRED
    principalValue() == null -> LoanEditError.INVALID_PRINCIPAL
    periodsValue() == null -> LoanEditError.INVALID_PERIODS
    annualRateBpValue() == null -> LoanEditError.INVALID_RATE
    upfrontFeeValue() == null || perPeriodFeeValue() == null -> LoanEditError.INVALID_FEE
    dueDayInput.isNotBlank() && dueDayValue() == null -> LoanEditError.INVALID_DUE_DAY
    method == InstallmentMethod.CUSTOM && customTotalsValue()?.size != periodsValue() ->
        LoanEditError.INVALID_CUSTOM
    else -> null
}

fun LoanEditUiState.showsCustomTotalsField(): Boolean = method == InstallmentMethod.CUSTOM
