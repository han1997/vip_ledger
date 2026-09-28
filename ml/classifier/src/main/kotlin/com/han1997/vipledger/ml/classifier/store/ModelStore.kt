package com.han1997.vipledger.ml.classifier.store

import com.han1997.vipledger.ml.classifier.model.LinearModel

/**
 * 端侧模型的加载结果。三态而非「模型或 null」，是为了不把「文件本来就没有」和「文件损坏/版本不兼容」
 * 混成一个静默的 null（error-handling：不吞异常）。训练/推理路径都能安全降级到「不出建议」。
 */
sealed interface ModelLoadResult {

    data class Loaded(val model: LinearModel) : ModelLoadResult

    /** 还没训练过，或文件不存在。 */
    data object Missing : ModelLoadResult

    /** 文件在但读不出来（损坏、格式/特征版本不兼容等）；[reason] 供界面/日志展示，不含账目文本。 */
    data class Invalid(val reason: String) : ModelLoadResult

    val modelOrNull: LinearModel?
        get() = (this as? Loaded)?.model
}

/**
 * 权重文件的读写接缝（PR3 决定文件落在哪儿；`:ml:classifier` 只管「原子替换」这条不变式）。
 */
interface ModelStore {

    fun load(): ModelLoadResult

    /**
     * 原子替换权重文件：要么写成功、要么保持旧文件原样，绝不出现「半写文件被推理读到」。
     * 失败抛异常（[java.io.IOException] / [com.han1997.vipledger.ml.classifier.model.ModelFormatException]），
     * 由调用方决定是否降级。
     */
    fun save(model: LinearModel)
}
