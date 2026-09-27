package com.han1997.vipledger.feature.ledger.ui.recurring

import com.han1997.vipledger.core.domain.calculator.TxnValidator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.model.GenerateMode
import com.han1997.vipledger.core.model.MissedPolicy
import com.han1997.vipledger.core.model.RecurrenceEndType
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

/**
 * 周期规则编辑表单。
 *
 * 「能不能保存」「该显示哪些字段」这类规则写成纯函数放在这里（见 .trellis/spec/android/ui.md），
 * 界面只负责渲染，便于 JVM 单测覆盖。
 */
data class RecurringEditUiState(
    val isLoading: Boolean = true,
    val ledgerId: String = "",
    val ruleId: String? = null,
    val isEditing: Boolean = false,
    val name: String = "",
    val txnType: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val tagIds: Set<String> = emptySet(),
    val title: String = "",
    val remark: String = "",
    val freq: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val intervalInput: String = "1",
    val weekday: Int? = null,
    val monthDayInput: String = "",
    val monthInput: String = "",
    val startDate: LocalDate,
    val endType: RecurrenceEndType = RecurrenceEndType.NEVER,
    val endDate: LocalDate? = null,
    val maxOccurrencesInput: String = "",
    val generateMode: GenerateMode = GenerateMode.DRAFT,
    val missedPolicy: MissedPolicy = MissedPolicy.SKIP,
    val accounts: List<Account> = emptyList(),
    val categoryNodes: List<CategoryNode> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val error: RecurringEditError? = null,
    val saveFailureMessage: String? = null,
    val saved: Boolean = false,
)

enum class RecurringEditError {
    NAME_REQUIRED,
    INVALID_AMOUNT,
    ACCOUNT_REQUIRED,
    TARGET_REQUIRED,
    INVALID_INTERVAL,
    INVALID_END,
    SAVE_FAILED,
}

sealed interface RecurringEditAction {
    data class UpdateName(val text: String) : RecurringEditAction

    data class SelectType(val type: TransactionType) : RecurringEditAction

    data class UpdateAmount(val text: String) : RecurringEditAction

    data class SelectAccount(val accountId: String) : RecurringEditAction

    data class SelectTargetAccount(val accountId: String) : RecurringEditAction

    data class SelectCategory(val categoryId: String?) : RecurringEditAction

    data class ToggleTag(val tagId: String) : RecurringEditAction

    data class UpdateTitle(val text: String) : RecurringEditAction

    data class UpdateRemark(val text: String) : RecurringEditAction

    data class SelectFrequency(val freq: RecurrenceFrequency) : RecurringEditAction

    data class UpdateInterval(val text: String) : RecurringEditAction

    data class SelectWeekday(val weekday: Int?) : RecurringEditAction

    data class UpdateMonthDay(val text: String) : RecurringEditAction

    data class UpdateMonth(val text: String) : RecurringEditAction

    data class SelectStartDate(val date: LocalDate) : RecurringEditAction

    data class SelectEndType(val endType: RecurrenceEndType) : RecurringEditAction

    data class SelectEndDate(val date: LocalDate) : RecurringEditAction

    data class UpdateMaxOccurrences(val text: String) : RecurringEditAction

    data class SelectGenerateMode(val mode: GenerateMode) : RecurringEditAction

    data class SelectMissedPolicy(val policy: MissedPolicy) : RecurringEditAction

    data object Save : RecurringEditAction

    data object ConsumeError : RecurringEditAction
}

/** 间隔输入；空、非数字或小于 1 都算非法。 */
fun RecurringEditUiState.intervalValue(): Int? =
    intervalInput.trim().toIntOrNull()?.takeIf { it >= 1 }

/** 每月几号；留空表示沿用开始日。 */
fun RecurringEditUiState.monthDayValue(): Int? =
    monthDayInput.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()?.takeIf { it in 1..31 }

/** 月份；留空表示沿用开始日所在月。 */
fun RecurringEditUiState.monthValue(): Int? =
    monthInput.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()?.takeIf { it in 1..12 }

fun RecurringEditUiState.maxOccurrencesValue(): Int? =
    maxOccurrencesInput.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()?.takeIf { it >= 1 }

/** 界面层预检（领域层保存时还会再校验一次）。 */
fun RecurringEditUiState.validationError(): RecurringEditError? = when {
    name.isBlank() -> RecurringEditError.NAME_REQUIRED
    (Money.parse(amountInput)?.isPositive != true) ->
        RecurringEditError.INVALID_AMOUNT
    accountId == null -> RecurringEditError.ACCOUNT_REQUIRED
    txnType.requiresTargetAccount && toAccountId == null -> RecurringEditError.TARGET_REQUIRED
    intervalValue() == null -> RecurringEditError.INVALID_INTERVAL
    endType == RecurrenceEndType.UNTIL_DATE && endDate == null -> RecurringEditError.INVALID_END
    endType == RecurrenceEndType.COUNT && maxOccurrencesValue() == null -> RecurringEditError.INVALID_END
    else -> null
}

/** 当前类型可选的分类（一级 + 二级，方向必须匹配）。 */
fun RecurringEditUiState.categoryChoices(): List<Category> {
    val kind = TxnValidator.expectedKind(txnType)
    return categoryNodes.filter { it.category.kind == kind }
        .flatMap { node -> listOf(node.category) + node.children }
}

fun RecurringEditUiState.showsIntervalField(): Boolean = freq != RecurrenceFrequency.DAILY

fun RecurringEditUiState.showsWeekdayField(): Boolean = freq == RecurrenceFrequency.WEEKLY

fun RecurringEditUiState.showsMonthDayField(): Boolean = freq == RecurrenceFrequency.MONTHLY ||
    freq == RecurrenceFrequency.EVERY_N_MONTHS ||
    freq == RecurrenceFrequency.YEARLY

fun RecurringEditUiState.showsMonthField(): Boolean = freq == RecurrenceFrequency.YEARLY
