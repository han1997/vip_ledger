package com.han1997.vipledger.feature.ai.ui.entry

import com.han1997.vipledger.core.domain.ai.AiSettings
import com.han1997.vipledger.core.domain.ai.VoiceFailure
import com.han1997.vipledger.core.domain.ai.VoiceInputState
import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.nlp.LlmParserClient
import com.han1997.vipledger.core.domain.nlp.NlpWarning
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.domain.nlp.ParsedField
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

/** 下拉选项：账户（[kind] 为 null）或分类（带收支方向，便于按类型过滤）。 */
data class AiOption(val id: String, val label: String, val kind: CategoryKind? = null)

/**
 * 一条「解析出来的候选草稿」在界面上的状态。
 *
 * 解析成功后，凡所需字段齐全（有账户）的候选会立即落成 `draft_status = PENDING` 的账目
 * （[txnId] 非空），因此它同时出现在既有「待确认」列表（M4 的 `DraftListScreen`）——两处读同一份
 * `draft_status = PENDING` 数据（PRD D2）。账户未识别（[accountId] 为 null）的候选先留在内存里，
 * 等用户选中账户再落库。
 *
 * [unresolved] 保留解析层「未能识别」的字段（PRD Requirement 8），[warnings] 透传解析告警
 * （如裸金额被丢弃）；[parsed] 是解析层原始产出，用于在确认时判定用户是否修正（PRD Requirement 4/7）。
 */
data class AiDraft(
    /** 稳定的列表 key；落库后不变，避免选中项随 id 变化跳动。 */
    val key: String,
    /** 已落成 PENDING 草稿的账目 id；未落库为 null。 */
    val txnId: String? = null,
    val amountCents: Long,
    val type: TransactionType,
    val occurredAt: Long,
    val accountId: String?,
    val toAccountId: String?,
    val categoryId: String?,
    val title: String,
    val remark: String,
    val source: TxnSource,
    /** 解析层未能确定的字段；必须由用户选择后才能确认（PRD Requirement 8）。 */
    val unresolved: Set<ParsedField>,
    val warnings: List<NlpWarning>,
    val parsed: ParsedEntry,
) {
    val persisted: Boolean get() = txnId != null

    /**
     * 真正需要用户处理、会阻塞确认的未识别字段。
     *
     * `DATE` / `TIME` 不在此列：日期由规则层负责且恒有值（无日期线索时默认今天，研究 §2.6），
     * 钟点为空由落库层用当前时刻，本页都不要求用户选择。
     */
    val blockingUnresolved: Set<ParsedField>
        get() = unresolved - setOf(ParsedField.DATE, ParsedField.TIME)

    /**
     * 确认入账的前置：所有阻塞字段已选、账户已识别，且转账/还款的转出与转入账户**不相同**。
     *
     * 最后一条不能省：确认走 [com.han1997.vipledger.core.domain.usecase.txn.ConfirmAiEntryUseCase]，
     * 它只搬字段、不跑 `TxnValidator`，若这里放行「账户 == 目标账户」就会把非法转账写成 CONFIRMED。
     * `withAccount` 已在源头改选账户时清空冲突的目标账户，此处是防御性兜底。
     */
    fun canConfirm(): Boolean =
        blockingUnresolved.isEmpty() &&
            accountId != null &&
            (!type.requiresTargetAccount || (toAccountId != null && toAccountId != accountId))

    /** 是否已具备落库条件（转账/还款还需要目标账户，[TxnValidator] 会拒绝缺目标账户的转账）。 */
    val canPersist: Boolean
        get() = accountId != null && (!type.requiresTargetAccount || toAccountId != null)

    /** 展示用的未识别字段（含中文字段名的判定交给界面层，这里只给字段枚举）。 */
    fun isUnresolved(field: ParsedField): Boolean = field in unresolved
}

/** 候选草稿 → 落库入参（`draft_status = PENDING`）；字段不足时返回 null，不静默补默认账户（PRD Requirement 8）。 */
fun AiDraft.toTxnDraft(ledgerId: String): TxnDraft? {
    val account = accountId ?: return null
    if (type.requiresTargetAccount && toAccountId == null) return null
    return TxnDraft(
        ledgerId = ledgerId,
        type = type,
        amount = Money.ofCents(amountCents),
        accountId = account,
        toAccountId = toAccountId,
        categoryId = categoryId,
        occurredAt = occurredAt,
        title = title,
        remark = remark,
        source = source,
        draftStatus = DraftStatus.PENDING,
    )
}

