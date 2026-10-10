package com.han1997.vipledger.feature.ledger.ui.loan

import com.han1997.vipledger.core.domain.calculator.InstallmentAmounts
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentOrigin
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
    val origin: InstallmentOrigin = InstallmentOrigin.EXISTING,
    val disbursementAccountId: String? = null,
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

    /** 放款到账只能用可用的非负债账户，且不能是负债账户本身。 */
    val disbursementAccounts: List<Account>
        get() = accounts.filter { it.isUsable && !it.isDebt && it.id != accountId }

    val showsDisbursementAccount: Boolean get() = origin == InstallmentOrigin.DISBURSEMENT
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
    DISBURSEMENT_ACCOUNT_REQUIRED,
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

    data class SelectOrigin(val origin: InstallmentOrigin) : LoanEditAction

    data class SelectDisbursementAccount(val accountId: String) : LoanEditAction

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

/**
 * 自定义逐期本金/利息/费：每期一行，每行恰好三个数字（本金,利息,费）。
 *
 * 只在多行输入时生效（单行仍按 [customTotalsValue] 的旧口径解析，保持兼容）；
 * 若任一行不是三个数字则返回 null，由界面回退到旧口径（每期总额）。
 */
fun LoanEditUiState.customAmountsValue(): List<InstallmentAmounts>? {
    val lines = customTotalsInput.split(Regex("[\\r\\n;]+")).map { it.trim() }.filter { it.isNotBlank() }
    if (lines.size < 2) return null
    val parsed = ArrayList<InstallmentAmounts>(lines.size)
    lines.forEach { line ->
        val tokens = line.split(Regex("[^0-9.]+")).filter { it.isNotBlank() }
        if (tokens.size != 3) return null
        val principal = Money.parse(tokens[0]) ?: return null
        val interest = Money.parse(tokens[1]) ?: return null
        val fee = Money.parse(tokens[2]) ?: return null
        if (principal.isNegative || interest.isNegative || fee.isNegative) return null
        parsed += InstallmentAmounts(principal = principal, interest = interest, fee = fee)
    }
    return parsed
}

/** CUSTOM 的每期金额校验：逐期口径要求本金之和等于计划本金；旧口径只要求数量一致。 */
internal fun LoanEditUiState.customScheduleError(): Boolean {
    val amounts = customAmountsValue()
    if (amounts != null) {
        val principal = principalValue() ?: return true
        return amounts.size != periodsValue() || Money.sum(amounts.map { it.principal }) != principal
    }
    return customTotalsValue()?.size != periodsValue()
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
    showsDisbursementAccount && disbursementAccountId == null ->
        LoanEditError.DISBURSEMENT_ACCOUNT_REQUIRED
    method == InstallmentMethod.CUSTOM && customScheduleError() -> LoanEditError.INVALID_CUSTOM
    else -> null
}

fun LoanEditUiState.showsCustomTotalsField(): Boolean = method == InstallmentMethod.CUSTOM
