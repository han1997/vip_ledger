package com.han1997.vipledger.feature.ai.ui.entry

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.AmountText
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.format.transactionTypeLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.domain.ai.AiSettings
import com.han1997.vipledger.core.domain.ai.VoiceFailure
import com.han1997.vipledger.core.domain.ai.VoicePhase
import com.han1997.vipledger.core.domain.ai.VoiceRecognitionMode
import com.han1997.vipledger.core.domain.nlp.Confidence
import com.han1997.vipledger.core.domain.nlp.NlpWarning
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.domain.nlp.ParsedField
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.ai.R
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** AI 自然语言记账页：一句话 → 待确认草稿 → 确认入账。 */
@Composable
fun AiEntryScreen(
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenModel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AiEntryContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onOpenRules = onOpenRules,
        onOpenModel = onOpenModel,
        modifier = modifier,
    )
}

@Composable
internal fun AiEntryContent(
    state: AiEntryUiState,
    onAction: (AiAction) -> Unit,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeText = state.notice?.let { aiNoticeText(it, state.noticeDetail) }

    // 离页停止并复位（PRD「语音」切片）：返回/跳到次级页时取消录音且不提交文本。
    DisposableEffect(Unit) {
        onDispose { onAction(AiAction.VoiceCancel) }
    }

    LaunchedEffect(state.notice, state.noticeDetail) {
        if (noticeText != null) {
            snackbarHostState.showSnackbar(noticeText)
            onAction(AiAction.ConsumeNotice)
        }
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(R.string.ai_title),
                onBack = onBack,
                actions = {
                    // 端侧模型管理入口（PRD Requirement 6）；与规则管理页一样收敛在 :feature:ai 内。
                    TextButton(onClick = onOpenModel) {
                        Text(stringResource(R.string.ai_model_open))
                    }
                    // 商户规则管理入口（PRD Requirement 9）；规则管理页收敛在 :feature:ai 内（PRD D2）。
                    TextButton(onClick = onOpenRules) {
                        Text(stringResource(R.string.ai_rule_open))
                    }
                },
            )
        },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "input") {
                InputSection(state = state, onAction = onAction)
            }

            if (state.parsing) {
                item(key = "parsing") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(R.string.ai_parsing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.readingImages) {
                item(key = "reading-images") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = state.imageProgress?.let {
                                stringResource(R.string.ai_reading_images, it.done, it.total)
                            } ?: stringResource(R.string.ai_reading_images, 0, 0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "settings") {
                SettingsSection(state = state, onAction = onAction)
            }

            if (state.drafts.isEmpty()) {
                item(key = "empty") {
                    EmptyState(message = stringResource(R.string.ai_empty_drafts))
                }
            } else {
                item(key = "drafts-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.ai_drafts_title, state.drafts.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state.drafts.count { it.canConfirm() } > 1) {
                            OutlinedButton(
                                onClick = { onAction(AiAction.ConfirmAll) },
                                enabled = !state.saving,
                            ) {
                                Text(stringResource(R.string.ai_confirm_all))
                            }
                        }
                    }
                }
                items(items = state.drafts, key = { it.key }) { draft ->
                    DraftCard(draft = draft, state = state, onAction = onAction, saving = state.saving)
                }
            }
        }
    }
}

@Composable
private fun InputSection(state: AiEntryUiState, onAction: (AiAction) -> Unit) {
    val context = LocalContext.current
    var hasRecordPermission by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRecordPermission = granted
        if (!granted) onAction(AiAction.VoicePermissionDenied)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LabeledTextField(
            label = stringResource(R.string.ai_input_label),
            value = state.input,
            onValueChange = { onAction(AiAction.Input(it)) },
            placeholder = stringResource(R.string.ai_input_hint),
            singleLine = false,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { onAction(AiAction.Parse) },
                enabled = !state.parsing && !state.voice.isListening &&
                    state.input.isNotBlank() && state.ledgerId != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.ai_parse))
            }
            PickImagesButton(
                enabled = !state.parsing && !state.readingImages && state.ledgerId != null,
                onPick = { onAction(AiAction.ImagesPicked(it)) },
            )
            VoiceHoldButton(
                state = state,
                hasPermission = hasRecordPermission,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onAction = onAction,
            )
        }
        VoiceStatus(state = state, onAction = onAction)
    }
}