/** 解析结果 → 候选草稿（一句话可多笔，PRD Requirement 6：每个条目各成一条，不只取第一条）。 */
fun draftsOf(
    entries: List<ParsedEntry>,
    occurredAt: (LocalDate) -> Long,
    source: (ParsedEntry) -> TxnSource,
    keyOf: (Int) -> String,
): List<AiDraft> = entries.mapIndexed { index, entry ->
    AiDraft(
        key = keyOf(index),
        amountCents = entry.amount.cents,
        type = entry.type,
        occurredAt = occurredAt(entry.date),
        accountId = entry.accountId,
        toAccountId = entry.toAccountId,
        categoryId = entry.categoryId,
        title = entry.title,
        remark = entry.remark,
        source = source(entry),
        unresolved = entry.unresolved,
        warnings = entry.warnings,
        parsed = entry,
    )
}

/**
 * 来源判定：命中 `merchant_rule` 的条目由规则层直接定分类，标 [TxnSource.RULE]，其余标 [TxnSource.AI]。
 *
 * 管线（`NlEntryPipeline`）不逐条上报是第几级产出的，`matchedRuleId` 是唯一可靠、无需改动已验证
 * 管线的「规则层亲自定性」信号；纯规则层解析（无规则命中）也统一标 AI，误差只体现在审计字段上。
 */
fun sourceOf(entry: ParsedEntry): TxnSource =
    if (entry.matchedRuleId != null) TxnSource.RULE else TxnSource.AI

/** 账目类型对应的分类方向（转账/还款/调整记在「转账」方向）。 */
fun expectedCategoryKind(type: TransactionType): CategoryKind = when (type) {
    TransactionType.EXPENSE, TransactionType.REFUND -> CategoryKind.EXPENSE
    TransactionType.INCOME -> CategoryKind.INCOME
    TransactionType.TRANSFER, TransactionType.DEBT_REPAY, TransactionType.ADJUST -> CategoryKind.TRANSFER
}

/**
 * 隐私开关的落点（PRD D3）：开关关闭时对管线传 `null` 云端客户端，云端那一级**完全不可达**，
 * 不发起任何网络请求。这个转换是纯函数，便于单测钉住「关了就不联网」。
 */

/**
 * 改金额。`Long` 非空，赋值即代表「已有金额」，因此必须同时清掉 [ParsedField.AMOUNT]：
 * 云端解析不出金额时会把该条落成 `amount = 0` 且标未识别（`OpenAiCompatibleLlmParserClient`），
 * 用户补填后若标记不清，`blockingUnresolved` 会永久阻塞确认（与 [withType] 同类缺陷）。
 */
fun AiDraft.withAmountCents(cents: Long): AiDraft = copy(
    amountCents = cents,
    unresolved = unresolved - ParsedField.AMOUNT,
)

fun AiDraft.withTitle(title: String): AiDraft = copy(
    title = title,
    unresolved = if (title.isNotBlank()) unresolved - ParsedField.TITLE else unresolved + ParsedField.TITLE,
)

/**
 * 改选转出账户。转账/还款若改到与已选目标账户相同，则清空目标账户并重新标「未能识别」，
 * 避免落库出一条「转出 == 转入」的非法转账（[TxnValidator] 会拒绝，但确认路径不过校验）。
 */
fun AiDraft.withAccount(accountId: String): AiDraft {
    val conflictsWithTarget = type.requiresTargetAccount && toAccountId == accountId
    return copy(
        accountId = accountId,
        toAccountId = if (conflictsWithTarget) null else toAccountId,
        unresolved = unresolved - ParsedField.ACCOUNT +
            (if (conflictsWithTarget) setOf(ParsedField.TO_ACCOUNT) else emptySet()),
    )
}

/** 选择目标账户；等于转出账户的选择无效（界面已排除，这里再兜一层）。 */
fun AiDraft.withToAccount(accountId: String): AiDraft =
    if (accountId == this.accountId) {
        this
    } else {
        copy(toAccountId = accountId, unresolved = unresolved - ParsedField.TO_ACCOUNT)
    }

fun AiDraft.withCategory(categoryId: String): AiDraft = copy(
    categoryId = categoryId,
    unresolved = unresolved - ParsedField.CATEGORY,
)

/**
 * 切换类型：分类方向不一致时清空分类并重新要求选择；目标账户随之调整
 * （转账/还款需要目标账户，其它类型不允许目标账户）。
 */
