package com.han1997.vipledger.feature.ai.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.getOrNull
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.ai.AiSettingsRepository
import com.han1997.vipledger.core.domain.ai.ImageTextReader
import com.han1997.vipledger.core.domain.ai.SpeechTranscriber
import com.han1997.vipledger.core.domain.ai.VoiceEvent
import com.han1997.vipledger.core.domain.ai.VoiceFailure
import com.han1997.vipledger.core.domain.ai.VoiceInputState
import com.han1997.vipledger.core.domain.ai.VoiceRecognitionMode
import com.han1997.vipledger.core.domain.ai.cloudClientFor
import com.han1997.vipledger.core.domain.ai.reduce
import com.han1997.vipledger.core.domain.calculator.TxnValidator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.domain.nlp.CategoryClassifier
import com.han1997.vipledger.core.domain.nlp.LlmParserClient
import com.han1997.vipledger.core.domain.nlp.NlEntryPipeline
import com.han1997.vipledger.core.domain.repository.MerchantRuleRepository
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.txn.ConfirmAiEntryUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.DiscardDraftUseCase
import com.han1997.vipledger.core.domain.usecase.txn.GetTxnUseCase
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import com.han1997.vipledger.core.model.time.toLocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * AI 记账页状态持有者。
 *
 * 只依赖 `:core:domain` 的用例 / 接缝（云端客户端 [LlmParserClient]、分类器 [CategoryClassifier]、
 * 配置 [AiSettingsRepository]），不依赖 `:core:data` 或任何其它 `:feature:*`（architecture.md 硬规则）。
 *
 * 解析成功后，可落库的候选立即写成 `draft_status = PENDING` 的账目（PRD Requirement 2），因此也出现在
 * 既有「待确认」列表；用户确认或修正后经 [ConfirmAiEntryUseCase] 入账，修正同时沉淀训练样本与
 * `source = LEARNED` 的商户规则（PRD Requirement 4 / 7）。
 */