/**
 * 主动选图（Photo Picker，P5 图片切片）：多图逐张 OCR 后进入同一解析；
 * 始终先确认，不自动入账，原图不保存。
 */
@Composable
private fun PickImagesButton(
    enabled: Boolean,
    onPick: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK_IMAGES),
    ) { uris ->
        if (uris.isNotEmpty()) onPick(uris.map { it.toString() })
    }
    val pickContentDescription = stringResource(R.string.ai_pick_images)
    Box(
        modifier = modifier
            .size(VipLedgerTokens.spacing(14))
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled) {
                pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            .semantics {
                role = Role.Button
                contentDescription = pickContentDescription
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = VipLedgerTokens.AlphaDisabled)
            },
            modifier = Modifier.size(VipLedgerTokens.IconMedium),
        )
    }
}

/** 检查 `RECORD_AUDIO` 是否已授权（同意过就不再弹窗，拒绝后仍可手工输入）。 */
private fun hasRecordAudioPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

/**
 * 按住说话按钮：按住开始、松开结束、上滑取消（PRD「语音」切片）。
 *
 * 手势处理在 [pointerInput] 里；[state.voice] 只负责视觉相位（聆听/取消/失败），
 * 手势状态（是否已上滑取消）是本地瞬时值，不进 UiState。
 */
