package com.han1997.vipledger.feature.ai.model

import com.han1997.vipledger.ml.classifier.model.LinearModel
import com.han1997.vipledger.ml.classifier.model.ModelCodec
import com.han1997.vipledger.ml.classifier.model.ModelFormatException

/**
 * 模型导入的结果（PRD Requirement 6）：界面据枚举查 `strings.xml` 给可见文案。
 *
 * 不把 [ModelFormatException] 的原始 message 透出给用户（可能是英文/内部细节），
 * 界面统一显示「模型文件不兼容或已损坏」。
 */
sealed interface ModelImportResult {

    /** 校验通过并已原子替换权重文件、切到当前模型。 */
    data class Imported(val modelVersion: String) : ModelImportResult

    /** 解析失败 / 版本不兼容 / 结构损坏——**不采纳**，保留原模型。 */
    data object Invalid : ModelImportResult

    /** 文件校验通过但写盘失败——不采纳内存模型，保留原模型。 */
    data object PersistFailed : ModelImportResult
}

/**
 * 导入模型文件的**纯校验**：只依赖 `:ml:classifier` 的 [ModelCodec]（无 Android）。
 *
 * 用 `ModelCodec.decode` 做后校验——它严格核对 `formatVersion` / `featureVersion` / `buckets`
 * 与维度对齐，任何外来/不兼容的 JSON 都抛 [ModelFormatException]，此处收敛成 null。
 */
object ModelImport {

    /** 解析并校验；不兼容/损坏返回 null（调用方据此拒绝导入并提示）。 */
    fun parse(text: String): LinearModel? =
        try {
            ModelCodec.decode(text)
        } catch (_: ModelFormatException) {
            null
        }
}
