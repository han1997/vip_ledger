package com.han1997.vipledger.feature.ledger.ui.autobook

import com.han1997.vipledger.core.domain.autobook.*
import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money

/**「识别记录与待确认」区的状态筛选（P6：完善候选/已入账/忽略入口）。 */
enum class RecordFilter { CANDIDATES, POSTED, IGNORED }

data class AutoBookUiState(
    val config: AutoBookConfig = AutoBookConfig(), val ledgers: List<Ledger> = emptyList(),
    val accounts: List<Account> = emptyList(), val bindings: List<AccountBinding> = emptyList(),
    val candidates: List<AutoCandidate> = emptyList(), val txns: List<Txn> = emptyList(),
    val runtime: AutoBookRuntime = AutoBookRuntime(), val permissions: CapturePermissions = CapturePermissions(),
    val form: CandidateForm? = null, val busy: Boolean = false, val failed: Boolean = false,
    val bindingApp: PaymentApp = PaymentApp.WECHAT, val bindingToken: String = "", val bindingAccount: String = "",
    val recordFilter: RecordFilter = RecordFilter.CANDIDATES,
) {
    val hasUsableLedger: Boolean get() = ledgers.any { it.id == config.ledgerId && !it.isArchived && it.currencyCode == "CNY" }
    val canBind: Boolean get() = !busy && hasUsableLedger && bindingToken.isNotBlank() && accounts.any {
        it.id == bindingAccount && it.ledgerId == config.ledgerId && it.isUsable
    }
    val bindingTokens: List<String> get() = candidates.filter { it.app == bindingApp && it.ledgerId == config.ledgerId }
        .mapNotNull { it.facts.accountToken?.takeIf(String::isNotBlank) }.distinct()

    /** 筛选 chip 计数：已关联既有账目（LINKED）计入「已入账」。 */
    val pendingCount: Int get() = candidates.count { it.status == CandidateStatus.PENDING }
    val postedCount: Int get() = candidates.count { it.status == CandidateStatus.POSTED || it.status == CandidateStatus.LINKED }
    val ignoredCount: Int get() = candidates.count { it.status == CandidateStatus.IGNORED }

    /** 当前筛选下的记录列表（纯函数，便于单测钉住分组口径）。 */
    fun filteredCandidates(): List<AutoCandidate> = when (recordFilter) {
        RecordFilter.CANDIDATES -> candidates.filter { it.status == CandidateStatus.PENDING }
        RecordFilter.POSTED -> candidates.filter { it.status == CandidateStatus.POSTED || it.status == CandidateStatus.LINKED }
        RecordFilter.IGNORED -> candidates.filter { it.status == CandidateStatus.IGNORED }
    }
}
data class CandidateForm(
    val id: String, val type: TransactionType, val amount: String, val accountId: String,
    val targetId: String = "", val title: String, val refundId: String = "", val linkId: String = "",
) {
    fun confirmation(): CandidateConfirmation? {
        val cents = Money.parse(amount)?.takeIf { it.isPositive }?.cents ?: return null
        if (accountId.isBlank() || (type.requiresTargetAccount && (targetId.isBlank() || targetId == accountId))) return null
        return CandidateConfirmation(type, cents, accountId, targetId.takeIf { type.requiresTargetAccount }, title,
            refundId.takeIf { type == TransactionType.REFUND && it.isNotBlank() })
    }
}
sealed interface AutoBookAction {
    data class Config(val value: AutoBookConfig) : AutoBookAction
    data class BindingApp(val value: PaymentApp) : AutoBookAction
    data class BindingToken(val value: String) : AutoBookAction
    data class BindingAccount(val value: String) : AutoBookAction
    data object Bind : AutoBookAction
    data class Unbind(val value: AccountBinding) : AutoBookAction
    data class Select(val value: AutoCandidate) : AutoBookAction
    data class Edit(val value: CandidateForm) : AutoBookAction
    data object Confirm : AutoBookAction
    data object Link : AutoBookAction
    data class Ignore(val id: String) : AutoBookAction

    /** 已忽略候选重新确认（置回 PENDING，凭据不变）。 */
    data class Reopen(val id: String) : AutoBookAction

    /** 切换「识别记录」状态筛选。 */
    data class SetRecordFilter(val value: RecordFilter) : AutoBookAction
    data class OpenSetting(val value: CaptureSetting) : AutoBookAction
    data object Refresh : AutoBookAction
    data object DismissError : AutoBookAction
}