@Composable
private fun VoiceHoldButton(
    state: AiEntryUiState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onAction: (AiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var cancelArmed by remember { mutableStateOf(false) }
    val cancelDistance = with(LocalDensity.current) { 64.dp.toPx() }
    val listening = state.voice.isListening
    val enabled = !state.parsing && state.ledgerId != null
    val holdContentDescription = stringResource(R.string.ai_voice_hold)
    val listeningContentDescription = stringResource(R.string.ai_voice_listening_description)

    Box(
        modifier = modifier
            .size(VipLedgerTokens.spacing(14))
            .clip(CircleShape)
            .background(
                when {
                    cancelArmed -> MaterialTheme.colorScheme.errorContainer
                    listening -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .pointerInput(enabled, hasPermission) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val startY = down.position.y
                    var armCancel = false
                    if (!hasPermission) {
                        onRequestPermission()
                        // 等抬起：本次不开始录音，等用户授权后再次按住。
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                        return@awaitEachGesture
                    }
                    onAction(AiAction.VoiceStart)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.changedToUp()) break
                        if (change.position.y < startY - cancelDistance) armCancel = true
                        cancelArmed = armCancel
                        change.consume()
                    }
                    cancelArmed = false
                    onAction(if (armCancel) AiAction.VoiceCancel else AiAction.VoiceStop)
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = if (listening) listeningContentDescription else holdContentDescription
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = when {
                cancelArmed -> MaterialTheme.colorScheme.onErrorContainer
                listening -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(VipLedgerTokens.IconMedium),
        )
    }
}

/** 语音状态行：聆听中的实时转写与模式标注（可能联网时显式标注），以及失败提示与重试。 */
@Composable
private fun VoiceStatus(state: AiEntryUiState, onAction: (AiAction) -> Unit) {
    val voice = state.voice
    when (voice.phase) {
        VoicePhase.IDLE -> Unit

        VoicePhase.LISTENING -> {
            Text(
                text = stringResource(R.string.ai_voice_listening),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            voice.mode?.let { mode ->
                Text(
                    text = if (mode == VoiceRecognitionMode.MAYBE_ONLINE) {
                        stringResource(R.string.ai_voice_mode_maybe_online)
                    } else {
                        stringResource(R.string.ai_voice_mode_on_device)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mode == VoiceRecognitionMode.MAYBE_ONLINE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (voice.partialText.isNotBlank()) {
                Text(
                    text = voice.partialText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        VoicePhase.FAILED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = voiceFailureText(voice.failure),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onAction(AiAction.VoiceRetry) }) {
                    Text(stringResource(R.string.ai_voice_retry))
                }
            }
        }
    }
}

@Composable
private fun voiceFailureText(failure: VoiceFailure?): String = stringResource(
    when (failure) {
        VoiceFailure.NO_SERVICE -> R.string.ai_voice_failed_no_service
        VoiceFailure.PERMISSION_DENIED -> R.string.ai_voice_failed_permission
        VoiceFailure.TIMEOUT -> R.string.ai_voice_failed_timeout
        VoiceFailure.NO_RESULT -> R.string.ai_voice_failed_no_result
        VoiceFailure.UNKNOWN, null -> R.string.ai_voice_failed_unknown
    },
)

@Composable
private fun SettingsSection(state: AiEntryUiState, onAction: (AiAction) -> Unit) {
    val settings = state.settings
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(stringResource(R.string.ai_section_settings))
        // 隐私开关：文案必须说明开启后会把账目文字发往该端点（ai-ml.md 隐私红线）。
        ToggleRow(
            label = stringResource(R.string.ai_setting_network),
            checked = settings.networkEnabled,
            onCheckedChange = { onAction(AiAction.ToggleNetwork(it)) },
        )
        Text(
            text = stringResource(R.string.ai_setting_network_disclosure),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 语音联网转写独立开关（PRD「语音」切片，默认关）：端侧不可用且关闭时提示改手工输入。
        ToggleRow(
            label = stringResource(R.string.ai_setting_voice_online),
            checked = settings.voiceOnlineTranscription,
            onCheckedChange = { onAction(AiAction.ToggleVoiceOnline(it)) },
        )
        Text(
            text = stringResource(R.string.ai_setting_voice_online_disclosure),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabeledTextField(
            label = stringResource(R.string.ai_setting_base_url),
            value = settings.baseUrl,
            onValueChange = { onAction(AiAction.SettingsChanged(settings.copy(baseUrl = it))) },
            placeholder = stringResource(R.string.ai_setting_base_url_hint),
        )
        LabeledTextField(
            label = stringResource(R.string.ai_setting_api_key),
            value = settings.apiKey,
            onValueChange = { onAction(AiAction.SettingsChanged(settings.copy(apiKey = it))) },
            keyboardType = KeyboardType.Password,
        )
        LabeledTextField(
            label = stringResource(R.string.ai_setting_model),
            value = settings.model,
            onValueChange = { onAction(AiAction.SettingsChanged(settings.copy(model = it))) },
        )
        Button(onClick = { onAction(AiAction.SaveSettings) }) {
            Text(stringResource(R.string.ai_setting_save))
        }
    }
}

@Composable
private fun DraftCard(
    draft: AiDraft,
    state: AiEntryUiState,
    onAction: (AiAction) -> Unit,
    saving: Boolean,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (draft.source == TxnSource.RULE) R.string.ai_source_rule else R.string.ai_source_ai,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AmountText(amount = Money.ofCents(draft.amountCents))
            }

            AmountField(
                draft = draft,
                unresolved = draft.isUnresolved(ParsedField.AMOUNT),
                onValueChange = { onAction(AiAction.ChangeAmount(draft.key, it)) },
            )

            // 类型：规则层没有线索时要求用户选择（未能识别 → 明示）
            FieldLabel(
                label = stringResource(R.string.ai_field_type),
                unresolved = draft.isUnresolved(ParsedField.TYPE),
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = draft.type == type,
                        onClick = { onAction(AiAction.ChangeType(draft.key, type)) },
                        label = { Text(transactionTypeLabel(type)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Dropdown(
                label = stringResource(R.string.ai_field_account),
                options = state.accounts,
                selectedId = draft.accountId,
                unresolved = draft.isUnresolved(ParsedField.ACCOUNT),
                onSelect = { onAction(AiAction.ChangeAccount(draft.key, it)) },
            )

            if (draft.type.requiresTargetAccount) {
                Dropdown(
                    label = stringResource(R.string.ai_field_to_account),
                    options = state.targetAccountOptions(draft.accountId),
                    selectedId = draft.toAccountId,
                    unresolved = draft.isUnresolved(ParsedField.TO_ACCOUNT),
                    onSelect = { onAction(AiAction.ChangeToAccount(draft.key, it)) },
                )
            }

            Dropdown(
                label = stringResource(R.string.ai_field_category),
                options = state.categoryOptions(draft.type),
                selectedId = draft.categoryId,
                unresolved = draft.isUnresolved(ParsedField.CATEGORY),
                onSelect = { onAction(AiAction.ChangeCategory(draft.key, it)) },
            )

            LabeledTextField(
                label = stringResource(R.string.ai_field_title),
                value = draft.title,
                onValueChange = { onAction(AiAction.ChangeTitle(draft.key, it)) },
                isError = draft.isUnresolved(ParsedField.TITLE),
            )

            // 日期默认由规则层给出（无日期线索时回退今天），用户在本页不可改。但云端日期不可信且
            // 规则层也没识别出日期时会标 DATE 未识别，必须显式呈现，不能静默当作确定日期（PRD Req 8 /
            // Open Question「在草稿上可见」）。
            val dateFieldLabel = stringResource(R.string.ai_field_date)
            val dateUnresolved = draft.isUnresolved(ParsedField.DATE)
            Text(
                text = (if (dateUnresolved) stringResource(R.string.ai_unresolved_field, dateFieldLabel) else dateFieldLabel) +
                    "：" + fullDateLabel(draft.occurredAt.toLocalDate(zone)),
                style = MaterialTheme.typography.bodySmall,
                color = if (dateUnresolved) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (draft.blockingUnresolved.isNotEmpty()) {
                // `map` 是 inline，可在其中调用 @Composable；`joinToString` 的 transform 不是 inline，
                // 先取到文案列表再拼接（见 ui.md「LaunchedEffect 里不能调 @Composable」同类坑）。
                val unresolvedLabels = draft.blockingUnresolved.map { fieldLabel(it) }
                Text(
                    text = stringResource(
                        R.string.ai_unresolved_field,
                        unresolvedLabels.joinToString("、"),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            draft.warnings.forEach { warning ->
                Text(
                    text = "⚠ " + warningLabel(warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (draft.suspectedDuplicate) {
                Text(
                    text = "⚠ " + stringResource(R.string.ai_suspected_duplicate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onAction(AiAction.Confirm(draft.key)) },
                    enabled = draft.canConfirm() && !saving,
                ) {
                    Text(stringResource(R.string.ai_confirm))
                }
                OutlinedButton(
                    onClick = { onAction(AiAction.Discard(draft.key)) },
                    enabled = !saving,
                ) {
                    Text(stringResource(R.string.ai_discard))
                }
            }
        }
    }
}

@Composable
private fun AmountField(draft: AiDraft, unresolved: Boolean, onValueChange: (Long) -> Unit) {
    var text by rememberSaveable(draft.key) { mutableStateOf(Money.ofCents(draft.amountCents).toPlainString()) }
    LabeledTextField(
        label = stringResource(R.string.ai_field_amount),
        value = text,
        onValueChange = { input ->
            text = input
            Money.parse(input)?.let { onValueChange(it.cents) }
        },
        keyboardType = KeyboardType.Decimal,
        isError = unresolved,
    )
}

@Composable
private fun FieldLabel(label: String, unresolved: Boolean) {
    Text(
        text = if (unresolved) stringResource(R.string.ai_unresolved_field, label) else label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (unresolved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Dropdown(
    label: String,
    options: List<AiOption>,
    selectedId: String?,
    unresolved: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.id == selectedId }?.label
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(label = label, unresolved = unresolved && selectedId == null)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(
                    text = selectedLabel ?: stringResource(R.string.ai_unresolved_field, label),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            onSelect(option.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun fieldLabel(field: ParsedField): String = stringResource(
    when (field) {
        ParsedField.AMOUNT -> R.string.ai_unresolved_amount
        ParsedField.TYPE -> R.string.ai_unresolved_type
        ParsedField.ACCOUNT -> R.string.ai_unresolved_account
        ParsedField.TO_ACCOUNT -> R.string.ai_unresolved_to_account
        ParsedField.CATEGORY -> R.string.ai_unresolved_category
        ParsedField.TITLE -> R.string.ai_unresolved_title
        ParsedField.DATE -> R.string.ai_unresolved_date
        ParsedField.TIME -> R.string.ai_unresolved_time
    },
)

@Composable
private fun warningLabel(warning: NlpWarning): String = stringResource(
    when (warning) {
        NlpWarning.AMBIGUOUS_TYPE_CUE -> R.string.ai_warning_ambiguous_type_cue
        NlpWarning.MISSING_TARGET_ACCOUNT -> R.string.ai_warning_missing_target_account
        NlpWarning.RATIO_NOT_RESOLVED -> R.string.ai_warning_ratio_not_resolved
        NlpWarning.LOW_CONFIDENCE_AMOUNT -> R.string.ai_warning_low_confidence_amount
        NlpWarning.BARE_AMOUNT_DROPPED -> R.string.ai_warning_bare_amount_dropped
    },
)

@Composable
private fun aiNoticeText(notice: AiNotice, detail: String?): String = when (notice) {
    AiNotice.PARSED -> stringResource(R.string.ai_notice_parsed, detail?.toIntOrNull() ?: 0)
    AiNotice.NO_ENTRY -> stringResource(R.string.ai_notice_no_entry)
    AiNotice.IMAGES_NO_TEXT -> stringResource(R.string.ai_notice_images_no_text)
    AiNotice.IMAGES_PARTIAL -> stringResource(R.string.ai_notice_images_partial, detail?.toIntOrNull() ?: 0)
    AiNotice.CONFIRMED -> stringResource(R.string.ai_notice_confirmed)
    AiNotice.DISCARDED -> stringResource(R.string.ai_notice_discarded)
    AiNotice.SAVED -> stringResource(R.string.ai_notice_saved)
    AiNotice.FAILED -> if (!detail.isNullOrBlank()) detail else stringResource(R.string.ai_notice_failed)
}

/** 单批最多选入的图片数（与 AiEntryViewModel.MAX_IMAGES_PER_BATCH 保持一致）。 */
private const val MAX_PICK_IMAGES = 5

@Preview(showBackground = true)
@Composable
private fun AiEntryContentPreview() {
    val entry = ParsedEntry(
        amount = Money.ofCents(3500),
        type = TransactionType.EXPENSE,
        date = LocalDate(2026, 9, 27),
        time = null,
        accountId = "a_wx",
        toAccountId = null,
        categoryId = null,
        title = "午饭",
        remark = "",
        confidence = Confidence.MEDIUM,
        amountHasUnit = true,
        unresolved = setOf(ParsedField.CATEGORY),
        warnings = listOf(NlpWarning.BARE_AMOUNT_DROPPED),
    )
    VipLedgerTheme {
        AiEntryContent(
            state = AiEntryUiState(
                isLoading = false,
                ledgerId = "l1",
                input = "昨天午饭 35 块 微信",
                drafts = draftsOf(
                    entries = listOf(entry, entry.copy(amount = Money.ofCents(2800), title = "打车")),
                    occurredAt = { 1_790_524_800_000L },
                    source = { TxnSource.AI },
                    keyOf = { "draft-$it" },
                ),
                accounts = listOf(AiOption("a_wx", "微信")),
                categories = listOf(AiOption("c_food", "餐饮", com.han1997.vipledger.core.model.CategoryKind.EXPENSE)),
                settings = AiSettings(baseUrl = "http://192.168.10.4:11434/v1"),
            ),
            onAction = {},
            onBack = {},
            onOpenRules = {},
            onOpenModel = {},
        )
    }
}
