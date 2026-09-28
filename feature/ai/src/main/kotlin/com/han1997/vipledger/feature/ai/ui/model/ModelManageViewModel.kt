package com.han1997.vipledger.feature.ai.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.designsystem.format.categoryNameMap
import com.han1997.vipledger.core.designsystem.saf.safSuggestedFileName
import com.han1997.vipledger.core.domain.ai.AiSettingsRepository
import com.han1997.vipledger.core.domain.repository.MlSampleRepository
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.ai.model.ModelImportResult
import com.han1997.vipledger.feature.ai.model.OnDeviceModelManager
import com.han1997.vipledger.feature.ai.model.SampleTrainer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 模型管理页状态持有者（PRD Requirement 6）。
 *
 * 只依赖 `:core:domain` 的接缝/用例与 `:feature:ai` 内的模型管理器（architecture.md：不依赖 `:core:data`
 * 或其它 `:feature:*`）。展示当前模型版本 / 训练时间 / 样本数 / 标签数、被跳过的分类（D3#3 后果说明）、
 * 自动训练开关，并提供手动训练与模型导入导出。
 */
@HiltViewModel
class ModelManageViewModel @Inject constructor(
    private val manager: OnDeviceModelManager,
    private val sampleTrainer: SampleTrainer,
    private val sampleRepository: MlSampleRepository,
    private val settingsRepository: AiSettingsRepository,
    private val timeProvider: TimeProvider,
    observeCurrentLedger: ObserveCurrentLedgerUseCase,
    observeCategoryTree: ObserveCategoryTreeUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        ModelManageUiState(autoTrainEnabled = settingsRepository.settings().autoTrainEnabled),
    )
    val uiState: StateFlow<ModelManageUiState> = mutableState.asStateFlow()

    private var ledgerId: String? = null
    private var categoryNames: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            observeCurrentLedger()
                .map { it?.id }
                .distinctUntilChanged()
                .collectLatest { id ->
                    ledgerId = id
                    mutableState.update { it.copy(ledgerId = id) }
                    refresh()
                    if (id != null) {
                        observeCategoryTree(id, includeArchived = true).collect { nodes ->
                            categoryNames = nodes.categoryNameMap()
                            refresh()
                        }
                    }
                }
        }
    }

    fun onAction(action: ModelManageAction) {
        when (action) {
            is ModelManageAction.ToggleAutoTrain -> toggleAutoTrain(action.enabled)
            ModelManageAction.TrainNow -> trainNow()
            ModelManageAction.ExportSamples -> exportSamples()
            ModelManageAction.ExportModel -> exportModel()
            is ModelManageAction.ImportModel -> importModel(action.content)
            is ModelManageAction.ExportFinished -> mutableState.update {
                it.copy(notice = if (action.success) ModelNotice.EXPORTED else ModelNotice.EXPORT_FAILED)
            }
            ModelManageAction.ConsumeExport -> mutableState.update { it.copy(pendingExport = null) }
            ModelManageAction.ConsumeNotice -> mutableState.update { it.copy(notice = null, noticeDetail = null) }
        }
    }

    private fun toggleAutoTrain(enabled: Boolean) {
        // 开关语义必须即时、无歧义：与联网开关同款，立即落库。
        settingsRepository.save(settingsRepository.settings().copy(autoTrainEnabled = enabled))
        mutableState.update { it.copy(autoTrainEnabled = enabled, notice = ModelNotice.SAVED) }
    }

    private fun trainNow() {
        val id = ledgerId ?: return
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, notice = null, noticeDetail = null) }
        viewModelScope.launch {
            val untrained = runCatching { sampleRepository.listUntrained(id) }.getOrDefault(emptyList())
            val version = if (untrained.isEmpty()) null else sampleTrainer.trainAndMark(untrained)
            val notice = when {
                untrained.isEmpty() -> ModelNotice.TRAIN_NO_SAMPLES
                version != null -> ModelNotice.TRAINED
                else -> ModelNotice.TRAIN_FAILED
            }
            mutableState.update { it.copy(busy = false, notice = notice) }
            refresh()
        }
    }

    private fun exportSamples() {
        val id = ledgerId ?: return
        viewModelScope.launch {
            // 读样本失败（如数据库错误）与「确实没有样本」必须区分，否则失败会被误报成「没有可导出的样本」。
            val content = runCatching { manager.exportSamplesJsonl(id) }.getOrNull()
            when {
                content == null -> mutableState.update { it.copy(notice = ModelNotice.EXPORT_FAILED) }
                content.isBlank() -> mutableState.update { it.copy(notice = ModelNotice.EXPORT_NO_SAMPLES) }
                else -> mutableState.update {
                    it.copy(pendingExport = ModelExport(samplesFileName(), content, JSON_MIME))
                }
            }
        }
    }

    private fun exportModel() {
        val content = manager.exportModelText()
        if (content.isNullOrBlank()) {
            mutableState.update { it.copy(notice = ModelNotice.EXPORT_NO_MODEL) }
            return
        }
        mutableState.update {
            it.copy(pendingExport = ModelExport(modelFileName(), content, JSON_MIME))
        }
    }

    private fun importModel(content: String) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, notice = null, noticeDetail = null) }
        viewModelScope.launch {
            val notice = when (manager.importModel(content)) {
                is ModelImportResult.Imported -> ModelNotice.IMPORTED
                ModelImportResult.Invalid -> ModelNotice.IMPORT_INVALID
                ModelImportResult.PersistFailed -> ModelNotice.IMPORT_FAILED
            }
            mutableState.update { it.copy(busy = false, notice = notice) }
            refresh()
        }
    }

    private suspend fun refresh() {
        val id = ledgerId
        val summary = manager.summary()
        val total = id?.let { runCatching { sampleRepository.countByLedger(it) }.getOrDefault(0) } ?: 0
        val untrained = id?.let { runCatching { sampleRepository.listUntrained(it).size }.getOrDefault(0) } ?: 0
        mutableState.update { state ->
            state.copy(
                isLoading = false,
                autoTrainEnabled = settingsRepository.settings().autoTrainEnabled,
                hasModel = summary != null,
                modelVersion = summary?.modelVersion.orEmpty(),
                trainedAt = summary?.trainedAt ?: 0L,
                featureVersion = summary?.featureVersion ?: 0,
                modelSampleCount = summary?.sampleCount ?: 0,
                labelCount = summary?.labels?.size ?: 0,
                totalSamples = total,
                untrainedSamples = untrained,
                skippedClasses = skippedClassesOf(summary?.skippedClasses.orEmpty(), categoryNames),
            )
        }
    }

    private fun currentDate() = timeProvider.nowMillis().toLocalDate(timeProvider.zone)

    private fun samplesFileName(): String = safSuggestedFileName("ml-samples", "jsonl", currentDate())

    private fun modelFileName(): String = safSuggestedFileName("model", "json", currentDate())

    private companion object {
        const val JSON_MIME = "application/json"
    }
}
