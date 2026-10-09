package com.han1997.vipledger.core.domain.autobook

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.TransactionType
import kotlinx.coroutines.flow.Flow

enum class PaymentApp(val packageName: String) {
    WECHAT("com.tencent.mm"), ALIPAY("com.eg.android.AlipayGphone"),
    JD("com.jingdong.app.mall"), PDD("com.xunmeng.pinduoduo"), TAOBAO("com.taobao.taobao");
    companion object { fun fromPackage(value: String) = entries.firstOrNull { it.packageName == value } }
}
enum class CaptureSource { NOTIFICATION, ACCESSIBILITY, OCR }
enum class CandidateStatus { PENDING, POSTED, IGNORED, LINKED }
enum class ReviewReason { NONE, UNVERIFIED, AMOUNT, ACCOUNT, DIRECTION, REFUND, DUPLICATE, NOT_SUCCESS, STORAGE, TIME }

data class AutoBookConfig(
    val ledgerId: String = "",
    val enabled: Boolean = false,
    val ocrEnabled: Boolean = false,
    val enabledApps: Set<PaymentApp> = PaymentApp.entries.toSet(),
    /** User validates real samples before enabling automatic posting for a platform. */
    val verifiedApps: Set<PaymentApp> = emptySet(),
)
data class AccountBinding(val ledgerId: String, val app: PaymentApp, val token: String, val accountId: String)
data class PaymentObservation(
    val app: PaymentApp, val source: CaptureSource, val eventKey: String,
    val observedAt: Long, val text: String,
)
data class PaymentFacts(
    val type: TransactionType?, val amountCents: Long?, val merchant: String,
    val accountToken: String?, val targetToken: String?, val externalId: String?,
    val originalId: String?, val success: Boolean, val reason: ReviewReason,
    val paidAt: Long? = null,
)
data class AutoCandidate(
    val id: String, val ledgerId: String, val app: PaymentApp, val source: CaptureSource,
    val occurredAt: Long, val facts: PaymentFacts, val accountId: String? = null,
    val targetAccountId: String? = null, val status: CandidateStatus = CandidateStatus.PENDING,
    val reason: ReviewReason = ReviewReason.NONE, val txnId: String? = null,
    val refundOfTxnId: String? = null, val duplicateTxnId: String? = null,
)
data class CandidateConfirmation(
    val type: TransactionType, val amountCents: Long, val accountId: String,
    val targetAccountId: String? = null, val title: String,
    val refundOfTxnId: String? = null,
)
data class AutoBookRuntime(
    val notificationConnected: Boolean = false, val accessibilityConnected: Boolean = false,
    val lastEventAt: Long? = null, val lastReadAt: Long? = null, val issue: RuntimeIssue? = null,
    val lastRecordAt: Long? = null, val lastCaptureOutcome: CaptureOutcome? = null,
    val lastAmountRecognized: Boolean? = null,
)
enum class RuntimeIssue { DISCONNECTED, CAPTURE_BLOCKED, OCR_FAILED, STORAGE_FAILED }
enum class CaptureOutcome { PROCESSING, UPDATED, UNCHANGED, FAILED }

interface AutoBookRepository {
    fun observeConfig(): Flow<AutoBookConfig>
    suspend fun config(): AutoBookConfig
    suspend fun saveConfig(config: AutoBookConfig): AppResult<Unit>
    fun observeBindings(ledgerId: String): Flow<List<AccountBinding>>
    suspend fun bind(binding: AccountBinding): AppResult<Unit>
    suspend fun removeBinding(binding: AccountBinding): AppResult<Unit>
    fun observeCandidates(ledgerId: String): Flow<List<AutoCandidate>>
    suspend fun capture(observation: PaymentObservation): AppResult<AutoCandidate?>
    suspend fun confirm(id: String, input: CandidateConfirmation): AppResult<Txn>
    suspend fun ignore(id: String): AppResult<Unit>
    suspend fun link(id: String, txnId: String): AppResult<Unit>
}
