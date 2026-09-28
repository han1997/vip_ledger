package com.han1997.vipledger.feature.ai.ui.model

import com.han1997.vipledger.feature.ai.model.AutoTrainTrigger
import com.han1997.vipledger.ml.classifier.train.TrainingConfig

/**
 * 被跳过（累计样本数低于阈值）的分类，在模型管理页上展示（PRD D3#3 后果说明）。
 *
 * 缺名字时 [name] 为空串，界面回退显示 id——「哪些分类被跳过」必须可见，
 * 否则用户只看到分类器没反应而无从判断。
 */
data class SkippedClass(
    val categoryId: String,
    val name: String,
    val sampleCount: Int,
)

/** 待 SAF 导出的文件：文件名 + 内容 + MIME。VM 只产出内容与名字，**不碰 `Uri`**（ui.md）。 */
data class ModelExport(
    val fileName: String,
    val content: String,
    val mime: String,
)

/**
 * 模型管理页状态（PRD Requirement 6）。
 *
 * 只放数据与枚举，不放中文文案、不放 `Context`/`Uri`（`.trellis/spec/android/ui.md`）。
 */
data class ModelManageUiState(
    val isLoading: Boolean = true,
    val ledgerId: String? = null,
    val hasModel: Boolean = false,
    val modelVersion: String = "",
    /** 上次训练时间（epoch 毫秒）。 */
    val trainedAt: Long = 0L,
    val featureVersion: Int = 0,
    /** 当前模型累计训练样本数（`LinearModel.sampleCount`）。 */
    val modelSampleCount: Int = 0,
    val labelCount: Int = 0,
    /** 该账本样本总数（含未训练）。 */
    val totalSamples: Int = 0,
    /** 尚未纳入训练的样本数；达到 [autoTrainThreshold] 时下一轮修正会触发自动训练。 */
    val untrainedSamples: Int = 0,
    val autoTrainEnabled: Boolean = false,
    /** 自动训练触发线（ARCHITECTURE §7）：未训练样本累计到它才自动训练。与 [minSamplesPerClass] 不同。 */
    val autoTrainThreshold: Int = AutoTrainTrigger.AUTO_TRAIN_THRESHOLD,
    /**
     * 类别不平衡抑制阈值（PRD D3#3）：某个分类的**累计**样本数低于它的，该分类既不参与训练、
     * 也不进标签表（因此不出建议）。取值直连 [TrainingConfig] 的默认值，页面文案随源码走，
     * 不会被写死成另一个数（曾误用 [autoTrainThreshold] 的 30 描述本规则）。
     */
    val minSamplesPerClass: Int = TrainingConfig.DEFAULT_MIN_SAMPLES_PER_CLASS,
    val skippedClasses: List<SkippedClass> = emptyList(),
    val busy: Boolean = false,
    val pendingExport: ModelExport? = null,
    val notice: ModelNotice? = null,
    /** 失败原因（来自 AppError.message）；无说明时为 null。 */
    val noticeDetail: String? = null,
) {
    val hasSamplesToExport: Boolean get() = totalSamples > 0
}

/** 一次性提示：界面层查表成文案。 */
enum class ModelNotice {
    TRAINED,
    TRAIN_NO_SAMPLES,
    TRAIN_FAILED,
    IMPORTED,
    IMPORT_INVALID,
    IMPORT_FAILED,
    EXPORTED,
    EXPORT_FAILED,
    EXPORT_NO_SAMPLES,
    EXPORT_NO_MODEL,
    SAVED,
    FAILED,
}

sealed interface ModelManageAction {
    /** 自动训练开关：立即生效并落库（语义必须即时、无歧义，同联网开关）。 */
    data class ToggleAutoTrain(val enabled: Boolean) : ModelManageAction

    data object TrainNow : ModelManageAction

    data object ExportSamples : ModelManageAction

    data object ExportModel : ModelManageAction

    /** 用户在 SAF 选中的模型文件内容（读盘已成功）。 */
    data class ImportModel(val content: String) : ModelManageAction

    data class ExportFinished(val success: Boolean) : ModelManageAction

    data object ConsumeExport : ModelManageAction

    data object ConsumeNotice : ModelManageAction
}

/** 被跳过分类的展示映射：按 id 稳定排序，缺名字回退成 id（界面再决定显示口径）。 */
fun skippedClassesOf(pendingCounts: Map<String, Int>, names: Map<String, String>): List<SkippedClass> =
    pendingCounts.entries
        .sortedBy { it.key }
        .map { (categoryId, count) -> SkippedClass(categoryId, names[categoryId].orEmpty(), count) }
