package com.han1997.vipledger.feature.ai.model

import com.han1997.vipledger.core.domain.model.MlTrainingSample

/**
 * 「训练一批样本，并把它们标记为**实际产出**的模型版本」这一原子动作的接缝（PRD Requirement 3）。
 *
 * 抽象成接口、并把「训练」与「标记」两个副作用以函数注入，是为了让
 * 「开关关闭不发生训练」「达阈值才训练」「markTrained 用的是产出的版本」这些验收点
 * 能在纯 JVM 下用假实现计数与断言（`.trellis/spec/android/testing.md`），不必拉起 Android / 文件系统。
 */
fun interface SampleTrainer {

    /**
     * 训练 [samples] 并原子替换权重；成功后把样本标记为产出版本。
     *
     * 返回**产出的模型版本**；样本为空、训练失败（含权重未能落盘）或标记失败时返回 null——
     * 此时样本**保持未训练**，下一轮可重试。
     */
    suspend fun trainAndMark(samples: List<MlTrainingSample>): String?
}

/**
 * [SampleTrainer] 的默认实现：先训练拿版本，再用**同一个版本**标记样本。
 * 纯逻辑，无 Android 依赖。
 */
class DefaultSampleTrainer(
    private val train: suspend (List<MlTrainingSample>) -> String?,
    private val markTrained: suspend (ids: List<String>, version: String) -> Boolean,
) : SampleTrainer {

    override suspend fun trainAndMark(samples: List<MlTrainingSample>): String? {
        if (samples.isEmpty()) return null
        // 训练失败 → 不标记，样本保持未训练（下次修正后仍会重试）。
        val version = train(samples) ?: return null
        // 标记失败同样返回 null：绝不在没标记的情况下谎报成功（否则会重复训练同批样本）。
        return if (markTrained(samples.map { it.id }, version)) version else null
    }
}