@HiltViewModel
class AiEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val timeProvider: TimeProvider,
    private val settingsRepository: AiSettingsRepository,
    private val merchantRuleRepository: MerchantRuleRepository,
    private val llmClient: LlmParserClient,
    private val classifier: CategoryClassifier,
    /** 语音转写接缝（PRD「语音」切片）；实现是系统 SpeechRecognizer，接口在 :core:domain。 */
    private val transcriber: SpeechTranscriber,
    /** 图片文本识别接缝（P5 图片切片）；实现是 ML Kit 本机中文识别。 */
    private val imageTextReader: ImageTextReader,
    observeCurrentLedger: ObserveCurrentLedgerUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategoryTree: ObserveCategoryTreeUseCase,
    private val createTxn: CreateTxnUseCase,
    private val confirmAiEntry: ConfirmAiEntryUseCase,
    private val discardDraft: DiscardDraftUseCase,
    private val getTxn: GetTxnUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow(AiEntryUiState(settings = settingsRepository.settings()))
    val uiState: StateFlow<AiEntryUiState> = mutableState.asStateFlow()

    /** 解析入参（管线需要真实账户/分类/规则候选）。 */
    private var accounts: List<Account> = emptyList()
    private var categories: List<Category> = emptyList()
    private var keyCounter: Int = 0

    /**
     * 系统分享带入的图片（`ai/home?shared=...` 导航参数，MainActivity 提取 `EXTRA_STREAM`）。
     * init 即消费并从 SavedStateHandle 移除，避免旋转/恢复后重复入草稿；等账本就绪后逐张识别。
     */
    private val pendingSharedImages: List<String> = run {
        val raw = savedStateHandle.get<String>(SHARED_IMAGES_ARG).orEmpty()
        savedStateHandle.remove<String>(SHARED_IMAGES_ARG)
        if (raw.isBlank()) emptyList() else raw.split('\n').filter { it.isNotBlank() }
    }
    private var sharedImagesDispatched = false

    /**
     * 语音转写回调 → 状态机。
     *
     * 只有 [SpeechTranscriber.Listener.onResult]（松开结束的最终结果）会把文本提交到输入框；
     * 取消 / 离页 / 失败都不会产生 onResult，因此「取消/离页不提交文本」由事件语义保证。
     */
    private val voiceListener = object : SpeechTranscriber.Listener {
        override fun onReady(mode: VoiceRecognitionMode) {
            if (!mutableState.value.voice.isListening) return
            mutableState.update { it.copy(voice = it.voice.reduce(VoiceEvent.Ready(mode))) }
        }

        override fun onPartialResult(text: String) {
            if (!mutableState.value.voice.isListening) return
            mutableState.update { it.copy(voice = it.voice.reduce(VoiceEvent.Partial(text))) }
        }

        override fun onResult(text: String) {
            if (!mutableState.value.voice.isListening) return
            mutableState.update {
                it.copy(input = text, voice = it.voice.reduce(VoiceEvent.Succeeded))
            }
        }

        override fun onError(failure: VoiceFailure) {
            if (!mutableState.value.voice.isListening) return
            mutableState.update { it.copy(voice = it.voice.reduce(VoiceEvent.Failed(failure))) }
        }
    }

    init {
        transcriber.setListener(voiceListener)
        viewModelScope.launch {
            observeCurrentLedger().collectLatest { ledger ->
                val ledgerId = ledger?.id
                mutableState.update { it.copy(isLoading = false, ledgerId = ledgerId) }
                if (ledgerId == null) {
                    accounts = emptyList()
                    categories = emptyList()
                    mutableState.update { it.copy(accounts = emptyList(), categories = emptyList()) }
                    return@collectLatest
                }
                combine(
                    observeAccounts(ledgerId, includeArchived = true),
                    observeCategoryTree(ledgerId, includeArchived = true),
                ) { a, c -> a to c }.collect { (accountList, categoryNodes) ->
                    accounts = accountList
                    categories = flatten(categoryNodes)
                    mutableState.update {
                        it.copy(
                            accounts = accountList.map { account -> AiOption(account.id, account.name) },
                            categories = categories.map { category ->
                                AiOption(category.id, category.name, category.kind)
                            },
                        )
                    }
                    // 账本/账户就绪后一次性消费系统分享带入的图片（不自动入账，仍走候选确认）。
                    if (!sharedImagesDispatched && pendingSharedImages.isNotEmpty()) {
                        sharedImagesDispatched = true
                        readImages(pendingSharedImages.take(MAX_IMAGES_PER_BATCH))
                    }
                }
            }
        }
    }

    fun onAction(action: AiAction) {
        when (action) {
            is AiAction.Input -> mutableState.update { it.copy(input = action.text) }
            AiAction.Parse -> parse()
            is AiAction.ChangeAmount -> edit(action.key) { it.withAmountCents(action.cents) }
            is AiAction.ChangeType -> edit(action.key) { it.withType(action.type, categories.map(::toOption)) }
            is AiAction.ChangeAccount -> edit(action.key) { it.withAccount(action.accountId) }
            is AiAction.ChangeToAccount -> edit(action.key) { it.withToAccount(action.accountId) }
            is AiAction.ChangeCategory -> edit(action.key) { it.withCategory(action.categoryId) }
            is AiAction.ChangeTitle -> edit(action.key) { it.withTitle(action.title) }
            is AiAction.Confirm -> confirm(action.key)
            AiAction.ConfirmAll -> confirmAll()
            is AiAction.Discard -> discard(action.key)
            is AiAction.SettingsChanged -> mutableState.update { it.copy(settings = action.settings) }
            is AiAction.ToggleNetwork -> toggleNetwork(action.enabled)
            is AiAction.ToggleVoiceOnline -> toggleVoiceOnline(action.enabled)
            AiAction.VoiceStart -> startVoice()
            AiAction.VoiceStop -> stopVoice()
            AiAction.VoiceCancel -> cancelVoice()
            AiAction.VoicePermissionDenied -> mutableState.update {
                it.copy(voice = it.voice.reduce(VoiceEvent.Failed(VoiceFailure.PERMISSION_DENIED)))
            }
            AiAction.VoiceRetry -> mutableState.update {
                it.copy(voice = it.voice.reduce(VoiceEvent.Reset))
            }
            is AiAction.ImagesPicked -> readImages(action.uris.take(MAX_IMAGES_PER_BATCH))
            AiAction.SaveSettings -> saveSettings()
            AiAction.ConsumeNotice -> mutableState.update { it.copy(notice = null, noticeDetail = null) }
        }
    }

    /**
     * 语音转写回调 → 状态机。
     *
     * 只有 [SpeechTranscriber.Listener.onResult]（松开结束的最终结果）会把文本提交到输入框；
     * 取消 / 离页 / 失败都不会产生 onResult，因此「取消/离页不提交文本」由事件语义保证。
     */
    private fun startVoice() {
        if (mutableState.value.voice.isListening) return
        mutableState.update { it.copy(voice = it.voice.reduce(VoiceEvent.Start)) }
        transcriber.start()
    }

    private fun stopVoice() {
        if (!mutableState.value.voice.isListening) return
        transcriber.stop()
    }

    private fun cancelVoice() {
        transcriber.cancel()
        mutableState.update { it.copy(voice = VoiceInputState()) }
    }

    override fun onCleared() {
        transcriber.setListener(null)
        transcriber.cancel()
        super.onCleared()
    }

    private fun parse() {
        val text = mutableState.value.input.trim()
        val ledgerId = mutableState.value.ledgerId
        if (text.isBlank() || ledgerId == null || mutableState.value.parsing) return

        mutableState.update { it.copy(parsing = true, notice = null, noticeDetail = null) }
        viewModelScope.launch {
            val rules = loadRules(ledgerId)
            val count = parseIntoDrafts(text, ledgerId, rules)
            mutableState.update {
                it.copy(
                    parsing = false,
                    notice = if (count == 0) AiNotice.NO_ENTRY else AiNotice.PARSED,
                    noticeDetail = count.toString(),
                )
            }
        }
    }

    /** 当前账本的启用规则；查询失败按空规则处理，不阻塞解析。 */
    private suspend fun loadRules(ledgerId: String): List<MerchantRule> =
        runCatching { merchantRuleRepository.listEnabledByPriority(ledgerId) }
            .getOrDefault(emptyList())

    /**
     * 单段文本 → 候选草稿（文字 / 语音 / 图片四种输入共用的同一解析与确认链路）。
     * 追加到现有草稿列表并尝试落库，返回解析出的笔数（0 = 没识别出条目）。
     */
    private suspend fun parseIntoDrafts(text: String, ledgerId: String, rules: List<MerchantRule>): Int {
        val today = timeProvider.nowMillis().toLocalDate(timeProvider.zone)
        val pipeline = NlEntryPipeline(
            accounts = accounts,
            categories = categories,
            merchantRules = rules,
            classifier = classifier,
            // 隐私开关关闭时传 null，云端那一级完全不可达（PRD D3）。
            cloud = cloudClientFor(mutableState.value.settings, llmClient),
        )
        val entries = pipeline.parse(text, today).entries
        if (entries.isEmpty()) return 0
        val drafts = draftsOf(
            entries = entries,
            occurredAt = { date -> date.startOfDayEpochMillis(timeProvider.zone) },
            source = ::sourceOf,
            keyOf = { nextKey() },
        ).map { persistIfPossible(ledgerId, it) }
        mutableState.update { it.copy(drafts = it.drafts + drafts) }
        return drafts.size
    }

    /**
     * 多图逐张识别（P5 图片切片）：每张图片的 OCR 文本进入与文字相同的解析，产出候选草稿；
     * 始终先确认、不自动入账；原图不保存。失败不中断后续图片，最后归并成一次性提示。
     */
    private fun readImages(uris: List<String>) {
        val ledgerId = mutableState.value.ledgerId
        if (uris.isEmpty() || ledgerId == null || mutableState.value.readingImages || mutableState.value.parsing) return

        mutableState.update {
            it.copy(readingImages = true, imageProgress = ImageProgress(0, uris.size), notice = null, noticeDetail = null)
        }
        viewModelScope.launch {
            val rules = loadRules(ledgerId)
            var parsedTotal = 0
            var failedCount = 0
            uris.forEachIndexed { index, uri ->
                val text = imageTextReader.readText(uri).getOrNull()
                if (text.isNullOrBlank()) {
                    failedCount++
                } else {
                    parsedTotal += parseIntoDrafts(text, ledgerId, rules)
                }
                mutableState.update { it.copy(imageProgress = ImageProgress(index + 1, uris.size)) }
            }
            mutableState.update { state ->
                state.copy(
                    readingImages = false,
                    imageProgress = null,
                    notice = imageParseNotice(parsedTotal, failedCount),
                    noticeDetail = if (failedCount > 0) failedCount.toString() else parsedTotal.toString(),
                )
            }
        }
    }

    /** 编辑一条候选：先更新界面状态，再尝试把它落成 PENDING 草稿（字段不足时不落库）。 */
    private fun edit(key: String, transform: (AiDraft) -> AiDraft) {
        val draft = mutableState.value.draftOf(key) ?: return
        val next = transform(draft)
        mutableState.update { state ->
            state.copy(drafts = state.drafts.map { if (it.key == key) next else it })
        }
        persistIfPossibleAsync(mutableState.value.ledgerId, next)
    }

    /**
     * 解析产出即落库：字段齐全时立即写入一条 `PENDING` 草稿，返回带 [AiDraft.txnId] 的副本；
     * 缺账户/目标账户时原样返回，等用户补齐后再落库。
     */
    private suspend fun persistIfPossible(ledgerId: String, draft: AiDraft): AiDraft {
        val txnDraft = draft.toTxnDraft(ledgerId) ?: return draft
        val created = createTxn(txnDraft)
        return created.getOrNull()?.let { draft.copy(txnId = it.id) } ?: draft
    }

    private fun persistIfPossibleAsync(ledgerId: String?, draft: AiDraft) {
        if (ledgerId == null || draft.persisted || !draft.canPersist) return
        viewModelScope.launch {
            val next = persistIfPossible(ledgerId, draft)
            if (next.persisted) {
                mutableState.update { state ->
                    state.copy(drafts = state.drafts.map { if (it.key == draft.key) next else it })
                }
            }
        }
    }

    private fun confirm(key: String) {
        val draft = mutableState.value.draftOf(key) ?: return
        if (!draft.canConfirm() || mutableState.value.saving) return
        mutableState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = confirmInternal(draft)
            mutableState.update { state ->
                state.copy(
                    saving = false,
                    drafts = if (result.isSuccess) state.drafts.filterNot { it.key == key } else state.drafts,
                    notice = if (result.isSuccess) AiNotice.CONFIRMED else AiNotice.FAILED,
                    noticeDetail = result.failureMessage(),
                )
            }
        }
    }

    private fun confirmAll() {
        val keys = mutableState.value.drafts.filter { it.canConfirm() }.map { it.key }
        if (keys.isEmpty() || mutableState.value.saving) return
        mutableState.update { it.copy(saving = true) }
        viewModelScope.launch {
            var failed: String? = null
            var confirmed = 0
            keys.forEach { key ->
                val draft = mutableState.value.draftOf(key) ?: return@forEach
                val result = confirmInternal(draft)
                if (result.isSuccess) {
                    confirmed++
                    mutableState.update { state -> state.copy(drafts = state.drafts.filterNot { it.key == key }) }
                } else if (failed == null) {
                    failed = result.failureMessage()
                }
            }
            mutableState.update { state ->
                state.copy(
                    saving = false,
                    notice = if (failed == null && confirmed > 0) AiNotice.CONFIRMED else AiNotice.FAILED,
                    noticeDetail = failed,
                )
            }
        }
    }

    /** 落库并在同一路径上处理修正（[ConfirmAiEntryUseCase] 内部同一事务）。 */
    private suspend fun confirmInternal(draft: AiDraft): AppResult<*> {
        val ledgerId = mutableState.value.ledgerId
            ?: return AppResult.failure(AppError.Unknown("账本未就绪"))
        val txnId = draft.txnId ?: run {
            val created = createTxn(draft.toTxnDraft(ledgerId) ?: return AppResult.failure(AppError.Validation("草稿缺少账户")))
            created.getOrNull()?.id ?: return created
        }
        val existing = getTxn(txnId)
            ?: return AppResult.failure(AppError.NotFound("账目不存在"))
        val updated = existing.copy(
            type = draft.type,
            amount = Money.ofCents(draft.amountCents),
            accountId = draft.accountId ?: existing.accountId,
            toAccountId = draft.toAccountId,
            categoryId = draft.categoryId,
            occurredAt = draft.occurredAt,
            title = draft.title,
            remark = draft.remark,
        )
        // 用户在草稿上可改金额/类型/账户/分类，而 ConfirmAiEntryUseCase 只搬字段、不跑校验，
        // 因此这里先跑业务校验（金额 > 0、账户存在、转账目标合法、分类方向一致），
        // 用 AppError.message 直接反馈给用户，避免把非法账目写成 CONFIRMED。
        TxnValidator.validate(updated, accounts, categories)?.let { return AppResult.failure(it) }
        return confirmAiEntry(updated, draft.parsed)
    }

    private fun discard(key: String) {
        val draft = mutableState.value.draftOf(key) ?: return
        mutableState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = draft.txnId?.let { discardDraft(it) }
            mutableState.update { state ->
                state.copy(
                    saving = false,
                    drafts = state.drafts.filterNot { it.key == key },
                    notice = if (result == null || result.isSuccess) AiNotice.DISCARDED else AiNotice.FAILED,
                    noticeDetail = result?.failureMessage(),
                )
            }
        }
    }

    private fun toggleNetwork(enabled: Boolean) {
        val settings = mutableState.value.settings.copy(networkEnabled = enabled)
        settingsRepository.save(settings)
        mutableState.update { it.copy(settings = settings) }
    }

    private fun toggleVoiceOnline(enabled: Boolean) {
        val settings = mutableState.value.settings.copy(voiceOnlineTranscription = enabled)
        settingsRepository.save(settings)
        mutableState.update { it.copy(settings = settings) }
    }

    private fun saveSettings() {
        settingsRepository.save(mutableState.value.settings)
        mutableState.update { it.copy(notice = AiNotice.SAVED) }
    }

    private fun nextKey(): String = "draft-${keyCounter++}"

    private fun flatten(nodes: List<CategoryNode>): List<Category> =
        nodes.flatMap { node -> listOf(node.category) + node.children }

    private fun toOption(category: Category): AiOption = AiOption(category.id, category.name, category.kind)

    private companion object {
        /** 系统分享图片的导航参数名（`ai/home?shared=...`）。 */
        const val SHARED_IMAGES_ARG = "shared"

        /** 单批最多识别的图片数：逐张 OCR 耗时可控，也避免一次塞入过多草稿。 */
        const val MAX_IMAGES_PER_BATCH = 5
    }
}

private fun AppResult<*>.failureMessage(): String? = (this as? AppResult.Failure)?.error?.message