fun AiDraft.withType(type: TransactionType, categories: List<AiOption>): AiDraft {
    val expected = expectedCategoryKind(type)
    val currentKind = categories.firstOrNull { it.id == categoryId }?.kind
    // 选定类型即清掉 TYPE 未识别标记（与 withTitle / withAccount 等同构）：`type` 非空，
    // 赋值即代表已确定方向。漏掉这一步会让 TYPE 永久阻塞确认（PRD Requirement 8 的对称面）。
    var next = copy(type = type, unresolved = unresolved - ParsedField.TYPE)

    if (next.categoryId != null && currentKind != null && currentKind != expected) {
        next = next.copy(categoryId = null, unresolved = next.unresolved + ParsedField.CATEGORY)
    }

    next = if (type.requiresTargetAccount) {
        if (next.toAccountId == null || next.toAccountId == next.accountId) {
            next.copy(toAccountId = null, unresolved = next.unresolved + ParsedField.TO_ACCOUNT)
        } else {
            next
        }
    } else {
        next.copy(toAccountId = null, unresolved = next.unresolved - ParsedField.TO_ACCOUNT)
    }
    return next
}

/** AI 记账页状态；只放数据与枚举，不放中文文案（见 .trellis/spec/android/ui.md）。 */
data class AiEntryUiState(
    val isLoading: Boolean = true,
    val ledgerId: String? = null,
    val input: String = "",
    /** 正在解析：7B 本地模型单次可达 65 s，必须有可见的进行中状态（PRD 硬约束 3）。 */
    val parsing: Boolean = false,
    val saving: Boolean = false,
    val drafts: List<AiDraft> = emptyList(),
    val accounts: List<AiOption> = emptyList(),
    val categories: List<AiOption> = emptyList(),
    val settings: AiSettings = AiSettings(),
    /**
     * 语音输入状态机（PRD「语音」切片）。
     * 按住开始、松开结束、上滑取消、失败可重试；离页停止并复位；取消/离页不提交文本。
     */
    val voice: VoiceInputState = VoiceInputState(),
    val notice: AiNotice? = null,
    val noticeDetail: String? = null,
) {
    /** 目标账户候选：排除转出账户本身。 */
    fun targetAccountOptions(sourceAccountId: String?): List<AiOption> =
        accounts.filter { it.id != sourceAccountId }

    /** 某类型可选的分类：方向一致才可选。 */
    fun categoryOptions(type: TransactionType): List<AiOption> {
        val expected = expectedCategoryKind(type)
        return categories.filter { it.kind == expected }
    }

    fun draftOf(key: String): AiDraft? = drafts.firstOrNull { it.key == key }
}

/** 一次性提示：界面层查表成文案。 */
enum class AiNotice { PARSED, NO_ENTRY, CONFIRMED, DISCARDED, SAVED, FAILED }

sealed interface AiAction {
    data class Input(val text: String) : AiAction

    data object Parse : AiAction

    data class ChangeAmount(val key: String, val cents: Long) : AiAction

    data class ChangeType(val key: String, val type: TransactionType) : AiAction

    data class ChangeAccount(val key: String, val accountId: String) : AiAction

    data class ChangeToAccount(val key: String, val accountId: String) : AiAction

    data class ChangeCategory(val key: String, val categoryId: String) : AiAction

    data class ChangeTitle(val key: String, val title: String) : AiAction

    data class Confirm(val key: String) : AiAction

    data object ConfirmAll : AiAction

    data class Discard(val key: String) : AiAction

    /** 设置表单改动（仅内存，不落库）。 */
    data class SettingsChanged(val settings: AiSettings) : AiAction

    /** 隐私开关：立即生效并落库（开关语义必须即时、无歧义）。 */
    data class ToggleNetwork(val enabled: Boolean) : AiAction

    /** 语音联网转写开关：立即生效并落库（与 [ToggleNetwork] 同构，PRD「语音」切片）。 */
    data class ToggleVoiceOnline(val enabled: Boolean) : AiAction

    /** 按住开始录音：进入 [VoiceInputState] 的 LISTENING 相位。 */
    data object VoiceStart : AiAction

    /** 松开结束录音：取最终转写结果并提交到输入框。 */
    data object VoiceStop : AiAction

    /** 上滑取消 / 离页：停止并复位，不提交文本。 */
    data object VoiceCancel : AiAction

    /** 麦克风权限被拒：进入失败相位并提示可改手工输入。 */
    data object VoicePermissionDenied : AiAction

    /** 失败后可重试：回到 IDLE，等待再次按住。 */
    data object VoiceRetry : AiAction

    data object SaveSettings : AiAction

    data object ConsumeNotice : AiAction
}
